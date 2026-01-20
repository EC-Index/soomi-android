package com.soomi.baby.data.local.dao

import androidx.room.*
import com.soomi.baby.data.local.entity.*
import kotlinx.coroutines.flow.Flow

/**
 * Session DAO - CRUD operations for sessions and related data
 */
@Dao
interface SessionDao {
    
    // --- Sessions ---
    
    @Insert
    suspend fun insertSession(session: SessionEntity): Long
    
    @Update
    suspend fun updateSession(session: SessionEntity)
    
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: Long): SessionEntity?
    
    @Query("SELECT * FROM sessions ORDER BY start_time DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>
    
    @Query("SELECT * FROM sessions ORDER BY start_time DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<SessionEntity>
    
    @Query("SELECT * FROM sessions WHERE date = :date LIMIT 1")
    suspend fun getSessionByDate(date: String): SessionEntity?
    
    @Query("SELECT * FROM sessions WHERE end_time IS NULL ORDER BY start_time DESC LIMIT 1")
    suspend fun getActiveSession(): SessionEntity?
    
    @Query("SELECT * FROM sessions WHERE date >= :startDate ORDER BY date ASC")
    suspend fun getSessionsSince(startDate: String): List<SessionEntity>
    
    // --- Score Samples ---
    
    @Insert
    suspend fun insertScoreSample(sample: ScoreSampleEntity)
    
    @Insert
    suspend fun insertScoreSamples(samples: List<ScoreSampleEntity>)
    
    @Query("SELECT * FROM score_samples WHERE session_id = :sessionId ORDER BY timestamp ASC")
    suspend fun getScoreSamples(sessionId: Long): List<ScoreSampleEntity>
    
    @Query("SELECT AVG(score) FROM score_samples WHERE session_id = :sessionId")
    suspend fun getAverageScore(sessionId: Long): Float?
    
    // --- Unrest Events ---
    
    @Insert
    suspend fun insertUnrestEvent(event: UnrestEventEntity): Long
    
    @Update
    suspend fun updateUnrestEvent(event: UnrestEventEntity)
    
    @Query("SELECT * FROM unrest_events WHERE session_id = :sessionId ORDER BY start_time ASC")
    suspend fun getUnrestEvents(sessionId: Long): List<UnrestEventEntity>
    
    @Query("SELECT COUNT(*) FROM unrest_events WHERE session_id = :sessionId")
    suspend fun getUnrestEventCount(sessionId: Long): Int
    
    // --- FSM Events ---
    
    @Insert
    suspend fun insertFsmEvent(event: FsmEventEntity)
    
    @Query("SELECT * FROM fsm_events WHERE session_id = :sessionId ORDER BY timestamp ASC")
    suspend fun getFsmEvents(sessionId: Long): List<FsmEventEntity>
    
    // --- Morning Feedback ---
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: MorningFeedbackEntity)
    
    @Query("SELECT * FROM morning_feedback WHERE session_id = :sessionId")
    suspend fun getFeedback(sessionId: Long): MorningFeedbackEntity?
    
    @Query("SELECT * FROM morning_feedback ORDER BY submitted_at DESC LIMIT 1")
    suspend fun getLatestFeedback(): MorningFeedbackEntity?
    
    @Query("""
        SELECT s.*, f.helped_rating, f.annoying_rating 
        FROM sessions s 
        LEFT JOIN morning_feedback f ON s.id = f.session_id 
        WHERE s.date >= :startDate 
        ORDER BY s.date DESC
    """)
    suspend fun getSessionsWithFeedback(startDate: String): List<SessionWithFeedback>
    
    // --- Cleanup ---
    
    @Query("DELETE FROM sessions WHERE date < :beforeDate")
    suspend fun deleteOldSessions(beforeDate: String): Int
}

/**
 * Learning DAO - effectiveness tracking
 */
@Dao
interface LearningDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEffectiveness(effectiveness: InterventionEffectivenessEntity)
    
    @Query("SELECT * FROM intervention_effectiveness WHERE sound_type = :soundType AND baseline_mode = :baselineMode")
    suspend fun getEffectiveness(soundType: String, baselineMode: String): InterventionEffectivenessEntity?
    
    @Query("SELECT * FROM intervention_effectiveness WHERE baseline_mode = :baselineMode ORDER BY (CAST(success_count AS REAL) / CASE WHEN total_count = 0 THEN 1 ELSE total_count END) DESC")
    suspend fun getEffectivenessByBaseline(baselineMode: String): List<InterventionEffectivenessEntity>
    
    @Query("SELECT * FROM intervention_effectiveness")
    suspend fun getAllEffectiveness(): List<InterventionEffectivenessEntity>
    
    @Query("DELETE FROM intervention_effectiveness")
    suspend fun clearAllEffectiveness(): Int
}

/**
 * Telemetry DAO - for opt-in anonymous stats
 */
@Dao
interface TelemetryDao {
    
    @Insert
    suspend fun insertEvent(event: TelemetryEventEntity)
    
    @Query("SELECT * FROM telemetry_queue WHERE uploaded = 0 LIMIT :limit")
    suspend fun getPendingEvents(limit: Int = 100): List<TelemetryEventEntity>
    
    @Query("UPDATE telemetry_queue SET uploaded = 1 WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<Long>): Int
    
    @Query("DELETE FROM telemetry_queue WHERE uploaded = 1")
    suspend fun deleteUploaded(): Int
    
    @Query("DELETE FROM telemetry_queue")
    suspend fun clearAll(): Int
}

/**
 * Combined session with feedback for queries
 */
data class SessionWithFeedback(
    val id: Long,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long?,
    @ColumnInfo(name = "baseline_mode") val baselineMode: String,
    @ColumnInfo(name = "unrest_events") val unrestEvents: Int,
    @ColumnInfo(name = "total_soothing_seconds") val totalSoothingSeconds: Long,
    @ColumnInfo(name = "peak_unrest_score") val peakUnrestScore: Float,
    @ColumnInfo(name = "manual_interventions") val manualInterventions: Int,
    @ColumnInfo(name = "panic_stops") val panicStops: Int,
    val date: String,
    @ColumnInfo(name = "helped_rating") val helpedRating: String?,
    @ColumnInfo(name = "annoying_rating") val annoyingRating: String?
)
