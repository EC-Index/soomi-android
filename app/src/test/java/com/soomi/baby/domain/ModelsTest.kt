package com.soomi.baby.domain

import com.soomi.baby.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ModelsTest {
    
    @Test
    fun `UnrestScore isCalm returns true for low values`() {
        val score = UnrestScore(5f)
        assertTrue(score.isCalm)
        assertFalse(score.isCrying)
        assertFalse(score.isCrisis)
    }
    
    @Test
    fun `UnrestScore isCrying returns true for high values`() {
        val score = UnrestScore(70f)
        assertFalse(score.isCalm)
        assertTrue(score.isCrying)
        assertFalse(score.isCrisis)
    }
    
    @Test
    fun `UnrestScore isCrisis returns true for very high values`() {
        val score = UnrestScore(85f)
        assertFalse(score.isCalm)
        assertTrue(score.isCrying)
        assertTrue(score.isCrisis)
    }
    
    @Test
    fun `InterventionLevel next escalates correctly`() {
        assertEquals(InterventionLevel.LEVEL_1, InterventionLevel.OFF.next())
        assertEquals(InterventionLevel.LEVEL_2, InterventionLevel.LEVEL_1.next())
        assertEquals(InterventionLevel.LEVEL_3, InterventionLevel.LEVEL_2.next())
        assertEquals(InterventionLevel.LEVEL_4, InterventionLevel.LEVEL_3.next())
        assertEquals(InterventionLevel.LEVEL_4, InterventionLevel.LEVEL_4.next()) // Caps at max
    }
    
    @Test
    fun `InterventionLevel previous de-escalates correctly`() {
        assertEquals(InterventionLevel.OFF, InterventionLevel.OFF.previous()) // Stays at min
        assertEquals(InterventionLevel.OFF, InterventionLevel.LEVEL_1.previous())
        assertEquals(InterventionLevel.LEVEL_1, InterventionLevel.LEVEL_2.previous())
        assertEquals(InterventionLevel.LEVEL_2, InterventionLevel.LEVEL_3.previous())
        assertEquals(InterventionLevel.LEVEL_3, InterventionLevel.LEVEL_4.previous())
    }
    
    @Test
    fun `InterventionLevel volumeMultiplier is bounded`() {
        InterventionLevel.entries.forEach { level ->
            assertTrue(level.volumeMultiplier >= 0f)
            assertTrue(level.volumeMultiplier <= 1f)
        }
    }
    
    @Test
    fun `ThresholdConfig has sensible defaults`() {
        val config = ThresholdConfig()
        
        assertTrue(config.zEarlyThreshold < config.zCrisisThreshold)
        assertTrue(config.zStopThreshold < config.zEarlyThreshold)
        assertTrue(config.volumeCap <= 1f)
        assertTrue(config.volumeCap > 0f)
    }
    
    @Test
    fun `InterventionEffectiveness successRate calculates correctly`() {
        val effective = InterventionEffectiveness(
            soundType = SoundType.BROWN_NOISE,
            baselineMode = BaselineMode.GENTLE,
            successCount = 8,
            totalCount = 10,
            averageDeltaZ = 15f,
            lastUsed = java.time.Instant.now()
        )
        
        assertEquals(0.8f, effective.successRate, 0.001f)
    }
    
    @Test
    fun `InterventionEffectiveness successRate handles zero total`() {
        val noData = InterventionEffectiveness(
            soundType = SoundType.BROWN_NOISE,
            baselineMode = BaselineMode.OFF,
            successCount = 0,
            totalCount = 0,
            averageDeltaZ = 0f,
            lastUsed = java.time.Instant.now()
        )
        
        assertEquals(0f, noData.successRate, 0.001f)
    }
}
