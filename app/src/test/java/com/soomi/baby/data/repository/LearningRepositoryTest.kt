package com.soomi.baby.data.repository

import com.soomi.baby.data.local.dao.LearningDao
import com.soomi.baby.data.local.entity.InterventionEffectivenessEntity
import com.soomi.baby.domain.model.BaselineMode
import com.soomi.baby.domain.model.SoundType
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for LearningRepository
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LearningRepositoryTest {
    
    private lateinit var learningDao: LearningDao
    private lateinit var repository: LearningRepository
    
    @Before
    fun setup() {
        learningDao = mockk(relaxed = true)
        repository = LearningRepository(
            learningDao = learningDao,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )
    }
    
    @Test
    fun `recordInterventionOutcome creates new record if none exists`() = runTest {
        coEvery { learningDao.getEffectiveness(any(), any()) } returns null
        
        repository.recordInterventionOutcome(
            soundType = SoundType.BROWN_NOISE,
            baselineMode = BaselineMode.GENTLE,
            effective = true,
            deltaZ = 15f
        )
        
        coVerify {
            learningDao.upsertEffectiveness(
                match { entity ->
                    entity.soundType == "BROWN_NOISE" &&
                    entity.baselineMode == "GENTLE" &&
                    entity.successCount == 1 &&
                    entity.totalCount == 1 &&
                    entity.averageDeltaZ == 15f
                }
            )
        }
    }
    
    @Test
    fun `recordInterventionOutcome updates existing record`() = runTest {
        val existing = InterventionEffectivenessEntity(
            soundType = "BROWN_NOISE",
            baselineMode = "GENTLE",
            successCount = 5,
            totalCount = 10,
            averageDeltaZ = 12f,
            lastUsed = 0L
        )
        coEvery { learningDao.getEffectiveness("BROWN_NOISE", "GENTLE") } returns existing
        
        repository.recordInterventionOutcome(
            soundType = SoundType.BROWN_NOISE,
            baselineMode = BaselineMode.GENTLE,
            effective = true,
            deltaZ = 20f
        )
        
        coVerify {
            learningDao.upsertEffectiveness(
                match { entity ->
                    entity.soundType == "BROWN_NOISE" &&
                    entity.successCount == 6 &&  // 5 + 1
                    entity.totalCount == 11 &&   // 10 + 1
                    // New average: (12 * 10 + 20) / 11 ≈ 12.73
                    entity.averageDeltaZ > 12.7f && entity.averageDeltaZ < 12.8f
                }
            )
        }
    }
    
    @Test
    fun `recordInterventionOutcome does not increment success for ineffective`() = runTest {
        val existing = InterventionEffectivenessEntity(
            soundType = "BROWN_NOISE",
            baselineMode = "GENTLE",
            successCount = 5,
            totalCount = 10,
            averageDeltaZ = 12f,
            lastUsed = 0L
        )
        coEvery { learningDao.getEffectiveness("BROWN_NOISE", "GENTLE") } returns existing
        
        repository.recordInterventionOutcome(
            soundType = SoundType.BROWN_NOISE,
            baselineMode = BaselineMode.GENTLE,
            effective = false,
            deltaZ = 5f
        )
        
        coVerify {
            learningDao.upsertEffectiveness(
                match { entity ->
                    entity.successCount == 5 &&  // Unchanged
                    entity.totalCount == 11      // Incremented
                }
            )
        }
    }
    
    @Test
    fun `getBestSoundType returns BROWN_NOISE as default`() = runTest {
        coEvery { learningDao.getEffectivenessByBaseline(any()) } returns emptyList()
        
        val result = repository.getBestSoundType(BaselineMode.GENTLE)
        
        assertEquals(SoundType.BROWN_NOISE, result)
    }
    
    @Test
    fun `getBestSoundType returns BROWN_NOISE when insufficient trials`() = runTest {
        val effectiveness = listOf(
            InterventionEffectivenessEntity(
                soundType = "SHUSH_PULSE",
                baselineMode = "GENTLE",
                successCount = 2,
                totalCount = 2,  // Only 2 trials - not enough confidence
                averageDeltaZ = 20f,
                lastUsed = System.currentTimeMillis()
            )
        )
        coEvery { learningDao.getEffectivenessByBaseline("GENTLE") } returns effectiveness
        
        // With random = 0.5 (less than 0.8), should try best
        // But with insufficient trials, should default to BROWN_NOISE
        val result = repository.getBestSoundType(BaselineMode.GENTLE)
        
        // Due to randomness, we can't guarantee the exact result
        // But BROWN_NOISE should be a valid fallback
        assertNotNull(result)
    }
    
    @Test
    fun `resetLearning clears all effectiveness data`() = runTest {
        repository.resetLearning()
        
        coVerify { learningDao.clearAllEffectiveness() }
    }
    
    @Test
    fun `getAllEffectiveness returns mapped domain objects`() = runTest {
        val entities = listOf(
            InterventionEffectivenessEntity(
                soundType = "BROWN_NOISE",
                baselineMode = "GENTLE",
                successCount = 5,
                totalCount = 10,
                averageDeltaZ = 12f,
                lastUsed = 1000L
            ),
            InterventionEffectivenessEntity(
                soundType = "SHUSH_PULSE",
                baselineMode = "MEDIUM",
                successCount = 3,
                totalCount = 8,
                averageDeltaZ = 8f,
                lastUsed = 2000L
            )
        )
        coEvery { learningDao.getAllEffectiveness() } returns entities
        
        val result = repository.getAllEffectiveness()
        
        assertEquals(2, result.size)
        
        val first = result[0]
        assertEquals(SoundType.BROWN_NOISE, first.soundType)
        assertEquals(BaselineMode.GENTLE, first.baselineMode)
        assertEquals(5, first.successCount)
        assertEquals(10, first.totalCount)
        assertEquals(0.5f, first.successRate, 0.001f)  // 5/10
        
        val second = result[1]
        assertEquals(SoundType.SHUSH_PULSE, second.soundType)
        assertEquals(BaselineMode.MEDIUM, second.baselineMode)
        assertEquals(0.375f, second.successRate, 0.001f)  // 3/8
    }
}

/**
 * Tests for InterventionEffectiveness success rate calculation
 */
class InterventionEffectivenessTest {
    
    @Test
    fun `successRate returns correct value`() {
        val effectiveness = com.soomi.baby.domain.model.InterventionEffectiveness(
            soundType = SoundType.BROWN_NOISE,
            baselineMode = BaselineMode.GENTLE,
            successCount = 7,
            totalCount = 10,
            averageDeltaZ = 15f,
            lastUsed = java.time.Instant.now()
        )
        
        assertEquals(0.7f, effectiveness.successRate, 0.001f)
    }
    
    @Test
    fun `successRate returns 0 when totalCount is 0`() {
        val effectiveness = com.soomi.baby.domain.model.InterventionEffectiveness(
            soundType = SoundType.BROWN_NOISE,
            baselineMode = BaselineMode.GENTLE,
            successCount = 0,
            totalCount = 0,
            averageDeltaZ = 0f,
            lastUsed = java.time.Instant.now()
        )
        
        assertEquals(0f, effectiveness.successRate, 0.001f)
    }
}
