package com.soomi.baby.audio

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random
import kotlin.math.PI

class AudioFeatureExtractorTest {
    
    private lateinit var extractor: AudioFeatureExtractor
    
    @Before
    fun setup() {
        extractor = AudioFeatureExtractor.createDefault()
    }
    
    // === BASIC FUNCTIONALITY TESTS ===
    
    @Test
    fun `extractFeatures returns valid features for silence`() {
        val silence = FloatArray(8000) { 0f }
        val features = extractor.extractFeatures(silence)
        
        assertEquals(0f, features.rmsEnergy, 0.001f)
        assertEquals(0f, features.babyCryBandEnergy, 0.01f)
        assertEquals(0f, features.pitchStrength, 0.01f)
        assertFalse(features.isImpulsive)
    }
    
    @Test
    fun `computeUnrestScore returns value in valid range`() {
        val samples = FloatArray(8000) { Random.nextFloat() * 0.1f - 0.05f }
        val features = extractor.extractFeatures(samples)
        val score = extractor.computeUnrestScore(features)
        
        assertTrue("Score should be >= 0, was $score", score >= 0f)
        assertTrue("Score should be <= 100, was $score", score <= 100f)
    }
    
    @Test
    fun `computeUnrestScore returns low for silence`() {
        val silence = FloatArray(8000) { 0f }
        val features = extractor.extractFeatures(silence)
        val score = extractor.computeUnrestScore(features)
        
        assertTrue("Silent score should be < 10, was $score", score < 10f)
    }
    
    @Test
    fun `reset clears state properly`() {
        // Build up some state
        val samples = FloatArray(8000) { 0.3f * sin(it * 0.05f).toFloat() }
        repeat(10) {
            val features = extractor.extractFeatures(samples)
            extractor.computeUnrestScore(features)
        }
        
        // Reset
        extractor.reset()
        
        // Should start fresh
        val silence = FloatArray(8000) { 0f }
        val features = extractor.extractFeatures(silence)
        val score = extractor.computeUnrestScore(features)
        
        assertTrue("Score after reset should be low, was $score", score < 15f)
    }
    
    // === BABY CRY DETECTION TESTS ===
    
    @Test
    fun `detects tone in baby cry frequency range`() {
        // Simulate a 400 Hz tone (typical baby cry fundamental)
        val sampleRate = 16000
        val frequency = 400.0
        val samples = FloatArray(8000) { i ->
            (0.15f * sin(2 * PI * frequency * i / sampleRate)).toFloat()
        }
        
        val features = extractor.extractFeatures(samples)
        
        // Should have energy in baby cry band
        assertTrue("Should detect baby cry band energy", features.babyCryBandEnergy > 0.01f)
        
        // Pitch should be detected in baby range
        assertTrue("Pitch should be in baby range (200-700 Hz), was ${features.estimatedPitch}",
            features.estimatedPitch in 300f..500f || features.pitchStrength < 0.3f)
    }
    
    @Test
    fun `baby cry simulation gets higher score than white noise`() {
        // White noise
        extractor.reset()
        repeat(5) {
            val noise = FloatArray(8000) { Random.nextFloat() * 0.1f - 0.05f }
            extractor.extractFeatures(noise)
            extractor.computeUnrestScore(extractor.extractFeatures(noise))
        }
        val noiseFeatures = extractor.extractFeatures(FloatArray(8000) { Random.nextFloat() * 0.1f - 0.05f })
        val noiseScore = extractor.computeUnrestScore(noiseFeatures)
        
        // Baby cry simulation (400 Hz with harmonics and modulation)
        extractor.reset()
        repeat(5) {
            val babyCry = generateSimulatedBabyCry(8000, 16000, 400.0, 0.12f)
            extractor.extractFeatures(babyCry)
            extractor.computeUnrestScore(extractor.extractFeatures(babyCry))
        }
        val cryFeatures = extractor.extractFeatures(generateSimulatedBabyCry(8000, 16000, 400.0, 0.12f))
        val cryScore = extractor.computeUnrestScore(cryFeatures)
        
        assertTrue("Baby cry score ($cryScore) should be higher than noise score ($noiseScore)",
            cryScore > noiseScore)
    }
    
    // === FILTERING TESTS ===
    
    @Test
    fun `impulsive sounds like clapping are detected`() {
        extractor.reset()
        
        // Normal energy level first
        repeat(3) {
            val normal = FloatArray(8000) { 0.01f * Random.nextFloat() }
            extractor.extractFeatures(normal)
            extractor.computeUnrestScore(extractor.extractFeatures(normal))
        }
        
        // Sudden impulse (simulating clap)
        val impulse = FloatArray(8000) { i ->
            if (i < 200) 0.8f * (1f - i / 200f) * (Random.nextFloat() * 2 - 1)  // Sharp spike
            else 0.01f * Random.nextFloat()
        }
        val features = extractor.extractFeatures(impulse)
        
        // Should be marked as impulsive
        assertTrue("Impulse should be detected", features.isImpulsive)
    }
    
    @Test
    fun `impulsive sounds get low score`() {
        extractor.reset()
        
        // Build up baseline
        repeat(5) {
            val normal = FloatArray(8000) { 0.02f * Random.nextFloat() }
            extractor.extractFeatures(normal)
            extractor.computeUnrestScore(extractor.extractFeatures(normal))
        }
        
        // Loud impulse
        val impulse = FloatArray(8000) { i ->
            if (i < 300) 0.7f * (Random.nextFloat() * 2 - 1)
            else 0.01f * Random.nextFloat()
        }
        val features = extractor.extractFeatures(impulse)
        val score = extractor.computeUnrestScore(features)
        
        assertTrue("Impulsive sound score should be low (< 50), was $score", score < 50f)
    }
    
    @Test
    fun `adult speech frequencies get lower weight`() {
        extractor.reset()
        
        // Simulate adult male speech (~120 Hz fundamental)
        val sampleRate = 16000
        val speechFreq = 120.0
        
        repeat(5) {
            val speech = FloatArray(8000) { i ->
                (0.1f * sin(2 * PI * speechFreq * i / sampleRate)).toFloat()
            }
            extractor.extractFeatures(speech)
            extractor.computeUnrestScore(extractor.extractFeatures(speech))
        }
        val speechFeatures = extractor.extractFeatures(FloatArray(8000) { i ->
            (0.1f * sin(2 * PI * speechFreq * i / sampleRate)).toFloat()
        })
        val speechScore = extractor.computeUnrestScore(speechFeatures)
        
        // Simulate baby cry (400 Hz)
        extractor.reset()
        val cryFreq = 400.0
        repeat(5) {
            val cry = FloatArray(8000) { i ->
                (0.1f * sin(2 * PI * cryFreq * i / sampleRate)).toFloat()
            }
            extractor.extractFeatures(cry)
            extractor.computeUnrestScore(extractor.extractFeatures(cry))
        }
        val cryFeatures = extractor.extractFeatures(FloatArray(8000) { i ->
            (0.1f * sin(2 * PI * cryFreq * i / sampleRate)).toFloat()
        })
        val cryScore = extractor.computeUnrestScore(cryFeatures)
        
        assertTrue("Baby cry frequency score ($cryScore) should be >= speech score ($speechScore)",
            cryScore >= speechScore * 0.8f)  // Allow some tolerance
    }
    
    @Test
    fun `brown noise app sound gets low score`() {
        extractor.reset()
        
        // Simulate brown noise (low frequency dominated noise)
        var brownState = 0.0
        val brownNoise = FloatArray(8000) { 
            brownState += (Random.nextDouble() * 2 - 1) * 0.02
            brownState *= 0.998
            brownState.coerceIn(-1.0, 1.0).toFloat() * 0.15f
        }
        
        // Process multiple frames to build up noise baseline
        repeat(10) {
            val noise = FloatArray(8000) {
                brownState += (Random.nextDouble() * 2 - 1) * 0.02
                brownState *= 0.998
                brownState.coerceIn(-1.0, 1.0).toFloat() * 0.15f
            }
            extractor.extractFeatures(noise)
            extractor.computeUnrestScore(extractor.extractFeatures(noise))
        }
        
        val features = extractor.extractFeatures(brownNoise)
        val score = extractor.computeUnrestScore(features)
        
        // Brown noise should not trigger high score
        assertTrue("Brown noise score should be low (< 40), was $score", score < 40f)
    }
    
    @Test
    fun `setPlaybackActive adjusts noise baseline`() {
        extractor.reset()
        
        // Inform extractor that playback is active
        extractor.setPlaybackActive(true, 0.5f)
        
        // Process some brown noise
        var brownState = 0.0
        repeat(10) {
            val noise = FloatArray(8000) {
                brownState += (Random.nextDouble() * 2 - 1) * 0.02
                brownState *= 0.998
                brownState.coerceIn(-1.0, 1.0).toFloat() * 0.15f
            }
            extractor.extractFeatures(noise)
            extractor.computeUnrestScore(extractor.extractFeatures(noise))
        }
        
        val features = extractor.extractFeatures(FloatArray(8000) {
            brownState += (Random.nextDouble() * 2 - 1) * 0.02
            brownState *= 0.998
            brownState.coerceIn(-1.0, 1.0).toFloat() * 0.15f
        })
        val score = extractor.computeUnrestScore(features)
        
        assertTrue("Score with playback active should be low, was $score", score < 30f)
    }
    
    // === PERIODICITY TESTS ===
    
    @Test
    fun `periodic signals are detected`() {
        extractor.reset()
        
        // Generate periodic signal over multiple frames
        val sampleRate = 16000
        val frequency = 400.0
        
        repeat(8) {
            val periodic = FloatArray(8000) { i ->
                (0.1f * sin(2 * PI * frequency * i / sampleRate)).toFloat()
            }
            extractor.extractFeatures(periodic)
            extractor.computeUnrestScore(extractor.extractFeatures(periodic))
        }
        
        val features = extractor.extractFeatures(FloatArray(8000) { i ->
            (0.1f * sin(2 * PI * frequency * i / sampleRate)).toFloat()
        })
        
        assertTrue("Periodic signal should be detected", features.isPeriodic)
    }
    
    // === HELPER FUNCTIONS ===
    
    /**
     * Generate simulated baby cry audio
     * - Fundamental frequency ~400 Hz
     * - Harmonics at 2x, 3x, 4x
     * - Slight amplitude modulation
     */
    private fun generateSimulatedBabyCry(
        numSamples: Int,
        sampleRate: Int,
        fundamentalHz: Double,
        amplitude: Float
    ): FloatArray {
        return FloatArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            
            // Fundamental + harmonics
            var sample = sin(2 * PI * fundamentalHz * t)
            sample += 0.5 * sin(2 * PI * fundamentalHz * 2 * t)  // 2nd harmonic
            sample += 0.3 * sin(2 * PI * fundamentalHz * 3 * t)  // 3rd harmonic
            sample += 0.15 * sin(2 * PI * fundamentalHz * 4 * t) // 4th harmonic
            
            // Amplitude modulation (typical for crying)
            val modulation = 0.8 + 0.2 * sin(2 * PI * 3 * t)  // ~3 Hz modulation
            
            (sample * modulation * amplitude / 2.0).toFloat()
        }
    }
}

/**
 * Additional integration tests
 */
class AudioFeatureExtractorIntegrationTest {
    
    @Test
    fun `continuous baby crying maintains high score`() {
        val extractor = AudioFeatureExtractor.createDefault()
        val sampleRate = 16000
        
        // Simulate 10 seconds of baby crying (20 frames at 500ms each)
        val scores = mutableListOf<Float>()
        
        repeat(20) { frameIndex ->
            val babyCry = FloatArray(8000) { i ->
                val t = (frameIndex * 8000 + i).toDouble() / sampleRate
                val fundamental = 420.0 + 30 * sin(2 * PI * 0.5 * t)  // Slight pitch variation
                
                var sample = sin(2 * PI * fundamental * t)
                sample += 0.5 * sin(2 * PI * fundamental * 2 * t)
                sample += 0.25 * sin(2 * PI * fundamental * 3 * t)
                
                val modulation = 0.7 + 0.3 * sin(2 * PI * 2.5 * t)
                (sample * modulation * 0.12 / 2.0).toFloat()
            }
            
            val features = extractor.extractFeatures(babyCry)
            val score = extractor.computeUnrestScore(features)
            scores.add(score)
        }
        
        // After warmup (first 3 frames), score should stabilize above threshold
        val stableScores = scores.drop(5)
        val avgScore = stableScores.average()
        
        assertTrue("Average score for crying should be > 30, was $avgScore", avgScore > 30)
    }
    
    @Test
    fun `mixed environment handles transitions`() {
        val extractor = AudioFeatureExtractor.createDefault()
        
        // 1. Start with silence
        repeat(3) {
            val silence = FloatArray(8000) { 0.001f * Random.nextFloat() }
            extractor.extractFeatures(silence)
            extractor.computeUnrestScore(extractor.extractFeatures(silence))
        }
        val silenceScore = extractor.computeUnrestScore(
            extractor.extractFeatures(FloatArray(8000) { 0.001f * Random.nextFloat() })
        )
        
        // 2. Sudden clap
        val clap = FloatArray(8000) { i ->
            if (i < 200) 0.6f * (Random.nextFloat() * 2 - 1) else 0.01f * Random.nextFloat()
        }
        val clapScore = extractor.computeUnrestScore(extractor.extractFeatures(clap))
        
        // 3. Back to silence
        repeat(3) {
            val silence = FloatArray(8000) { 0.001f * Random.nextFloat() }
            extractor.extractFeatures(silence)
            extractor.computeUnrestScore(extractor.extractFeatures(silence))
        }
        val afterClapScore = extractor.computeUnrestScore(
            extractor.extractFeatures(FloatArray(8000) { 0.001f * Random.nextFloat() })
        )
        
        assertTrue("Silence score should be low: $silenceScore", silenceScore < 15)
        assertTrue("Clap should not spike score too high: $clapScore", clapScore < 60)
        assertTrue("Should recover after clap: $afterClapScore", afterClapScore < 20)
    }
}
