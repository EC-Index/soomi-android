package com.soomi.baby.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.soomi.baby.domain.model.InterventionLevel
import com.soomi.baby.domain.model.SoundProfile
import com.soomi.baby.domain.model.SoundType
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * AudioOutputEngine v2.6
 *
 * Generates soothing sounds for baby calming interventions.
 * Supports multiple sound profiles with "Living Noise" for natural variation.
 *
 * v2.6 Features:
 * - SoundProfile support (OCEAN_BREATH, CLASSIC_PINK, DEEP_BROWN, etc.)
 * - Living Noise with LFO amplitude modulation
 * - Spectral drift via 1-pole lowpass filter
 * - Zero allocations in audio callback (pre-allocated buffers)
 * - Safe profile switching during playback
 *
 * Sound types (legacy):
 * - Brown noise: Deep, rumbling noise - very soothing for babies
 * - Pink noise: Balanced noise
 * - Shush pulse: Rhythmic modulation mimicking parent's "shhhh" sound
 *
 * Safety features:
 * - Hard volume cap to prevent dangerously loud output
 * - Smooth transitions to avoid startling baby
 */
class AudioOutputEngine(
    private val sampleRate: Int = 44100,
    private val maxVolumeCap: Float = 0.85f
) {
    companion object {
        private const val TAG = "AudioOutputEngine"
        
        // LFO defaults for Ocean Breath
        private const val DEFAULT_LFO_RATE_HZ = 0.08f      // ~12.5s cycle
        private const val DEFAULT_LFO_DEPTH = 0.08f        // +/- 8% amplitude
        
        // Spectral drift defaults
        private const val DRIFT_RATE_HZ = 0.015f           // Very slow drift
        private const val DRIFT_CUTOFF_MIN = 900f          // Hz
        private const val DRIFT_CUTOFF_MAX = 1600f         // Hz
    }

    // Audio configuration
    private val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSizeSamples = sampleRate / 10  // 100ms buffers
    private val bufferSizeBytes = bufferSizeSamples * 2

    // State
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val isPlaying = AtomicBoolean(false)
    
    // Current settings
    private var currentSoundType = SoundType.BROWN_NOISE
    @Volatile private var currentSoundProfile = SoundProfile.OCEAN_BREATH
    @Volatile private var pendingProfileChange: SoundProfile? = null
    private var currentVolume = 0f
    private var targetVolume = 0f
    
    // === PRE-ALLOCATED STATE (no allocations in audio callback) ===
    
    // Brown noise state
    private var brownNoiseState = 0.0
    
    // Pink noise state (Voss-McCartney algorithm)
    private val pinkNoiseOctaves = 8
    private val pinkNoiseValues = DoubleArray(pinkNoiseOctaves)
    private var pinkNoiseCounter = 0
    
    // Shush pulse state
    private var shushPhase = 0.0
    private val shushFrequency = 0.8  // ~0.8 Hz pulse rate
    
    // === LIVING NOISE STATE (v2.6) ===
    
    // LFO state for amplitude modulation
    private var lfoPhase = 0.0
    private var lfoRateHz = DEFAULT_LFO_RATE_HZ
    private var lfoDepth = DEFAULT_LFO_DEPTH
    
    // Spectral drift state (1-pole lowpass filter)
    private var driftPhase = 0.0
    private var filterState = 0.0
    private var filterCoeff = 0.0  // Will be computed from cutoff
    private var currentCutoff = (DRIFT_CUTOFF_MIN + DRIFT_CUTOFF_MAX) / 2f
    
    // Pre-computed constants
    private val twoPiOverSampleRate = 2.0 * PI / sampleRate
    
    /**
     * Initialize filter coefficient from cutoff frequency
     * 1-pole lowpass: y[n] = (1-a)*x[n] + a*y[n-1]
     * where a = exp(-2*pi*fc/fs)
     */
    private fun updateFilterCoeff(cutoffHz: Float) {
        val fc = cutoffHz.coerceIn(100f, 10000f)
        filterCoeff = kotlin.math.exp(-2.0 * PI * fc / sampleRate)
        currentCutoff = fc
    }

    /**
     * Start audio playback with specified sound type (legacy)
     */
    fun start(soundType: SoundType = SoundType.BROWN_NOISE) {
        if (isPlaying.get()) {
            Log.d(TAG, "Already playing, changing sound type to $soundType")
            currentSoundType = soundType
            return
        }

        Log.d(TAG, "Starting audio output: $soundType, profile: $currentSoundProfile")
        currentSoundType = soundType
        
        // Initialize filter
        updateFilterCoeff((DRIFT_CUTOFF_MIN + DRIFT_CUTOFF_MAX) / 2f)
        
        try {
            initAudioTrack()
            isPlaying.set(true)
            startPlaybackLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio", e)
            stop()
        }
    }
    
    /**
     * Start audio playback with specified sound profile (v2.6)
     */
    fun startWithProfile(profile: SoundProfile) {
        currentSoundProfile = profile
        configureLfoForProfile(profile)
        start(SoundType.BROWN_NOISE)  // Base sound type
    }

    /**
     * Stop audio playback with fade out
     */
    fun stop() {
        Log.d(TAG, "Stopping audio output")
        isPlaying.set(false)
        
        playbackJob?.cancel()
        playbackJob = null
        
        audioTrack?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping AudioTrack", e)
            }
        }
        audioTrack = null
        
        currentVolume = 0f
        targetVolume = 0f
        resetNoiseState()
    }

    /**
     * Set target volume level (0.0 to 1.0)
     * Volume transitions smoothly to avoid startling
     */
    fun setVolume(volume: Float) {
        targetVolume = (volume * maxVolumeCap).coerceIn(0f, maxVolumeCap)
        Log.d(TAG, "Target volume set to $targetVolume")
    }

    /**
     * Set volume from InterventionLevel
     */
    fun setVolume(level: InterventionLevel) {
        setVolume(level.volumeMultiplier)
    }

    /**
     * Set volume based on intervention level
     */
    fun setLevel(level: InterventionLevel) {
        setVolume(level.volumeMultiplier)
    }

    /**
     * Set raw volume value (for InterventionEngine compatibility)
     */
    fun setVolumeRaw(volume: Float) {
        setVolume(volume)
    }

    /**
     * Initialize the audio engine (for InterventionEngine compatibility)
     */
    fun initialize(): Boolean {
        Log.d(TAG, "AudioOutputEngine initialized")
        return true
    }

    /**
     * Force stop playback immediately
     */
    fun forceStop() {
        stop()
    }

    /**
     * Change sound type while playing (legacy)
     */
    fun setSoundType(soundType: SoundType) {
        Log.d(TAG, "Changing sound type to $soundType")
        currentSoundType = soundType
    }
    
    /**
     * Change sound profile while playing (v2.6)
     * Safe: applies at next buffer boundary
     */
    fun setSoundProfile(profile: SoundProfile) {
        Log.d(TAG, "Changing sound profile to $profile")
        pendingProfileChange = profile
    }
    
    /**
     * Get current sound profile
     */
    fun getCurrentProfile(): SoundProfile = currentSoundProfile

    /**
     * Check if currently playing
     */
    fun isPlaying(): Boolean = isPlaying.get()
    
    /**
     * Configure LFO parameters for a specific profile
     */
    private fun configureLfoForProfile(profile: SoundProfile) {
        when (profile) {
            SoundProfile.OCEAN_BREATH -> {
                lfoRateHz = 0.08f   // ~12.5s cycle
                lfoDepth = 0.08f   // +/- 8%
            }
            SoundProfile.CLASSIC_PINK -> {
                lfoRateHz = 0f      // No LFO
                lfoDepth = 0f
            }
            SoundProfile.DEEP_BROWN -> {
                lfoRateHz = 0.03f   // Very slow, subtle
                lfoDepth = 0.03f   // +/- 3% - even more subtle
            }
            // Pro profiles (stubs for now)
            SoundProfile.HEARTBEAT_OCEAN -> {
                lfoRateHz = 1.2f    // ~heartbeat rate
                lfoDepth = 0.15f
            }
            SoundProfile.RAIN_DRIFT -> {
                lfoRateHz = 0.05f
                lfoDepth = 0.05f
            }
            SoundProfile.FAN_STABLE -> {
                lfoRateHz = 0f
                lfoDepth = 0f
            }
            SoundProfile.SHUSH_SOFT -> {
                lfoRateHz = 0.8f
                lfoDepth = 0.3f
            }
            SoundProfile.TRAVEL_CALM -> {
                lfoRateHz = 0.1f
                lfoDepth = 0.06f
            }
        }
        Log.d(TAG, "LFO configured for $profile: rate=$lfoRateHz Hz, depth=$lfoDepth")
    }

    private fun initAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            channelConfig,
            audioFormat
        )
        
        val bufferSize = maxOf(bufferSizeBytes, minBufferSize)
        
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        val audioFormatObj = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(audioFormat)
            .setChannelMask(channelConfig)
            .build()
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormatObj)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        
        audioTrack?.play()
        Log.d(TAG, "AudioTrack initialized, buffer size: $bufferSize")
    }

    private fun startPlaybackLoop() {
        playbackJob = CoroutineScope(Dispatchers.Default).launch {
            val buffer = ShortArray(bufferSizeSamples)
            
            while (isActive && isPlaying.get()) {
                // Check for pending profile change (safe switch at buffer boundary)
                pendingProfileChange?.let { newProfile ->
                    currentSoundProfile = newProfile
                    configureLfoForProfile(newProfile)
                    pendingProfileChange = null
                    Log.d(TAG, "Profile switched to $newProfile at buffer boundary")
                }
                
                // Smooth volume transition
                updateVolume()
                
                // Generate audio based on current profile
                generateAudioForProfile(buffer, currentSoundProfile)
                
                // Apply volume
                applyVolume(buffer)
                
                // Write to track
                audioTrack?.write(buffer, 0, buffer.size)
            }
        }
    }
    
    /**
     * Generate audio samples based on the current sound profile
     * CRITICAL: No allocations in this method!
     */
    private fun generateAudioForProfile(buffer: ShortArray, profile: SoundProfile) {
        when (profile) {
            SoundProfile.OCEAN_BREATH -> generateOceanBreath(buffer)
            SoundProfile.CLASSIC_PINK -> generateClassicPink(buffer)
            SoundProfile.DEEP_BROWN -> generateDeepBrown(buffer)
            // Pro profiles (stubs - use similar base sounds)
            SoundProfile.HEARTBEAT_OCEAN -> generateOceanBreath(buffer)  // Stub
            SoundProfile.RAIN_DRIFT -> generateClassicPink(buffer)       // Stub
            SoundProfile.FAN_STABLE -> generateClassicPink(buffer)       // Stub  
            SoundProfile.SHUSH_SOFT -> generateShushPulse(buffer)        // Stub
            SoundProfile.TRAVEL_CALM -> generateDeepBrown(buffer)        // Stub
        }
    }
    
    /**
     * OCEAN_BREATH: Pink noise with LFO amplitude modulation + spectral drift
     * This is the "Living Noise" default profile.
     * 
     * CONSTRAINTS:
     * - No allocations in this method
     * - Subtle modulation (no audible pumping)
     * - Filter coefficients pre-computed
     */
    private fun generateOceanBreath(buffer: ShortArray) {
        // Update spectral drift (slow sine wave moves filter cutoff)
        val driftPhaseIncrement = DRIFT_RATE_HZ * twoPiOverSampleRate
        
        for (i in buffer.indices) {
            // === Step 1: Generate base pink noise ===
            pinkNoiseCounter++
            var pinkSample = 0.0
            for (octave in 0 until pinkNoiseOctaves) {
                if (pinkNoiseCounter and (1 shl octave) != 0) {
                    pinkNoiseValues[octave] = Random.nextDouble() * 2 - 1
                }
                pinkSample += pinkNoiseValues[octave]
            }
            pinkSample /= pinkNoiseOctaves
            
            // === Step 2: Apply spectral drift (1-pole lowpass filter) ===
            // Update cutoff based on drift phase (slow oscillation)
            val driftOffset = sin(driftPhase)
            val newCutoff = DRIFT_CUTOFF_MIN + 
                (DRIFT_CUTOFF_MAX - DRIFT_CUTOFF_MIN) * ((driftOffset + 1.0) / 2.0)
            
            // Update filter coefficient (only if cutoff changed significantly)
            if (kotlin.math.abs(newCutoff - currentCutoff) > 10) {
                updateFilterCoeff(newCutoff.toFloat())
            }
            
            // Apply 1-pole lowpass: y[n] = (1-a)*x[n] + a*y[n-1]
            filterState = (1.0 - filterCoeff) * pinkSample + filterCoeff * filterState
            var filteredSample = filterState
            
            // === Step 3: Apply LFO amplitude modulation ===
            val lfoValue = sin(lfoPhase)
            val amplitudeModulation = 1.0 + (lfoValue * lfoDepth)
            // Clamp to prevent pumping
            val clampedModulation = amplitudeModulation.coerceIn(0.85, 1.15)
            
            filteredSample *= clampedModulation
            
            // === Step 4: Convert to output ===
            buffer[i] = (filteredSample * Short.MAX_VALUE * 0.7).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            
            // Update phases
            lfoPhase += lfoRateHz * twoPiOverSampleRate
            if (lfoPhase > 2 * PI) lfoPhase -= 2 * PI
            
            driftPhase += driftPhaseIncrement
            if (driftPhase > 2 * PI) driftPhase -= 2 * PI
        }
    }
    
    /**
     * CLASSIC_PINK: Plain pink noise (no LFO, no spectral drift)
     */
    private fun generateClassicPink(buffer: ShortArray) {
        for (i in buffer.indices) {
            pinkNoiseCounter++
            var sample = 0.0
            
            for (octave in 0 until pinkNoiseOctaves) {
                if (pinkNoiseCounter and (1 shl octave) != 0) {
                    pinkNoiseValues[octave] = Random.nextDouble() * 2 - 1
                }
                sample += pinkNoiseValues[octave]
            }
            
            sample /= pinkNoiseOctaves
            buffer[i] = (sample * Short.MAX_VALUE * 0.7).toInt().toShort()
        }
    }
    
    /**
     * DEEP_BROWN: Brown-ish noise with optional very subtle drift
     */
    private fun generateDeepBrown(buffer: ShortArray) {
        val driftPhaseIncrement = 0.01 * twoPiOverSampleRate  // Very slow
        
        for (i in buffer.indices) {
            // Brown noise: integrate white noise
            brownNoiseState += (Random.nextDouble() * 2 - 1) * 0.015
            brownNoiseState *= 0.998  // Slight decay to prevent drift
            brownNoiseState = brownNoiseState.coerceIn(-1.0, 1.0)
            
            var sample = brownNoiseState
            
            // Very subtle LFO if configured
            if (lfoDepth > 0) {
                val lfoValue = sin(lfoPhase)
                val modulation = 1.0 + (lfoValue * lfoDepth)
                sample *= modulation.coerceIn(0.9, 1.1)
                
                lfoPhase += lfoRateHz * twoPiOverSampleRate
                if (lfoPhase > 2 * PI) lfoPhase -= 2 * PI
            }
            
            buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
            
            driftPhase += driftPhaseIncrement
            if (driftPhase > 2 * PI) driftPhase -= 2 * PI
        }
    }

    private fun updateVolume() {
        // Smooth transition (about 100ms to reach target)
        val transitionSpeed = 0.1f
        currentVolume += (targetVolume - currentVolume) * transitionSpeed
    }

    private fun generateBrownNoise(buffer: ShortArray) {
        for (i in buffer.indices) {
            // Brown noise: integrate white noise
            brownNoiseState += (Random.nextDouble() * 2 - 1) * 0.02
            brownNoiseState *= 0.998  // Slight decay to prevent drift
            brownNoiseState = brownNoiseState.coerceIn(-1.0, 1.0)
            buffer[i] = (brownNoiseState * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun generatePinkNoise(buffer: ShortArray) {
        for (i in buffer.indices) {
            // Voss-McCartney algorithm for pink noise
            var sample = 0.0
            pinkNoiseCounter++
            
            for (octave in 0 until pinkNoiseOctaves) {
                if (pinkNoiseCounter and (1 shl octave) != 0) {
                    pinkNoiseValues[octave] = Random.nextDouble() * 2 - 1
                }
                sample += pinkNoiseValues[octave]
            }
            
            sample /= pinkNoiseOctaves
            buffer[i] = (sample * Short.MAX_VALUE * 0.7).toInt().toShort()
        }
    }

    private fun generateShushPulse(buffer: ShortArray) {
        val phaseIncrement = shushFrequency * 2 * Math.PI / sampleRate
        
        for (i in buffer.indices) {
            // Generate base noise
            val noise = Random.nextDouble() * 2 - 1
            
            // Modulate with smooth pulse
            val envelope = (sin(shushPhase) + 1) / 2  // 0 to 1
            val modulated = noise * (0.3 + envelope * 0.7)  // Keep some baseline
            
            buffer[i] = (modulated * Short.MAX_VALUE * 0.6).toInt().toShort()
            
            shushPhase += phaseIncrement
            if (shushPhase > 2 * Math.PI) shushPhase -= 2 * Math.PI
        }
    }

    private fun applyVolume(buffer: ShortArray) {
        for (i in buffer.indices) {
            buffer[i] = (buffer[i] * currentVolume).toInt().toShort()
        }
    }
    
    private fun resetNoiseState() {
        brownNoiseState = 0.0
        pinkNoiseValues.fill(0.0)
        pinkNoiseCounter = 0
        shushPhase = 0.0
        lfoPhase = 0.0
        driftPhase = 0.0
        filterState = 0.0
    }

    /**
     * Release all resources
     */
    fun release() {
        stop()
        Log.d(TAG, "AudioOutputEngine released")
    }
}
