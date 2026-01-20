package com.soomi.baby.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * AudioInputEngine
 * 
 * Captures live audio from the device microphone for real-time processing.
 * 
 * PRIVACY CRITICAL:
 * - Audio is processed in memory only
 * - No recording to disk
 * - No network transmission
 * - Buffers are immediately discarded after feature extraction
 * 
 * Battery optimization:
 * - Uses 16kHz sample rate (sufficient for cry detection, lower than CD quality)
 * - Processes in 500ms windows with 250ms hop
 * - Runs on background thread with appropriate priority
 */
class AudioInputEngine(
    private val context: Context,
    private val sampleRate: Int = 16000,
    private val windowSizeMs: Int = 500
) {
    // Audio configuration
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSizeSamples = (sampleRate * windowSizeMs) / 1000
    private val bufferSizeBytes = bufferSizeSamples * 2  // 16-bit = 2 bytes per sample
    
    // AudioRecord instance
    private var audioRecord: AudioRecord? = null
    
    // Processing coroutine
    private var captureJob: Job? = null
    private val captureScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Output flow for audio samples (normalized to -1.0 to 1.0)
    private val _audioSamples = MutableSharedFlow<FloatArray>(replay = 0, extraBufferCapacity = 4)
    val audioSamples: SharedFlow<FloatArray> = _audioSamples.asSharedFlow()
    
    // State
    private var isCapturing = false
    
    /**
     * Check if microphone permission is granted
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Start capturing audio from microphone
     * 
     * @throws SecurityException if permission not granted
     * @throws IllegalStateException if AudioRecord initialization fails
     */
    @Synchronized
    fun startCapture() {
        if (isCapturing) return
        
        if (!hasPermission()) {
            throw SecurityException("RECORD_AUDIO permission not granted")
        }
        
        // Calculate minimum buffer size
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            audioFormat
        )
        
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("Unable to determine minimum buffer size")
        }
        
        // Use larger buffer to prevent dropouts (at least 2x minimum)
        val actualBufferSize = maxOf(bufferSizeBytes, minBufferSize * 2)
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                actualBufferSize
            ).also { record ->
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    throw IllegalStateException("AudioRecord failed to initialize")
                }
            }
        } catch (e: SecurityException) {
            throw SecurityException("Microphone access denied", e)
        }
        
        audioRecord?.startRecording()
        isCapturing = true
        
        // Start capture loop
        captureJob = captureScope.launch {
            captureLoop()
        }
    }
    
    /**
     * Stop capturing audio
     */
    @Synchronized
    fun stopCapture() {
        if (!isCapturing) return
        
        isCapturing = false
        captureJob?.cancel()
        captureJob = null
        
        audioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (e: IllegalStateException) {
                // Already stopped, ignore
            }
            record.release()
        }
        audioRecord = null
    }
    
    /**
     * Release all resources
     */
    fun release() {
        stopCapture()
        captureScope.cancel()
    }
    
    /**
     * Check if currently capturing
     */
    fun isCapturing(): Boolean = isCapturing
    
    /**
     * Main capture loop - runs on background thread
     * 
     * PRIVACY: Audio buffers are processed and immediately discarded.
     * No audio data is ever stored or transmitted.
     */
    private suspend fun captureLoop() {
        val buffer = ShortArray(bufferSizeSamples)
        val floatBuffer = FloatArray(bufferSizeSamples)
        
        while (isCapturing && coroutineContext.isActive) {
            val record = audioRecord ?: break
            
            // Read audio data
            val samplesRead = record.read(buffer, 0, bufferSizeSamples)
            
            if (samplesRead > 0) {
                // Convert to normalized float (-1.0 to 1.0)
                // This is the ONLY place raw audio exists in memory
                for (i in 0 until samplesRead) {
                    floatBuffer[i] = buffer[i] / 32768f
                }
                
                // Emit for processing (copy to prevent buffer reuse issues)
                val outputBuffer = floatBuffer.copyOf(samplesRead)
                _audioSamples.emit(outputBuffer)
                
                // PRIVACY: Original buffer will be overwritten in next iteration
                // No audio data persists beyond this point
            } else if (samplesRead < 0) {
                // Error reading - might be permission revoked
                when (samplesRead) {
                    AudioRecord.ERROR_INVALID_OPERATION -> {
                        // AudioRecord not properly initialized
                        break
                    }
                    AudioRecord.ERROR_BAD_VALUE -> {
                        // Invalid parameters
                        break
                    }
                    AudioRecord.ERROR_DEAD_OBJECT -> {
                        // AudioRecord instance is no longer valid
                        break
                    }
                }
            }
            
            // Small yield to prevent CPU hogging
            yield()
        }
    }
    
    companion object {
        const val DEFAULT_SAMPLE_RATE = 16000
        const val DEFAULT_WINDOW_MS = 500
    }
}
