package com.soomi.baby.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Core domain models for SOOMI
 * These represent the business logic concepts, independent of storage or UI.
 * 
 * v2.7: Simplified state machine with LISTENING, SOOTHING, COOLDOWN, STOPPED
 */

/**
 * Real-time unrest score computed from audio features.
 * 
 * IMPORTANT: This is NOT a medical stress indicator. It's a heuristic
 * likelihood of baby unrest based on audio characteristics like crying patterns.
 * 
 * @property value Normalized score 0-100 where:
 *   0-10: Very calm/quiet
 *   10-30: Mild stirring
 *   30-60: Moderate fussing
 *   60-80: Significant crying
 *   80-100: Intense distress
 * @property trend Direction of change (rising/falling/stable)
 * @property timestamp When this score was computed
 */
data class UnrestScore(
    val value: Float,
    val trend: Trend = Trend.STABLE,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Trend { RISING, FALLING, STABLE }
    
    val isCalm: Boolean get() = value < 10f
    val isRising: Boolean get() = value in 10f..30f
    val isCrying: Boolean get() = value > 60f
    val isCrisis: Boolean get() = value > 80f
}

/**
 * Baseline audio mode - what plays continuously (if anything)
 * This is a separate "layer" from intervention sounds
 */
enum class BaselineMode {
    OFF,      // No continuous audio
    GENTLE,   // Very soft background noise
    MEDIUM    // Moderate background noise
}

/**
 * SOOMI State Machine States (v2.7 - Simplified)
 * 
 * STOPPED   - Session not running
 * LISTENING - Session active, monitoring, only baseline playing (if enabled)
 * SOOTHING  - Intervention sound playing (baby is/was unruhig)
 * COOLDOWN  - Baby is calm, but sound continues with countdown timer
 * 
 * Legacy states kept for compatibility:
 * IDLE      - Alias for STOPPED
 * BASELINE  - Alias for LISTENING
 */
enum class SoomiState {
    STOPPED,    // Session not running
    LISTENING,  // Monitoring, baseline only
    SOOTHING,   // Intervention active
    COOLDOWN,   // Post-calm countdown, sound still playing
    
    // Legacy aliases for backward compatibility
    IDLE,       // Same as STOPPED
    BASELINE;   // Same as LISTENING
    
    /** Check if session is currently active */
    val isSessionActive: Boolean
        get() = this != STOPPED && this != IDLE
}

/**
 * Intervention levels for soothing audio
 */
enum class InterventionLevel(val volumeMultiplier: Float) {
    OFF(0f),
    LEVEL_1(0.3f),   // Gentle - just above baseline
    LEVEL_2(0.5f),   // Medium - noticeable
    LEVEL_3(0.7f),   // Strong - for crisis
    LEVEL_4(0.85f);  // Maximum allowed - never go to 1.0 for safety
    
    fun next(): InterventionLevel = when (this) {
        OFF -> LEVEL_1
        LEVEL_1 -> LEVEL_2
        LEVEL_2 -> LEVEL_3
        LEVEL_3 -> LEVEL_4
        LEVEL_4 -> LEVEL_4  // Cap at max
    }
    
    fun previous(): InterventionLevel = when (this) {
        OFF -> OFF
        LEVEL_1 -> OFF
        LEVEL_2 -> LEVEL_1
        LEVEL_3 -> LEVEL_2
        LEVEL_4 -> LEVEL_3
    }
}

/**
 * Types of soothing sounds available (legacy, replaced by SoundProfile)
 */
enum class SoundType {
    BROWN_NOISE,    // Deep, rumbling noise - very soothing for babies
    PINK_NOISE,     // Balanced noise
    SHUSH_PULSE     // Rhythmic shushing pattern modulated on noise
}

/**
 * FSM Thresholds for v2.7 SimpleFsm
 */
data class FsmThresholds(
    /** Unruhe-Schwelle für SOOTHING */
    val soothingTriggerThreshold: Float = 70f,
    /** Wie lange Schwelle gehalten werden muss (ms) */
    val soothingTriggerDurationMs: Long = 1500L,
    /** Unruhe-Schwelle für COOLDOWN */
    val cooldownTriggerThreshold: Float = 35f,
    /** Wie lange ruhig sein muss (ms) */
    val cooldownTriggerDurationMs: Long = 3000L,
    /** Unruhe für Retrigger in COOLDOWN */
    val retriggerThreshold: Float = 55f,
    /** Cooldown Timer Dauer (ms) */
    val cooldownDurationMs: Long = 45000L
)

/**
 * Configuration thresholds for the intervention engine (v2.6)
 */
data class InterventionConfig(
    // Thresholds
    val startThreshold: Float = 70f,        // Unruhe >= this starts SOOTHING
    val calmThreshold: Float = 35f,         // Unruhe <= this triggers COOLDOWN
    val retriggerThreshold: Float = 55f,    // Unruhe >= this during COOLDOWN re-triggers SOOTHING
    
    // Timing (in seconds)
    val startConfirmSec: Int = 2,           // How long above startThreshold to confirm
    val calmConfirmSec: Int = 3,            // How long below calmThreshold to confirm
    val cooldownSec: Int = 20,              // Cooldown duration
    val retriggerConfirmSec: Int = 1,       // How long above retriggerThreshold to re-trigger
    val minSoothingSec: Int = 10,           // Minimum soothing duration (anti-flicker)
    
    // Escalation limits
    val maxEscalationsPerEvent: Int = 2,    // Max level increases per unrest event
    
    // Volume
    val volumeCap: Float = 0.85f            // Maximum volume multiplier
)

/**
 * Legacy ThresholdConfig for compatibility
 */
data class ThresholdConfig(
    val zEarlyThreshold: Float = 15f,
    val zCrisisThreshold: Float = 80f,
    val zStopThreshold: Float = 10f,
    val earlyConfirmationWindowMs: Long = 2000,
    val crisisRiseTimeMs: Long = 1000,
    val evaluationTimeMs: Long = 12000,
    val cooldownTimeMs: Long = 45000,
    val improvementThreshold: Float = 10f,
    val volumeCap: Float = 0.85f
)

/**
 * Represents a complete overnight session
 */
data class Session(
    val id: Long = 0,
    val startTime: Instant,
    val endTime: Instant? = null,
    val baselineMode: BaselineMode,
    val unrestEvents: Int = 0,
    val totalSoothingTimeSeconds: Long = 0,
    val peakUnrestScore: Float = 0f,
    val manualInterventions: Int = 0,
    val panicStops: Int = 0,
    val date: LocalDate = LocalDate.now()
)

/**
 * Downsampled Z score for time series storage
 */
data class ScoreSample(
    val timestamp: Long,
    val score: Float,
    val state: SoomiState
)

/**
 * A discrete unrest event during a session
 */
data class UnrestEvent(
    val id: Long = 0,
    val sessionId: Long,
    val startTime: Instant,
    val endTime: Instant? = null,
    val peakScore: Float,
    val triggerState: SoomiState,
    val interventionUsed: SoundType,
    val interventionLevel: InterventionLevel,
    val deltaZ: Float? = null,
    val wasEffective: Boolean? = null
)

/**
 * Morning check-in response from parent
 */
data class MorningFeedback(
    val sessionId: Long,
    val helpedRating: HelpedRating,
    val annoyingRating: AnnoyingRating,
    val notes: String? = null,
    val submittedAt: Instant = Instant.now()
)

enum class HelpedRating { YES, A_BIT, NO, NOT_SURE }
enum class AnnoyingRating { NO, A_LITTLE, YES }

/**
 * Learning data for intervention effectiveness
 */
data class InterventionEffectiveness(
    val soundType: SoundType,
    val baselineMode: BaselineMode,
    val successCount: Int,
    val totalCount: Int,
    val averageDeltaZ: Float,
    val lastUsed: Instant
) {
    val successRate: Float get() = if (totalCount > 0) successCount.toFloat() / totalCount else 0f
}

/**
 * Night summary for progress view
 */
data class NightSummary(
    val date: LocalDate,
    val session: Session?,
    val feedback: MorningFeedback?,
    val unrestEventCount: Int,
    val totalSoothingMinutes: Int,
    val averageScore: Float?,
    val trend: ProgressTrend = ProgressTrend.STABLE
)

enum class ProgressTrend { IMPROVING, STABLE, WORSE }

/**
 * Telemetry event (only collected if user opts in)
 */
data class TelemetryEvent(
    val eventType: String,
    val timestamp: Instant,
    val sessionId: Long,
    val metadata: Map<String, Any> = emptyMap()
)

// ============================================================================
// Audio Output Routing Models
// ============================================================================

enum class OutputDeviceType {
    PHONE_SPEAKER,
    BLUETOOTH,
    AUTO
}

data class AudioOutputDevice(
    val id: Int,
    val name: String,
    val type: OutputDeviceType,
    val isConnected: Boolean = true,
    val isDefault: Boolean = false
) {
    companion object {
        val PHONE_SPEAKER = AudioOutputDevice(
            id = 0,
            name = "Phone Speaker",
            type = OutputDeviceType.PHONE_SPEAKER,
            isConnected = true,
            isDefault = true
        )
    }
}

data class AudioRoutingEvent(
    val timestamp: Instant = Instant.now(),
    val eventType: AudioRoutingEventType,
    val fromDevice: String? = null,
    val toDevice: String? = null,
    val reason: String? = null
)

enum class AudioRoutingEventType {
    DEVICE_SELECTED,
    DEVICE_CONNECTED,
    DEVICE_DISCONNECTED,
    FALLBACK_TRIGGERED,
    AUDIO_FOCUS_LOST,
    AUDIO_FOCUS_GAINED,
    OUTPUT_FAILED,
    OUTPUT_RECOVERED
}

enum class AudioFocusState {
    NONE,
    GAINED,
    LOST_TRANSIENT,
    LOST_PERMANENT,
    DUCKED
}
