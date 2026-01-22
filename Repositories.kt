package com.soomi.baby.data.repository

import com.soomi.baby.data.local.dao.LearningDao
import com.soomi.baby.data.local.dao.SessionDao
import com.soomi.baby.data.local.dao.SessionWithFeedback
import com.soomi.baby.data.local.entity.*
import com.soomi.baby.data.preferences.SoomiPreferences
import com.soomi.baby.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Session Repository
 * 
 * Manages session data including:
 * - Session lifecycle (start, update, end)
 * - Score samples
 * - Unrest events
 * - Morning feedback
 */
class SessionRepository(
    private val sessionDao: SessionDao,
    private val scope: CoroutineScope
) {
    
    // --- Session Management ---
    
    suspend fun startSession(baselineMode: BaselineMode): Long {
        val entity = SessionEntity(
            startTime = System.currentTimeMillis(),
            baselineMode = baselineMode.name,
            date = LocalDate.now().toString()
        )
        return sessionDao.insertSession(entity)
    }
    
    suspend fun endSession(sessionId: Long) {
        sessionDao.getSession(sessionId)?.let { session ->
            sessionDao.updateSession(
                session.copy(endTime = System.currentTimeMillis())
            )
        }
    }
    
    suspend fun updateSessionStats(
        sessionId: Long,
        unrestEvents: Int? = null,
        totalSoothingSeconds: Long? = null,
        peakUnrestScore: Float? = null,
        manualInterventions: Int? = null,
        panicStops: Int? = null
    ) {
        sessionDao.getSession(sessionId)?.let { session ->
            sessionDao.updateSession(
                session.copy(
                    unrestEvents = unrestEvents ?: session.unrestEvents,
                    totalSoothingSeconds = totalSoothingSeconds ?: session.totalSoothingSeconds,
                    peakUnrestScore = peakUnrestScore ?: session.peakUnrestScore,
                    manualInterventions = manualInterventions ?: session.manualInterventions,
                    panicStops = panicStops ?: session.panicStops
                )
            )
        }
    }
    
    suspend fun getActiveSession(): Session? {
        return sessionDao.getActiveSession()?.toModel()
    }
    
    suspend fun getSession(id: Long): Session? {
        return sessionDao.getSession(id)?.toModel()
    }
    
    fun getAllSessions(): Flow<List<Session>> {
        return sessionDao.getAllSessions().map { list ->
            list.map { it.toModel() }
        }
    }
    
    suspend fun getRecentSessions(limit: Int = 7): List<Session> {
        return sessionDao.getRecentSessions(limit).map { it.toModel() }
    }
    
    // --- Score Samples ---
    
    private var sampleBuffer = mutableListOf<ScoreSampleEntity>()
    private var lastSampleTime = 0L
    private val sampleIntervalMs = 3000L  // Downsample to every 3 seconds
    
    fun recordScoreSample(sessionId: Long, score: Float, state: SoomiState) {
        val now = System.currentTimeMillis()
        if (now - lastSampleTime >= sampleIntervalMs) {
            sampleBuffer.add(
                ScoreSampleEntity(
                    sessionId = sessionId,
                    timestamp = now,
                    score = score,
                    state = state.name
                )
            )
            lastSampleTime = now
            
            // Flush every 20 samples
            if (sampleBuffer.size >= 20) {
                flushSamples()
            }
        }
    }
    
    fun flushSamples() {
        if (sampleBuffer.isNotEmpty()) {
            val toFlush = sampleBuffer.toList()
            sampleBuffer.clear()
            scope.launch {
                sessionDao.insertScoreSamples(toFlush)
            }
        }
    }
    
    suspend fun getScoreSamples(sessionId: Long): List<ScoreSample> {
        return sessionDao.getScoreSamples(sessionId).map {
            ScoreSample(
                timestamp = it.timestamp,
                score = it.score,
                state = SoomiState.valueOf(it.state)
            )
        }
    }
    
    // --- Unrest Events ---
    
    suspend fun startUnrestEvent(
        sessionId: Long,
        peakScore: Float,
        triggerState: SoomiState,
        interventionSound: SoundType,
        interventionLevel: InterventionLevel
    ): Long {
        val entity = UnrestEventEntity(
            sessionId = sessionId,
            startTime = System.currentTimeMillis(),
            peakScore = peakScore,
            triggerState = triggerState.name,
            interventionSound = interventionSound.name,
            interventionLevel = interventionLevel.name
        )
        return sessionDao.insertUnrestEvent(entity)
    }
    
    suspend fun endUnrestEvent(
        eventId: Long,
        sessionId: Long,
        deltaZ: Float,
        wasEffective: Boolean
    ) {
        val events = sessionDao.getUnrestEvents(sessionId)
        events.find { it.id == eventId }?.let { event ->
            sessionDao.updateUnrestEvent(
                event.copy(
                    endTime = System.currentTimeMillis(),
                    deltaZ = deltaZ,
                    wasEffective = wasEffective
                )
            )
        }
    }
    
    suspend fun getUnrestEvents(sessionId: Long): List<UnrestEvent> {
        return sessionDao.getUnrestEvents(sessionId).map { it.toModel() }
    }
    
    // --- FSM Events ---
    
    fun logFsmTransition(
        sessionId: Long,
        fromState: SoomiState,
        toState: SoomiState,
        trigger: String? = null,
        scoreAtTransition: Float? = null
    ) {
        scope.launch {
            sessionDao.insertFsmEvent(
                FsmEventEntity(
                    sessionId = sessionId,
                    timestamp = System.currentTimeMillis(),
                    fromState = fromState.name,
                    toState = toState.name,
                    trigger = trigger,
                    scoreAtTransition = scoreAtTransition
                )
            )
        }
    }
    
    // --- Morning Feedback ---
    
    suspend fun saveFeedback(feedback: MorningFeedback) {
        sessionDao.insertFeedback(
            MorningFeedbackEntity(
                sessionId = feedback.sessionId,
                helpedRating = feedback.helpedRating.name,
                annoyingRating = feedback.annoyingRating.name,
                notes = feedback.notes,
                submittedAt = feedback.submittedAt.toEpochMilli()
            )
        )
    }
    
    suspend fun getFeedback(sessionId: Long): MorningFeedback? {
        return sessionDao.getFeedback(sessionId)?.toModel()
    }
    
    // --- Night Summaries ---
    
    suspend fun getNightSummaries(days: Int = 30): List<NightSummary> {
        val startDate = LocalDate.now().minusDays(days.toLong()).toString()
        val sessionsWithFeedback = sessionDao.getSessionsWithFeedback(startDate)
        
        return sessionsWithFeedback.map { swf ->
            val avgScore = sessionDao.getAverageScore(swf.id)
            NightSummary(
                date = LocalDate.parse(swf.date),
                session = swf.toSessionModel(),
                feedback = if (swf.helpedRating != null) {
                    MorningFeedback(
                        sessionId = swf.id,
                        helpedRating = HelpedRating.valueOf(swf.helpedRating),
                        annoyingRating = AnnoyingRating.valueOf(swf.annoyingRating ?: "NO")
                    )
                } else null,
                unrestEventCount = swf.unrestEvents,
                totalSoothingMinutes = (swf.totalSoothingSeconds / 60).toInt(),
                averageScore = avgScore,
                trend = ProgressTrend.STABLE  // Calculated separately
            )
        }
    }
    
    suspend fun calculateProgressTrend(): ProgressTrend {
        val recentSessions = sessionDao.getRecentSessions(7)
        if (recentSessions.size < 3) return ProgressTrend.STABLE
        
        val recentAvg = recentSessions.take(3).map { it.unrestEvents }.average()
        val olderAvg = recentSessions.takeLast(minOf(4, recentSessions.size - 3))
            .map { it.unrestEvents }.average()
        
        return when {
            recentAvg < olderAvg * 0.7 -> ProgressTrend.IMPROVING
            recentAvg > olderAvg * 1.3 -> ProgressTrend.WORSE
            else -> ProgressTrend.STABLE
        }
    }
    
    // --- Cleanup ---
    
    suspend fun deleteOldData(keepDays: Int = 90) {
        val cutoff = LocalDate.now().minusDays(keepDays.toLong()).toString()
        sessionDao.deleteOldSessions(cutoff)
    }
    
    // --- Extension Functions for Mapping ---
    
    private fun SessionEntity.toModel() = Session(
        id = id,
        startTime = Instant.ofEpochMilli(startTime),
        endTime = endTime?.let { Instant.ofEpochMilli(it) },
        baselineMode = BaselineMode.valueOf(baselineMode),
        unrestEvents = unrestEvents,
        totalSoothingTimeSeconds = totalSoothingSeconds,
        peakUnrestScore = peakUnrestScore,
        manualInterventions = manualInterventions,
        panicStops = panicStops,
        date = LocalDate.parse(date)
    )
    
    private fun SessionWithFeedback.toSessionModel() = Session(
        id = id,
        startTime = Instant.ofEpochMilli(startTime),
        endTime = endTime?.let { Instant.ofEpochMilli(it) },
        baselineMode = BaselineMode.valueOf(baselineMode),
        unrestEvents = unrestEvents,
        totalSoothingTimeSeconds = totalSoothingSeconds,
        peakUnrestScore = peakUnrestScore,
        manualInterventions = manualInterventions,
        panicStops = panicStops,
        date = LocalDate.parse(date)
    )
    
    private fun UnrestEventEntity.toModel() = UnrestEvent(
        id = id,
        sessionId = sessionId,
        startTime = Instant.ofEpochMilli(startTime),
        endTime = endTime?.let { Instant.ofEpochMilli(it) },
        peakScore = peakScore,
        triggerState = SoomiState.valueOf(triggerState),
        interventionUsed = SoundType.valueOf(interventionSound),
        interventionLevel = InterventionLevel.valueOf(interventionLevel),
        deltaZ = deltaZ,
        wasEffective = wasEffective
    )
    
    private fun MorningFeedbackEntity.toModel() = MorningFeedback(
        sessionId = sessionId,
        helpedRating = HelpedRating.valueOf(helpedRating),
        annoyingRating = AnnoyingRating.valueOf(annoyingRating),
        notes = notes,
        submittedAt = Instant.ofEpochMilli(submittedAt)
    )
}

/**
 * Settings Repository
 * 
 * Wraps DataStore preferences with domain models
 * 
 * v2.7: Added cooldownSeconds support
 */
class SettingsRepository(
    private val preferences: SoomiPreferences
) {
    val baselineMode: Flow<BaselineMode> = preferences.baselineMode
    val thresholdConfig: Flow<ThresholdConfig> = preferences.thresholdConfig
    val telemetryEnabled: Flow<Boolean> = preferences.telemetryEnabled
    val onboardingComplete: Flow<Boolean> = preferences.onboardingComplete
    val lastSessionPrompted: Flow<Long> = preferences.lastSessionPrompted
    val appLanguage: Flow<String> = preferences.appLanguage
    val audioOutputMode: Flow<String> = preferences.audioOutputMode
    val singlePhoneMode: Flow<Boolean> = preferences.singlePhoneMode
    
    // v2.6: Sound Profile
    val soundProfile: Flow<SoundProfile> = preferences.soundProfile
    
    // v2.7: Cooldown Seconds
    val cooldownSeconds: Flow<Int> = preferences.cooldownSeconds
    
    suspend fun setBaselineMode(mode: BaselineMode) = preferences.setBaselineMode(mode)
    suspend fun setZEarlyThreshold(value: Float) = preferences.setZEarlyThreshold(value)
    suspend fun setZCrisisThreshold(value: Float) = preferences.setZCrisisThreshold(value)
    suspend fun setZStopThreshold(value: Float) = preferences.setZStopThreshold(value)
    suspend fun setVolumeCap(value: Float) = preferences.setVolumeCap(value)
    suspend fun setTelemetryEnabled(enabled: Boolean) = preferences.setTelemetryEnabled(enabled)
    suspend fun setOnboardingComplete(complete: Boolean) = preferences.setOnboardingComplete(complete)
    suspend fun setLastSessionPrompted(sessionId: Long) = preferences.setLastSessionPrompted(sessionId)
    suspend fun setAppLanguage(language: String) = preferences.setAppLanguage(language)
    suspend fun setAudioOutputMode(mode: String) = preferences.setAudioOutputMode(mode)
    suspend fun setSinglePhoneMode(enabled: Boolean) = preferences.setSinglePhoneMode(enabled)
    
    // v2.6: Sound Profile
    suspend fun setSoundProfile(profile: SoundProfile) = preferences.setSoundProfile(profile)
    
    // v2.7: Cooldown Seconds
    suspend fun setCooldownSeconds(value: Int) = preferences.setCooldownSeconds(value)
}

/**
 * Learning Repository
 * 
 * Manages intervention effectiveness tracking for adaptive behavior
 */
class LearningRepository(
    private val learningDao: LearningDao,
    private val scope: CoroutineScope
) {
    
    /**
     * Record the outcome of an intervention
     */
    suspend fun recordInterventionOutcome(
        soundType: SoundType,
        baselineMode: BaselineMode,
        effective: Boolean,
        deltaZ: Float
    ) {
        val existing = learningDao.getEffectiveness(soundType.name, baselineMode.name)
        
        val updated = if (existing != null) {
            // Update running average
            val newTotal = existing.totalCount + 1
            val newSuccess = existing.successCount + if (effective) 1 else 0
            val newAvgDelta = (existing.averageDeltaZ * existing.totalCount + deltaZ) / newTotal
            
            existing.copy(
                successCount = newSuccess,
                totalCount = newTotal,
                averageDeltaZ = newAvgDelta,
                lastUsed = System.currentTimeMillis()
            )
        } else {
            InterventionEffectivenessEntity(
                soundType = soundType.name,
                baselineMode = baselineMode.name,
                successCount = if (effective) 1 else 0,
                totalCount = 1,
                averageDeltaZ = deltaZ,
                lastUsed = System.currentTimeMillis()
            )
        }
        
        learningDao.upsertEffectiveness(updated)
    }
    
    /**
     * Get the most effective sound type for a given baseline mode
     */
    suspend fun getBestSoundType(baselineMode: BaselineMode): SoundType {
        val effectiveness = learningDao.getEffectivenessByBaseline(baselineMode.name)
        
        // Epsilon-greedy: 80% best, 20% explore
        if (effectiveness.isNotEmpty() && Math.random() < 0.8) {
            val best = effectiveness.first()
            if (best.totalCount >= 3) {  // Minimum trials for confidence
                return SoundType.valueOf(best.soundType)
            }
        }
        
        // Default or explore
        return SoundType.BROWN_NOISE
    }
    
    /**
     * Get all effectiveness data for display
     */
    suspend fun getAllEffectiveness(): List<InterventionEffectiveness> {
        return learningDao.getAllEffectiveness().map {
            InterventionEffectiveness(
                soundType = SoundType.valueOf(it.soundType),
                baselineMode = BaselineMode.valueOf(it.baselineMode),
                successCount = it.successCount,
                totalCount = it.totalCount,
                averageDeltaZ = it.averageDeltaZ,
                lastUsed = Instant.ofEpochMilli(it.lastUsed)
            )
        }
    }
    
    /**
     * Reset learning data
     */
    suspend fun resetLearning() {
        learningDao.clearAllEffectiveness()
    }
}
