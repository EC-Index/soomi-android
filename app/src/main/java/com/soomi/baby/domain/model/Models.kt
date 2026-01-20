package com.soomi.baby.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Core domain models for SOOMI
 * These represent the business logic concepts, independent of storage or UI.
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
 */
enum class BaselineMode {
    OFF,      // No continuous audio
    GENTLE,   // Very soft background noise
    MEDIUM    // Moderate background noise
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
 * Types of soothing sounds available
 */
enum class SoundType {
    BROWN_NOISE,    // Deep, rumbling noise - very soothing for babies
    PINK_NOISE,     // Balanced noise
    SHUSH_PULSE     // Rhythmic shushing pattern modulated on noise
}

/**
 * State machine states for the intervention engine
 */
enum class SoomiState {
    IDLE,           // Not running
    BASELINE,       // Running with baseline only (if enabled), monitoring
    EARLY_SMOOTH,   // Detected mild unrest, applying gentle intervention
    CRISIS,         // Detected sudden/severe crying, stronger intervention
    COOLDOWN,       // Post-intervention waiting period
    PAUSED          // User paused (panic stop or manual)
}

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
    val manualInterventions: Int = 0,  // Soothe Now or Panic Stop
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
    val triggerState: SoomiState,  // What state it triggered (EARLY_SMOOTH or CRISIS)
    val interventionUsed: SoundType,
    val interventionLevel: InterventionLevel,
    val deltaZ: Float? = null,  // Improvement achieved (positive = better)
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
 * Configuration thresholds (adjustable in settings)
 */
data class ThresholdConfig(
    val zEarlyThreshold: Float = 15f,      // Z10/Z20 - when to start gentle intervention
    val zCrisisThreshold: Float = 80f,      // When to trigger crisis mode
    val zStopThreshold: Float = 10f,        // When to stop intervention
    val earlyConfirmationWindowMs: Long = 2000,  // How long Z must stay elevated
    val crisisRiseTimeMs: Long = 1000,      // How fast Z must rise for crisis
    val evaluationTimeMs: Long = 12000,     // Time to evaluate effectiveness
    val cooldownTimeMs: Long = 45000,       // Wait time after intervention
    val improvementThreshold: Float = 10f,  // ΔZ needed to count as effective
    val volumeCap: Float = 0.85f            // Maximum volume multiplier
)

/**
 * Telemetry event (only collected if user opts in)
 */
data class TelemetryEvent(
    val eventType: String,
    val timestamp: Instant,
    val sessionId: Long,
    val metadata: Map<String, Any> = emptyMap()
) {
    // Never include raw audio data
    companion object {
        fun stateChange(from: SoomiState, to: SoomiState, sessionId: Long) = TelemetryEvent(
            eventType = "state_change",
            timestamp = Instant.now(),
            sessionId = sessionId,
            metadata = mapOf("from" to from.name, "to" to to.name)
        )
        
        fun interventionResult(effective: Boolean, deltaZ: Float, sessionId: Long) = TelemetryEvent(
            eventType = "intervention_result",
            timestamp = Instant.now(),
            sessionId = sessionId,
            metadata = mapOf("effective" to effective, "delta_z" to deltaZ)
        )
    }
}

// ============================================================================
// Audio Output Routing Models
// ============================================================================

/**
 * Audio output device type selection
 */
enum class OutputDeviceType {
    PHONE_SPEAKER,    // Built-in phone speaker (default)
    BLUETOOTH,        // Connected Bluetooth speaker (A2DP)
    AUTO              // Automatic: prefer Bluetooth if available, else phone speaker
}

/**
 * Represents an available audio output device
 */
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

/**
 * Audio routing event for logging/debugging
 */
data class AudioRoutingEvent(
    val timestamp: Instant = Instant.now(),
    val eventType: AudioRoutingEventType,
    val fromDevice: String? = null,
    val toDevice: String? = null,
    val reason: String? = null
)

enum class AudioRoutingEventType {
    DEVICE_SELECTED,          // User or auto selected a device
    DEVICE_CONNECTED,         // New device became available
    DEVICE_DISCONNECTED,      // Device was disconnected
    FALLBACK_TRIGGERED,       // Had to fall back to another device
    AUDIO_FOCUS_LOST,         // Another app took audio focus
    AUDIO_FOCUS_GAINED,       // Regained audio focus
    OUTPUT_FAILED,            // Failed to initialize output
    OUTPUT_RECOVERED          // Recovered from failure
}

/**
 * Audio focus state
 */
enum class AudioFocusState {
    NONE,           // No focus requested
    GAINED,         // Have full audio focus
    LOST_TRANSIENT, // Lost temporarily (e.g., notification)
    LOST_PERMANENT, // Lost permanently (e.g., phone call)
    DUCKED          // Should lower volume
}
