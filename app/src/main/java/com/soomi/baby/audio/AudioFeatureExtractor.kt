package com.soomi.baby.audio

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * AudioFeatureExtractor
 * 
 * Extracts audio features from raw PCM samples to compute unrest likelihood.
 * Uses computationally efficient algorithms suitable for real-time processing on mobile.
 * 
 * PRIVACY: Operates only on in-memory buffers. Never records or persists raw audio.
 * 
 * Features extracted:
 * 1. RMS Energy - Overall loudness
 * 2. Band-limited energy (300-3000 Hz) - Cry-typical frequency range
 * 3. Zero-crossing rate - Pitch/noise indicator
 * 4. Amplitude modulation - Detects "bursty" cry patterns
 */
class AudioFeatureExtractor(
    private val sampleRate: Int = 16000,
    private val windowSizeMs: Int = 500,  // 0.5 second windows
    private val hopSizeMs: Int = 250      // 50% overlap
) {
    // Pre-computed constants
    private val windowSizeSamples = (sampleRate * windowSizeMs) / 1000
    private val hopSizeSamples = (sampleRate * hopSizeMs) / 1000
    
    // Simple IIR bandpass filter coefficients for 300-3000 Hz
    // Using Butterworth-style approximation for efficiency
    private val bandpassLowFreq = 300.0
    private val bandpassHighFreq = 3000.0
    
    // Filter state for IIR
    private var filterState = FloatArray(4)
    
    // EMA smoothing for output
    private val emaAlpha = 0.3f
    private var smoothedScore = 0f
    private var prevScore = 0f
    
    // Ring buffer for amplitude modulation detection
    private val envelopeBuffer = FloatArray(20)  // ~5 seconds of envelope samples
    private var envelopeIndex = 0
    
    /**
     * Raw audio features before normalization
     */
    data class RawFeatures(
        val rmsEnergy: Float,
        val bandEnergy: Float,
        val zeroCrossingRate: Float,
        val amplitudeModulation: Float,
        val peakAmplitude: Float
    )
    
    /**
     * Extract features from a buffer of PCM samples
     * 
     * @param samples Raw 16-bit PCM samples normalized to -1.0 to 1.0
     * @return Raw features for this window
     */
    fun extractFeatures(samples: FloatArray): RawFeatures {
        require(samples.isNotEmpty()) { "Samples array cannot be empty" }
        
        // 1. RMS Energy
        val rmsEnergy = calculateRMS(samples)
        
        // 2. Band-limited energy (simple bandpass + RMS)
        val bandFiltered = applyBandpassFilter(samples)
        val bandEnergy = calculateRMS(bandFiltered)
        
        // 3. Zero-crossing rate
        val zcr = calculateZeroCrossingRate(samples)
        
        // 4. Amplitude modulation (burstiness detection)
        val ampMod = calculateAmplitudeModulation(rmsEnergy)
        
        // 5. Peak amplitude
        val peak = samples.maxOfOrNull { abs(it) } ?: 0f
        
        return RawFeatures(
            rmsEnergy = rmsEnergy,
            bandEnergy = bandEnergy,
            zeroCrossingRate = zcr,
            amplitudeModulation = ampMod,
            peakAmplitude = peak
        )
    }
    
    /**
     * Compute normalized unrest score from features
     * 
     * @param features Raw features from extractFeatures()
     * @return Normalized score 0-100
     */
    fun computeUnrestScore(features: RawFeatures): Float {
        // Normalize each feature to 0-1 range based on empirical thresholds
        // These thresholds are tuned for typical baby crying patterns
        
        // RMS: quiet room ~0.01, crying ~0.1-0.3
        val rmsNorm = (features.rmsEnergy / 0.15f).coerceIn(0f, 1f)
        
        // Band energy: crying has high energy in 300-3000Hz
        val bandNorm = (features.bandEnergy / 0.12f).coerceIn(0f, 1f)
        
        // ZCR: crying typically 50-200 crossings per 1000 samples
        val zcrNorm = ((features.zeroCrossingRate - 0.02f) / 0.15f).coerceIn(0f, 1f)
        
        // Amplitude modulation: crying is bursty
        val modNorm = (features.amplitudeModulation / 0.5f).coerceIn(0f, 1f)
        
        // Weighted combination - band energy and modulation are most cry-specific
        val rawScore = (
            rmsNorm * 0.20f +
            bandNorm * 0.35f +
            zcrNorm * 0.15f +
            modNorm * 0.30f
        ) * 100f
        
        // Apply EMA smoothing to reduce jitter
        prevScore = smoothedScore
        smoothedScore = emaAlpha * rawScore + (1 - emaAlpha) * smoothedScore
        
        return smoothedScore.coerceIn(0f, 100f)
    }
    
    /**
     * Get score trend based on recent history
     */
    fun getScoreTrend(): com.soomi.baby.domain.model.UnrestScore.Trend {
        val delta = smoothedScore - prevScore
        return when {
            delta > 2f -> com.soomi.baby.domain.model.UnrestScore.Trend.RISING
            delta < -2f -> com.soomi.baby.domain.model.UnrestScore.Trend.FALLING
            else -> com.soomi.baby.domain.model.UnrestScore.Trend.STABLE
        }
    }
    
    /**
     * Reset filter and smoothing state (call when starting new session)
     */
    fun reset() {
        filterState = FloatArray(4)
        smoothedScore = 0f
        prevScore = 0f
        envelopeBuffer.fill(0f)
        envelopeIndex = 0
    }
    
    // --- Private helper methods ---
    
    private fun calculateRMS(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        val sumSquares = samples.fold(0.0) { acc, s -> acc + s * s }
        return sqrt(sumSquares / samples.size).toFloat()
    }
    
    private fun calculateZeroCrossingRate(samples: FloatArray): Float {
        if (samples.size < 2) return 0f
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i] >= 0 && samples[i-1] < 0) || 
                (samples[i] < 0 && samples[i-1] >= 0)) {
                crossings++
            }
        }
        return crossings.toFloat() / samples.size
    }
    
    /**
     * Simple 2nd-order IIR bandpass filter
     * Approximates Butterworth response for computational efficiency
     */
    private fun applyBandpassFilter(samples: FloatArray): FloatArray {
        val output = FloatArray(samples.size)
        
        // Pre-computed coefficients for 300-3000Hz bandpass at 16kHz sample rate
        // Using bilinear transform approximation
        val omega1 = 2.0 * Math.PI * bandpassLowFreq / sampleRate
        val omega2 = 2.0 * Math.PI * bandpassHighFreq / sampleRate
        
        val bw = omega2 - omega1
        val center = (omega1 + omega2) / 2
        
        // Simplified coefficients
        val b0 = (bw / 2).toFloat()
        val b1 = 0f
        val b2 = -b0
        val a1 = (-2 * Math.cos(center)).toFloat()
        val a2 = (1 - bw / 2).toFloat()
        
        for (i in samples.indices) {
            output[i] = b0 * samples[i] + 
                        b1 * filterState[0] + 
                        b2 * filterState[1] -
                        a1 * filterState[2] - 
                        a2 * filterState[3]
            
            // Update state
            filterState[1] = filterState[0]
            filterState[0] = samples[i]
            filterState[3] = filterState[2]
            filterState[2] = output[i]
        }
        
        return output
    }
    
    /**
     * Detect amplitude modulation (burstiness) typical of crying
     * Tracks envelope variance over time
     */
    private fun calculateAmplitudeModulation(currentRMS: Float): Float {
        // Store current RMS in ring buffer
        envelopeBuffer[envelopeIndex] = currentRMS
        envelopeIndex = (envelopeIndex + 1) % envelopeBuffer.size
        
        // Calculate variance of envelope
        val mean = envelopeBuffer.average().toFloat()
        if (mean < 0.01f) return 0f  // Too quiet to measure modulation
        
        val variance = envelopeBuffer.map { (it - mean) * (it - mean) }.average().toFloat()
        
        // Normalize by mean (coefficient of variation)
        return sqrt(variance) / mean
    }
    
    companion object {
        // Default configuration
        const val DEFAULT_SAMPLE_RATE = 16000
        const val DEFAULT_WINDOW_MS = 500
        const val DEFAULT_HOP_MS = 250
        
        /**
         * Create extractor with default settings optimized for baby monitoring
         */
        fun createDefault() = AudioFeatureExtractor(
            sampleRate = DEFAULT_SAMPLE_RATE,
            windowSizeMs = DEFAULT_WINDOW_MS,
            hopSizeMs = DEFAULT_HOP_MS
        )
    }
}
