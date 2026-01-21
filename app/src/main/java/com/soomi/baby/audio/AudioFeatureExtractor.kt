package com.soomi.baby.audio

import kotlin.math.*

/**
 * AudioFeatureExtractor - Baby-Cry Specific
 * 
 * Version: 2.4
 * 
 * CHANGELOG v2.4:
 * - Fix: Baby-Weinen wurde bei aktivem Playback nicht erkannt
 * - Neuer Ansatz: Wenn KLARER PITCH im Baby-Bereich → Score MUSS steigen
 * - Noise-Filter nur wenn KEIN Pitch vorhanden
 * - Bessere Balance zwischen Filter und Erkennung
 * 
 * LOGIK:
 * 1. Hat das Signal einen KLAREN PITCH (pitchStrength > 0.4)?
 *    - JA → Ist der Pitch im Baby-Bereich (250-650 Hz)?
 *      - JA → BABY ERKANNT → Score berechnen
 *      - NEIN → Erwachsener oder anderes → niedriger Score
 *    - NEIN → Kein Pitch = Rauschen/Umgebung → Score = 0
 */
class AudioFeatureExtractor(
    private val sampleRate: Int = 16000,
    private val windowSizeMs: Int = 500,
    private val hopSizeMs: Int = 250
) {
    // FFT Größe
    private val fftSize = 512
    
    // Baby-Cry Frequenzbänder
    private val babyCryLowBin = (250.0 * fftSize / sampleRate).toInt()
    private val babyCryHighBin = (600.0 * fftSize / sampleRate).toInt()
    private val harmonicsLowBin = (600.0 * fftSize / sampleRate).toInt()
    private val harmonicsHighBin = (2500.0 * fftSize / sampleRate).toInt()
    
    // Smoothing
    private val emaAlpha = 0.25f
    private var smoothedScore = 0f
    private var prevScore = 0f
    
    // Historie
    private val energyHistory = ArrayDeque<Float>(30)
    private val pitchHistory = ArrayDeque<Float>(20)
    private val scoreHistory = ArrayDeque<Float>(12)
    
    // Impuls-Erkennung
    private var lastEnergy = 0f
    private var impulseDecayCounter = 0
    
    // Playback State
    private var isPlaybackActive = false
    private var playbackLevel = 0f
    
    // FFT Arrays
    private val fftReal = DoubleArray(fftSize)
    private val fftImag = DoubleArray(fftSize)
    private val hannWindow = DoubleArray(fftSize) { i ->
        0.5 * (1 - cos(2 * PI * i / (fftSize - 1)))
    }
    private val magnitudeSpectrum = DoubleArray(fftSize / 2)
    
    data class RawFeatures(
        val rmsEnergy: Float,
        val babyCryBandEnergy: Float,
        val harmonicEnergy: Float,
        val spectralCentroid: Float,
        val pitchStrength: Float,
        val estimatedPitch: Float,
        val isImpulsive: Boolean,
        val spectralFlatness: Float,
        val peakAmplitude: Float
    )
    
    fun extractFeatures(samples: FloatArray): RawFeatures {
        require(samples.isNotEmpty()) { "Samples array cannot be empty" }
        
        val rmsEnergy = calculateRMS(samples)
        val peakAmplitude = samples.maxOfOrNull { abs(it) } ?: 0f
        
        // Stille
        if (rmsEnergy < 0.005f) {
            return createSilentFeatures(rmsEnergy, peakAmplitude)
        }
        
        // Impuls-Erkennung (Klatschen etc.)
        val energyRatio = if (lastEnergy > 0.001f) rmsEnergy / lastEnergy else 1f
        val isImpulsive = detectImpulse(energyRatio, rmsEnergy, samples)
        lastEnergy = rmsEnergy
        
        // FFT
        computeFFT(samples)
        
        // Band-Energien
        val babyCryBandEnergy = calculateBandEnergy(babyCryLowBin, babyCryHighBin)
        val harmonicEnergy = calculateBandEnergy(harmonicsLowBin, harmonicsHighBin)
        
        // Spektrale Features
        val spectralCentroid = calculateSpectralCentroid()
        val spectralFlatness = calculateSpectralFlatness()
        
        // Pitch - DAS WICHTIGSTE FEATURE!
        val (estimatedPitch, pitchStrength) = estimatePitch(samples)
        
        // History
        energyHistory.addLast(rmsEnergy)
        if (energyHistory.size > 30) energyHistory.removeFirst()
        pitchHistory.addLast(estimatedPitch)
        if (pitchHistory.size > 20) pitchHistory.removeFirst()
        
        return RawFeatures(
            rmsEnergy = rmsEnergy,
            babyCryBandEnergy = babyCryBandEnergy,
            harmonicEnergy = harmonicEnergy,
            spectralCentroid = spectralCentroid,
            pitchStrength = pitchStrength,
            estimatedPitch = estimatedPitch,
            isImpulsive = isImpulsive,
            spectralFlatness = spectralFlatness,
            peakAmplitude = peakAmplitude
        )
    }
    
    fun computeUnrestScore(features: RawFeatures): Float {
        
        // === FILTER 1: Impulsive Geräusche (Klatschen, Klopfen) ===
        if (features.isImpulsive) {
            impulseDecayCounter = 6
            return applySmoothing(smoothedScore * 0.5f)
        }
        if (impulseDecayCounter > 0) {
            impulseDecayCounter--
            return applySmoothing(smoothedScore * 0.85f)
        }
        
        // === FILTER 2: Stille ===
        if (features.rmsEnergy < 0.008f) {
            return applySmoothing(0f)
        }
        
        // === KERN-LOGIK: PITCH-BASIERTE ERKENNUNG ===
        // 
        // Die ENTSCHEIDENDE Frage: Gibt es einen KLAREN PITCH?
        // - Rauschen (Brown/Pink Noise): KEIN Pitch (pitchStrength < 0.3)
        // - Baby-Weinen: KLARER Pitch (pitchStrength > 0.4) im Bereich 250-650 Hz
        // - Erwachsenen-Sprache: Pitch im Bereich 85-250 Hz
        //
        
        val hasClearPitch = features.pitchStrength > 0.35f
        val hasStrongPitch = features.pitchStrength > 0.5f
        val pitchInBabyRange = features.estimatedPitch in 220f..700f
        val pitchInAdultRange = features.estimatedPitch in 80f..220f
        
        // === FALL 1: KEIN KLARER PITCH → Rauschen/Umgebung ===
        if (!hasClearPitch) {
            // Kein Pitch = definitiv kein Baby-Weinen
            // Das ist Rauschen, Umgebungsgeräusche, oder sehr leise
            return applySmoothing(smoothedScore * 0.3f)
        }
        
        // === FALL 2: PITCH IM ERWACHSENEN-BEREICH → Sprache ===
        if (pitchInAdultRange && hasClearPitch) {
            // Erwachsener spricht - kein Baby
            return applySmoothing(smoothedScore * 0.4f)
        }
        
        // === FALL 3: PITCH IM BABY-BEREICH → BABY ERKANNT! ===
        if (pitchInBabyRange && hasClearPitch) {
            // HIER IST EIN BABY!
            // Jetzt Score basierend auf Intensität berechnen
            
            var rawScore = 0f
            
            // Pitch-Stärke (je klarer der Pitch, desto sicherer)
            val pitchScore = features.pitchStrength.coerceIn(0f, 1f)
            rawScore += pitchScore * 35f
            
            // Energie (lauter = höherer Score)
            val energyScore = (features.rmsEnergy / 0.12f).coerceIn(0f, 1f)
            rawScore += energyScore * 25f
            
            // Baby-Cry Band Energie
            val bandScore = (features.babyCryBandEnergy / 0.03f).coerceIn(0f, 1f)
            rawScore += bandScore * 20f
            
            // Harmonische (Obertöne = Stimme)
            val harmonicScore = (features.harmonicEnergy / 0.02f).coerceIn(0f, 1f)
            rawScore += harmonicScore * 10f
            
            // Tonal Score (niedriger Flatness = mehr Stimme)
            val tonalScore = (1f - features.spectralFlatness).coerceIn(0f, 1f)
            rawScore += tonalScore * 10f
            
            // Bonus für sehr starken Pitch
            if (hasStrongPitch) {
                rawScore *= 1.2f
            }
            
            // Konsistenz-Filter
            rawScore = applyConsistencyFilter(rawScore)
            
            return applySmoothing(rawScore.coerceIn(0f, 100f))
        }
        
        // === FALL 4: PITCH VORHANDEN ABER NICHT IM BABY-BEREICH ===
        // Könnte ein älteres Kind, Haustier, oder anderes sein
        val otherScore = features.pitchStrength * 15f
        return applySmoothing(otherScore.coerceIn(0f, 30f))
    }
    
    fun getScoreTrend(): com.soomi.baby.domain.model.UnrestScore.Trend {
        if (scoreHistory.size < 4) return com.soomi.baby.domain.model.UnrestScore.Trend.STABLE
        
        val recent = scoreHistory.takeLast(4).average().toFloat()
        val older = scoreHistory.take(4).average().toFloat()
        
        val delta = recent - older
        return when {
            delta > 5f -> com.soomi.baby.domain.model.UnrestScore.Trend.RISING
            delta < -5f -> com.soomi.baby.domain.model.UnrestScore.Trend.FALLING
            else -> com.soomi.baby.domain.model.UnrestScore.Trend.STABLE
        }
    }
    
    fun reset() {
        smoothedScore = 0f
        prevScore = 0f
        energyHistory.clear()
        pitchHistory.clear()
        scoreHistory.clear()
        lastEnergy = 0f
        impulseDecayCounter = 0
        isPlaybackActive = false
        playbackLevel = 0f
        fftReal.fill(0.0)
        fftImag.fill(0.0)
    }
    
    /**
     * Vom Service aufrufen wenn Audio abgespielt wird
     * (Wird für Logging benutzt, filtert aber nicht mehr aggressiv)
     */
    fun setPlaybackActive(isPlaying: Boolean, level: Float = 0f) {
        isPlaybackActive = isPlaying
        playbackLevel = level
    }
    
    // === PRIVATE HILFSFUNKTIONEN ===
    
    private fun createSilentFeatures(rmsEnergy: Float, peakAmplitude: Float) = RawFeatures(
        rmsEnergy = rmsEnergy,
        babyCryBandEnergy = 0f,
        harmonicEnergy = 0f,
        spectralCentroid = 0f,
        pitchStrength = 0f,
        estimatedPitch = 0f,
        isImpulsive = false,
        spectralFlatness = 1f,
        peakAmplitude = peakAmplitude
    )
    
    private fun calculateRMS(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        val sumSquares = samples.fold(0.0) { acc, s -> acc + s * s }
        return sqrt(sumSquares / samples.size).toFloat()
    }
    
    private fun detectImpulse(energyRatio: Float, currentEnergy: Float, samples: FloatArray): Boolean {
        if (energyRatio > 10f && currentEnergy > 0.08f) {
            val peak = samples.maxOfOrNull { abs(it) } ?: 0f
            val rms = calculateRMS(samples)
            val crestFactor = if (rms > 0.001f) peak / rms else 1f
            return crestFactor > 4f
        }
        return false
    }
    
    private fun computeFFT(samples: FloatArray) {
        val numSamples = minOf(samples.size, fftSize)
        for (i in 0 until fftSize) {
            fftReal[i] = if (i < numSamples) samples[i].toDouble() * hannWindow[i] else 0.0
            fftImag[i] = 0.0
        }
        
        fft(fftReal, fftImag)
        
        for (i in 0 until fftSize / 2) {
            magnitudeSpectrum[i] = sqrt(fftReal[i] * fftReal[i] + fftImag[i] * fftImag[i])
        }
    }
    
    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        if (n <= 1) return
        
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                var temp = real[i]
                real[i] = real[j]
                real[j] = temp
                temp = imag[i]
                imag[i] = imag[j]
                imag[j] = temp
            }
            var k = n / 2
            while (k <= j) {
                j -= k
                k /= 2
            }
            j += k
        }
        
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angle = -2.0 * PI / len
            val wReal = cos(angle)
            val wImag = sin(angle)
            
            var i = 0
            while (i < n) {
                var curWReal = 1.0
                var curWImag = 0.0
                
                for (k in 0 until halfLen) {
                    val tReal = curWReal * real[i + k + halfLen] - curWImag * imag[i + k + halfLen]
                    val tImag = curWReal * imag[i + k + halfLen] + curWImag * real[i + k + halfLen]
                    
                    real[i + k + halfLen] = real[i + k] - tReal
                    imag[i + k + halfLen] = imag[i + k] - tImag
                    real[i + k] = real[i + k] + tReal
                    imag[i + k] = imag[i + k] + tImag
                    
                    val newWReal = curWReal * wReal - curWImag * wImag
                    curWImag = curWReal * wImag + curWImag * wReal
                    curWReal = newWReal
                }
                i += len
            }
            len *= 2
        }
    }
    
    private fun calculateBandEnergy(lowBin: Int, highBin: Int): Float {
        var sum = 0.0
        val actualLow = lowBin.coerceIn(0, magnitudeSpectrum.size - 1)
        val actualHigh = highBin.coerceIn(0, magnitudeSpectrum.size - 1)
        
        for (i in actualLow..actualHigh) {
            sum += magnitudeSpectrum[i] * magnitudeSpectrum[i]
        }
        return sqrt(sum / (actualHigh - actualLow + 1).coerceAtLeast(1)).toFloat()
    }
    
    private fun calculateSpectralCentroid(): Float {
        var weightedSum = 0.0
        var sum = 0.0
        
        for (i in 1 until magnitudeSpectrum.size) {
            val freq = i.toDouble() * sampleRate / fftSize
            weightedSum += freq * magnitudeSpectrum[i]
            sum += magnitudeSpectrum[i]
        }
        
        return if (sum > 0.001) (weightedSum / sum).toFloat() else 0f
    }
    
    private fun calculateSpectralFlatness(): Float {
        val startBin = 2
        val endBin = magnitudeSpectrum.size - 1
        
        var logSum = 0.0
        var sum = 0.0
        var validBins = 0
        
        for (i in startBin..endBin) {
            val mag = magnitudeSpectrum[i]
            if (mag > 1e-10) {
                logSum += ln(mag)
                sum += mag
                validBins++
            }
        }
        
        if (validBins == 0 || sum < 1e-10) return 1f
        
        val geometricMean = exp(logSum / validBins)
        val arithmeticMean = sum / validBins
        
        return (geometricMean / arithmeticMean).toFloat().coerceIn(0f, 1f)
    }
    
    /**
     * Pitch-Erkennung via Autokorrelation
     * Returns: Pair(estimatedPitch in Hz, pitchStrength 0-1)
     */
    private fun estimatePitch(samples: FloatArray): Pair<Float, Float> {
        // Frequenzbereich: 70 Hz - 700 Hz
        val minLag = sampleRate / 700  // ~23 samples für 700 Hz
        val maxLag = sampleRate / 70   // ~229 samples für 70 Hz
        
        val n = minOf(samples.size, maxLag * 2)
        if (n < maxLag) return Pair(0f, 0f)
        
        // Normalisierung
        var norm = 0f
        for (i in 0 until n) {
            norm += samples[i] * samples[i]
        }
        if (norm < 0.0001f) return Pair(0f, 0f)
        
        // Autokorrelation berechnen
        var maxCorr = 0f
        var bestLag = 0
        
        for (lag in minLag until minOf(maxLag, n / 2)) {
            var corr = 0f
            for (i in 0 until n - lag) {
                corr += samples[i] * samples[i + lag]
            }
            corr /= norm
            
            // Peak finden
            if (corr > maxCorr) {
                maxCorr = corr
                bestLag = lag
            }
        }
        
        val estimatedPitch = if (bestLag > 0) sampleRate.toFloat() / bestLag else 0f
        
        // Pitch Strength: Wie klar ist der Pitch?
        // Bei Rauschen: maxCorr < 0.3
        // Bei klarer Stimme: maxCorr > 0.5
        val pitchStrength = maxCorr.coerceIn(0f, 1f)
        
        return Pair(estimatedPitch, pitchStrength)
    }
    
    private fun applyConsistencyFilter(rawScore: Float): Float {
        scoreHistory.addLast(rawScore)
        if (scoreHistory.size > 12) scoreHistory.removeFirst()
        
        if (scoreHistory.size < 2) return rawScore * 0.7f
        
        // Nicht zu stark von vorherigen Werten abweichen
        val recentAvg = scoreHistory.takeLast(3).average().toFloat()
        
        return if (abs(rawScore - recentAvg) > 40f) {
            (rawScore * 0.6f + recentAvg * 0.4f)
        } else {
            rawScore
        }
    }
    
    private fun applySmoothing(rawScore: Float): Float {
        prevScore = smoothedScore
        smoothedScore = emaAlpha * rawScore + (1 - emaAlpha) * smoothedScore
        return smoothedScore.coerceIn(0f, 100f)
    }
    
    companion object {
        const val DEFAULT_SAMPLE_RATE = 16000
        const val DEFAULT_WINDOW_MS = 500
        const val DEFAULT_HOP_MS = 250
        
        fun createDefault() = AudioFeatureExtractor(
            sampleRate = DEFAULT_SAMPLE_RATE,
            windowSizeMs = DEFAULT_WINDOW_MS,
            hopSizeMs = DEFAULT_HOP_MS
        )
    }
}
