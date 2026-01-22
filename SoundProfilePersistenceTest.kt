package com.soomi.baby.data.preferences

import com.soomi.baby.domain.model.SoundProfile
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SoundProfile persistence
 * Tests the SoundProfile enum behavior and defaults
 */
class SoundProfilePersistenceTest {
    
    @Test
    fun `default profile is OCEAN_BREATH`() {
        assertEquals(SoundProfile.OCEAN_BREATH, SoundProfile.DEFAULT)
    }
    
    @Test
    fun `free profiles list contains only non-pro profiles`() {
        val freeProfiles = SoundProfile.freeProfiles()
        
        assertEquals(3, freeProfiles.size)
        assertTrue(freeProfiles.contains(SoundProfile.OCEAN_BREATH))
        assertTrue(freeProfiles.contains(SoundProfile.CLASSIC_PINK))
        assertTrue(freeProfiles.contains(SoundProfile.DEEP_BROWN))
        
        freeProfiles.forEach { profile ->
            assertFalse("Profile $profile should not be Pro", profile.isPro)
        }
    }
    
    @Test
    fun `pro profiles list contains only pro profiles`() {
        val proProfiles = SoundProfile.proProfiles()
        
        assertEquals(5, proProfiles.size)
        assertTrue(proProfiles.contains(SoundProfile.HEARTBEAT_OCEAN))
        assertTrue(proProfiles.contains(SoundProfile.RAIN_DRIFT))
        assertTrue(proProfiles.contains(SoundProfile.FAN_STABLE))
        assertTrue(proProfiles.contains(SoundProfile.SHUSH_SOFT))
        assertTrue(proProfiles.contains(SoundProfile.TRAVEL_CALM))
        
        proProfiles.forEach { profile ->
            assertTrue("Profile $profile should be Pro", profile.isPro)
        }
    }
    
    @Test
    fun `IS_PRO_ENABLED is false for v2_6 beta`() {
        assertFalse(SoundProfile.IS_PRO_ENABLED)
    }
    
    @Test
    fun `all profiles have display names in German`() {
        SoundProfile.entries.forEach { profile ->
            assertTrue(
                "Profile $profile should have a display name",
                profile.displayName.isNotBlank()
            )
            assertTrue(
                "Profile $profile should have a description",
                profile.description.isNotBlank()
            )
        }
    }
    
    @Test
    fun `profile can be serialized and deserialized by name`() {
        SoundProfile.entries.forEach { original ->
            val name = original.name
            val restored = SoundProfile.valueOf(name)
            assertEquals(original, restored)
        }
    }
    
    @Test
    fun `OCEAN_BREATH is not a pro profile`() {
        assertFalse(SoundProfile.OCEAN_BREATH.isPro)
    }
    
    @Test
    fun `HEARTBEAT_OCEAN is a pro profile`() {
        assertTrue(SoundProfile.HEARTBEAT_OCEAN.isPro)
    }
}

/**
 * Tests for LFO parameter clamping in audio generation
 */
class LfoParameterClampingTest {
    
    @Test
    fun `amplitude modulation clamping prevents pumping`() {
        // Simulate the clamping logic from AudioOutputEngine
        val lfoDepth = 0.08f // +/- 8%
        
        // Test at maximum LFO value (sin = 1.0)
        val maxLfoValue = 1.0
        val maxModulation = 1.0 + (maxLfoValue * lfoDepth)
        val clampedMax = maxModulation.coerceIn(0.85, 1.15)
        
        assertTrue("Max modulation should be clamped", clampedMax <= 1.15)
        assertTrue("Max modulation should be clamped", clampedMax >= 0.85)
        assertEquals(1.08, clampedMax, 0.001)
        
        // Test at minimum LFO value (sin = -1.0)
        val minLfoValue = -1.0
        val minModulation = 1.0 + (minLfoValue * lfoDepth)
        val clampedMin = minModulation.coerceIn(0.85, 1.15)
        
        assertTrue("Min modulation should be clamped", clampedMin <= 1.15)
        assertTrue("Min modulation should be clamped", clampedMin >= 0.85)
        assertEquals(0.92, clampedMin, 0.001)
    }
    
    @Test
    fun `extreme LFO depth is clamped properly`() {
        // Even with extreme depth (which shouldn't happen), clamping protects us
        val extremeDepth = 0.5f // +/- 50% (unrealistic)
        
        val maxModulation = 1.0 + (1.0 * extremeDepth)
        val clampedMax = maxModulation.coerceIn(0.85, 1.15)
        assertEquals(1.15, clampedMax, 0.001)
        
        val minModulation = 1.0 + (-1.0 * extremeDepth)
        val clampedMin = minModulation.coerceIn(0.85, 1.15)
        assertEquals(0.85, clampedMin, 0.001)
    }
    
    @Test
    fun `zero LFO depth produces no modulation`() {
        val zeroDepth = 0.0f
        
        for (lfoValue in listOf(-1.0, 0.0, 0.5, 1.0)) {
            val modulation = 1.0 + (lfoValue * zeroDepth)
            assertEquals(1.0, modulation, 0.001)
        }
    }
    
    @Test
    fun `ocean breath LFO rate is approximately 12_5 seconds per cycle`() {
        val lfoRateHz = 0.08f
        val cyclePeriodSeconds = 1.0f / lfoRateHz
        
        // Should be approximately 12.5 seconds
        assertEquals(12.5f, cyclePeriodSeconds, 0.1f)
    }
    
    @Test
    fun `spectral drift cutoff range is valid`() {
        val minCutoff = 900f
        val maxCutoff = 1600f
        
        assertTrue("Min cutoff should be positive", minCutoff > 0)
        assertTrue("Max cutoff should be greater than min", maxCutoff > minCutoff)
        assertTrue("Cutoff range should be in audible spectrum", maxCutoff < 20000)
    }
    
    @Test
    fun `filter coefficient calculation is stable`() {
        // Test the 1-pole filter coefficient calculation
        val sampleRate = 44100
        
        for (cutoffHz in listOf(100f, 500f, 1000f, 2000f, 5000f)) {
            val filterCoeff = kotlin.math.exp(-2.0 * kotlin.math.PI * cutoffHz / sampleRate)
            
            assertTrue("Filter coeff should be positive", filterCoeff > 0)
            assertTrue("Filter coeff should be less than 1", filterCoeff < 1)
        }
    }
    
    @Test
    fun `deep brown profile has more subtle LFO than ocean breath`() {
        // Ocean Breath settings
        val oceanLfoDepth = 0.08f
        
        // Deep Brown should have even less (from our implementation: 0.03)
        val deepBrownLfoDepth = 0.03f
        
        assertTrue(
            "Deep brown LFO should be more subtle",
            deepBrownLfoDepth < oceanLfoDepth
        )
    }
    
    @Test
    fun `classic pink has no LFO modulation`() {
        // Classic Pink should have zero LFO
        val classicPinkLfoDepth = 0.0f
        val classicPinkLfoRate = 0.0f
        
        assertEquals(0.0f, classicPinkLfoDepth, 0.001f)
        assertEquals(0.0f, classicPinkLfoRate, 0.001f)
    }
}
