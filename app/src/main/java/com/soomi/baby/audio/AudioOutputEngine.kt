package com.soomi.baby.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.soomi.baby.domain.model.InterventionLevel
import com.soomi.baby.domain.model.SoundType
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sin
import kotlin.random.Random

/**
 * AudioOutputEngine
 *
 * Generates soothing sounds for baby calming interventions.
 * Supports multiple sound types with smooth volume transitions.
 *
 * Sound types:
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
    private var currentVolume = 0f
    private var targetVolume = 0f
    
    // Brown noise state
    private var brownNoiseState = 0.0
    
    // Pink noise state (Voss-McCartney algorithm)
    private val pinkNoiseOctaves = 8
    private val pinkNoiseValues = DoubleArray(pinkNoiseOctaves)
    private var pinkNoiseCounter = 0
    
    // Shush pulse state
    private var shushPhase = 0.0
    private val shushFrequency = 0.8  // ~0.8 Hz pulse rate

    /**
     * Start audio playback with specified sound type
     */
    fun start(soundType: SoundType = SoundType.BROWN_NOISE) {
        if (isPlaying.get()) {
            Log.d(TAG, "Already playing, changing sound type to $soundType")
            currentSoundType = soundType
            return
        }

        Log.d(TAG, "Starting audio output: $soundType")
        currentSoundType = soundType
        
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
    fun initialize() {
        Log.d(TAG, "AudioOutputEngine initialized")
    }

    /**
     * Force stop playback immediately
     */
    fun forceStop() {
        stop()
    }

    /**
     * Change sound type while playing
     */
    fun setSoundType(soundType: SoundType) {
        Log.d(TAG, "Changing sound type to $soundType")
        currentSoundType = soundType
    }

    /**
     * Check if currently playing
     */
    fun isPlaying(): Boolean = isPlaying.get()

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
                // Smooth volume transition
                updateVolume()
                
                // Generate audio
                when (currentSoundType) {
                    SoundType.BROWN_NOISE -> generateBrownNoise(buffer)
                    SoundType.PINK_NOISE -> generatePinkNoise(buffer)
                    SoundType.SHUSH_PULSE -> generateShushPulse(buffer)
                }
                
                // Apply volume
                applyVolume(buffer)
                
                // Write to track
                audioTrack?.write(buffer, 0, buffer.size)
            }
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

    /**
     * Release all resources
     */
    fun release() {
        stop()
        Log.d(TAG, "AudioOutputEngine released")
    }
}
