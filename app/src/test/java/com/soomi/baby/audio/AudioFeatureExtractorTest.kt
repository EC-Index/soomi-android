package com.soomi.baby.audio

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random

class AudioFeatureExtractorTest {
    
    private lateinit var extractor: AudioFeatureExtractor
    
    @Before
    fun setup() {
        extractor = AudioFeatureExtractor.createDefault()
    }
    
    @Test
    fun `extractFeatures returns valid features for silence`() {
        val silence = FloatArray(8000) { 0f }
        val features = extractor.extractFeatures(silence)
        assertEquals(0f, features.rmsEnergy, 0.001f)
        assertEquals(0f, features.zeroCrossingRate, 0.001f)
    }
    
    @Test
    fun `extractFeatures returns higher energy for loud signal`() {
        val quiet = FloatArray(8000) { 0.01f * sin(it * 0.1f).toFloat() }
        val loud = FloatArray(8000) { 0.5f * sin(it * 0.1f).toFloat() }
        
        val quietFeatures = extractor.extractFeatures(quiet)
        extractor.reset()
        val loudFeatures = extractor.extractFeatures(loud)
        
        assertTrue(loudFeatures.rmsEnergy > quietFeatures.rmsEnergy)
    }
    
    @Test
    fun `computeUnrestScore returns value in valid range`() {
        val samples = FloatArray(8000) { Random.nextFloat() * 2 - 1 }
        val features = extractor.extractFeatures(samples)
        val score = extractor.computeUnrestScore(features)
        
        assertTrue(score >= 0f)
        assertTrue(score <= 100f)
    }
    
    @Test
    fun `computeUnrestScore returns low for silence`() {
        val silence = FloatArray(8000) { 0f }
        val features = extractor.extractFeatures(silence)
        val score = extractor.computeUnrestScore(features)
        assertTrue(score < 10f)
    }
    
    @Test
    fun `reset clears state properly`() {
        val samples = FloatArray(8000) { 0.5f * sin(it * 0.1f).toFloat() }
        repeat(5) {
            val features = extractor.extractFeatures(samples)
            extractor.computeUnrestScore(features)
        }
        extractor.reset()
        
        val silence = FloatArray(8000) { 0f }
        val features = extractor.extractFeatures(silence)
        val score = extractor.computeUnrestScore(features)
        assertTrue(score < 15f)
    }
}
