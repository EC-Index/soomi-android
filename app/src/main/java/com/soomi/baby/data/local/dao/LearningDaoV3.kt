package com.soomi.baby.data.local.dao

import androidx.room.*
import com.soomi.baby.data.local.entity.InterventionEventEntity
import com.soomi.baby.data.local.entity.SoundProfileScoreEntity
import kotlinx.coroutines.flow.Flow

/**
 * SOOMI v3.0 - Learning DAO
 * 
 * Data Access Object für das adaptive Learning System.
 * Verwaltet InterventionEvents und SoundProfileScores.
 */
@Dao
interface LearningDaoV3 {
    
    // =========================================================================
    // Intervention Events
    // =========================================================================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInterventionEvent(event: InterventionEventEntity): Long
    
    @Update
    suspend fun updateInterventionEvent(event: InterventionEventEntity)
    
    @Query("SELECT * FROM intervention_events WHERE id = :id")
    suspend fun getInterventionEvent(id: Long): InterventionEventEntity?
    
    @Query("SELECT * FROM intervention_events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getInterventionEventsForSession(sessionId: Long): List<InterventionEventEntity>
    
    @Query("SELECT * FROM intervention_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentInterventionEvents(limit: Int = 100): List<InterventionEventEntity>
    
    @Query("SELECT * FROM intervention_events WHERE contextTag = :contextTag ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getInterventionEventsByContext(contextTag: String, limit: Int = 50): List<InterventionEventEntity>
    
    @Query("SELECT COUNT(*) FROM intervention_events WHERE sessionId = :sessionId")
    suspend fun getEventCountForSession(sessionId: Long): Int
    
    @Query("""
        SELECT COUNT(*) FROM intervention_events 
        WHERE timestamp > :since
    """)
    suspend fun getEventCountSince(since: Long): Int
    
    @Query("SELECT AVG(deltaZ) FROM intervention_events WHERE sessionId = :sessionId")
    suspend fun getAvgDeltaZForSession(sessionId: Long): Float?
    
    @Query("SELECT AVG(deltaZ) FROM intervention_events WHERE contextTag = :contextTag AND timestamp > :since")
    suspend fun getAvgDeltaZByContext(contextTag: String, since: Long): Float?
    
    @Query("SELECT AVG(durationSec) FROM intervention_events WHERE wasEffective = 1 AND timestamp > :since")
    suspend fun getAvgTimeToCalm(since: Long): Float?
    
    // =========================================================================
    // Sound Profile Scores
    // =========================================================================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfileScore(score: SoundProfileScoreEntity): Long
    
    @Update
    suspend fun updateProfileScore(score: SoundProfileScoreEntity)
    
    @Upsert
    suspend fun upsertProfileScore(score: SoundProfileScoreEntity)
    
    @Query("""
        SELECT * FROM sound_profile_scores 
        WHERE soundType = :soundType 
          AND startLevel = :startLevel 
          AND baselineMode = :baselineMode 
          AND contextTag = :contextTag
        LIMIT 1
    """)
    suspend fun getProfileScore(
        soundType: String,
        startLevel: String,
        baselineMode: String,
        contextTag: String
    ): SoundProfileScoreEntity?
    
    @Query("""
        SELECT * FROM sound_profile_scores 
        WHERE contextTag = :contextTag 
        ORDER BY effectivenessScore DESC
        LIMIT 1
    """)
    suspend fun getBestProfileForContext(contextTag: String): SoundProfileScoreEntity?
    
    @Query("""
        SELECT * FROM sound_profile_scores 
        WHERE contextTag = :contextTag 
          AND baselineMode = :baselineMode
        ORDER BY effectivenessScore DESC
        LIMIT 1
    """)
    suspend fun getBestProfileForContextAndBaseline(
        contextTag: String,
        baselineMode: String
    ): SoundProfileScoreEntity?
    
    @Query("""
        SELECT * FROM sound_profile_scores 
        WHERE usageCount < :minUsage
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getExplorationProfile(minUsage: Int = 5): SoundProfileScoreEntity?
    
    @Query("""
        SELECT * FROM sound_profile_scores 
        WHERE contextTag = :contextTag 
          AND usageCount < :minUsage
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getExplorationProfileForContext(
        contextTag: String,
        minUsage: Int = 5
    ): SoundProfileScoreEntity?
    
    @Query("SELECT * FROM sound_profile_scores ORDER BY effectivenessScore DESC")
    suspend fun getAllProfileScores(): List<SoundProfileScoreEntity>
    
    @Query("SELECT * FROM sound_profile_scores WHERE contextTag = :contextTag ORDER BY effectivenessScore DESC")
    suspend fun getProfileScoresByContext(contextTag: String): List<SoundProfileScoreEntity>
    
    @Query("SELECT * FROM sound_profile_scores ORDER BY effectivenessScore DESC")
    fun observeAllProfileScores(): Flow<List<SoundProfileScoreEntity>>
    
    @Query("SELECT DISTINCT contextTag FROM sound_profile_scores")
    suspend fun getAllContextTags(): List<String>
    
    // =========================================================================
    // Aggregierte Statistiken
    // =========================================================================
    
    @Query("""
        SELECT AVG(effectivenessScore) 
        FROM sound_profile_scores 
        WHERE usageCount >= :minUsage
    """)
    suspend fun getOverallAvgEffectiveness(minUsage: Int = 3): Float?
    
    @Query("""
        SELECT COUNT(*) FROM intervention_events 
        WHERE wasEffective = 1 
          AND timestamp > :since
    """)
    suspend fun getSuccessfulInterventionCount(since: Long): Int
    
    @Query("""
        SELECT COUNT(*) FROM intervention_events 
        WHERE wasHighlyEffective = 1 
          AND timestamp > :since
    """)
    suspend fun getHighlyEffectiveInterventionCount(since: Long): Int
    
    @Query("""
        SELECT 
            CAST(SUM(CASE WHEN triggerType = 'PREDICTIVE' THEN 1 ELSE 0 END) AS FLOAT) / 
            CAST(COUNT(*) AS FLOAT) * 100
        FROM intervention_events 
        WHERE timestamp > :since
    """)
    suspend fun getPredictiveInterventionRate(since: Long): Float?
    
    // =========================================================================
    // Cleanup
    // =========================================================================
    
    @Query("DELETE FROM intervention_events WHERE timestamp < :before")
    suspend fun deleteOldInterventionEvents(before: Long)
    
    @Query("DELETE FROM sound_profile_scores WHERE usageCount = 0 AND lastUpdated < :before")
    suspend fun deleteUnusedProfiles(before: Long)
    
    @Query("DELETE FROM intervention_events")
    suspend fun clearAllInterventionEvents()
    
    @Query("DELETE FROM sound_profile_scores")
    suspend fun clearAllProfileScores()
}
