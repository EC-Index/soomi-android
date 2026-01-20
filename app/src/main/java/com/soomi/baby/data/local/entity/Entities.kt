package com.soomi.baby.data.local.entity

import androidx.room.*
import java.time.Instant
import java.time.LocalDate

/**
 * Room entities for local data persistence
 * 
 * PRIVACY: These store aggregated metrics only - never raw audio.
 */

/**
 * Session entity - represents one overnight monitoring session
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "start_time")
    val startTime: Long,  // Epoch millis
    
    @ColumnInfo(name = "end_time")
    val endTime: Long? = null,
    
    @ColumnInfo(name = "baseline_mode")
    val baselineMode: String,
    
    @ColumnInfo(name = "unrest_events")
    val unrestEvents: Int = 0,
    
    @ColumnInfo(name = "total_soothing_seconds")
    val totalSoothingSeconds: Long = 0,
    
    @ColumnInfo(name = "peak_unrest_score")
    val peakUnrestScore: Float = 0f,
    
    @ColumnInfo(name = "manual_interventions")
    val manualInterventions: Int = 0,
    
    @ColumnInfo(name = "panic_stops")
    val panicStops: Int = 0,
    
    @ColumnInfo(name = "date")
    val date: String  // LocalDate ISO string
)

/**
 * Score sample - downsampled time series for visualization
 */
@Entity(
    tableName = "score_samples",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("session_id")]
)
data class ScoreSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    
    @ColumnInfo(name = "score")
    val score: Float,
    
    @ColumnInfo(name = "state")
    val state: String
)

/**
 * Unrest event - discrete event during session
 */
@Entity(
    tableName = "unrest_events",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("session_id")]
)
data class UnrestEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    
    @ColumnInfo(name = "start_time")
    val startTime: Long,
    
    @ColumnInfo(name = "end_time")
    val endTime: Long? = null,
    
    @ColumnInfo(name = "peak_score")
    val peakScore: Float,
    
    @ColumnInfo(name = "trigger_state")
    val triggerState: String,
    
    @ColumnInfo(name = "intervention_sound")
    val interventionSound: String,
    
    @ColumnInfo(name = "intervention_level")
    val interventionLevel: String,
    
    @ColumnInfo(name = "delta_z")
    val deltaZ: Float? = null,
    
    @ColumnInfo(name = "was_effective")
    val wasEffective: Boolean? = null
)

/**
 * FSM event log - state transitions for debugging/analysis
 */
@Entity(
    tableName = "fsm_events",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("session_id")]
)
data class FsmEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    
    @ColumnInfo(name = "from_state")
    val fromState: String,
    
    @ColumnInfo(name = "to_state")
    val toState: String,
    
    @ColumnInfo(name = "trigger")
    val trigger: String? = null,  // What caused the transition
    
    @ColumnInfo(name = "score_at_transition")
    val scoreAtTransition: Float? = null
)

/**
 * Morning feedback from parent
 */
@Entity(
    tableName = "morning_feedback",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("session_id", unique = true)]
)
data class MorningFeedbackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    
    @ColumnInfo(name = "helped_rating")
    val helpedRating: String,
    
    @ColumnInfo(name = "annoying_rating")
    val annoyingRating: String,
    
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    
    @ColumnInfo(name = "submitted_at")
    val submittedAt: Long
)

/**
 * Learning data - intervention effectiveness tracking
 */
@Entity(
    tableName = "intervention_effectiveness",
    primaryKeys = ["sound_type", "baseline_mode"]
)
data class InterventionEffectivenessEntity(
    @ColumnInfo(name = "sound_type")
    val soundType: String,
    
    @ColumnInfo(name = "baseline_mode")
    val baselineMode: String,
    
    @ColumnInfo(name = "success_count")
    val successCount: Int = 0,
    
    @ColumnInfo(name = "total_count")
    val totalCount: Int = 0,
    
    @ColumnInfo(name = "average_delta_z")
    val averageDeltaZ: Float = 0f,
    
    @ColumnInfo(name = "last_used")
    val lastUsed: Long = 0
)

/**
 * Telemetry event queue (only used if user opted in)
 */
@Entity(tableName = "telemetry_queue")
data class TelemetryEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "event_type")
    val eventType: String,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    
    @ColumnInfo(name = "metadata_json")
    val metadataJson: String,  // JSON-encoded metadata
    
    @ColumnInfo(name = "uploaded")
    val uploaded: Boolean = false
)
