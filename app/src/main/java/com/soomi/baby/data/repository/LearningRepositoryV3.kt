package com.soomi.baby.data.repository

import android.util.Log
import com.soomi.baby.data.local.dao.LearningDaoV3
import com.soomi.baby.data.local.entity.*
import com.soomi.baby.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Calendar

/**
 * SOOMI v3.0 - Learning Repository
 * 
 * Verwaltet das adaptive Learning System mit:
 * - InterventionEvent Tracking (deltaZ-basiert)
 * - SoundProfileScore Management
 * - Exploration vs. Exploitation
 * - Context-aware Profile Selection
 */
class LearningRepositoryV3(
    private val learningDao: LearningDaoV3,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "LearningRepoV3"
        
        // Effectiveness Thresholds
        const val DELTA_Z_EFFECTIVE = 15f           // deltaZ > 15 = effektiv
        const val DELTA_Z_HIGHLY_EFFECTIVE = 30f    // deltaZ > 30 = sehr effektiv
        const val HIGHLY_EFFECTIVE_MAX_DURATION = 60 // in < 60s
        
        // Score Update Weights
        const val SCORE_DECAY = 0.8f                // Alte Scores werden gewichtet
        const val SCORE_NEW_WEIGHT = 0.2f           // Neue Scores werden gewichtet
        
        // Exploration Rate
        const val EXPLORATION_RATE = 0.2f           // 20% Exploration
        const val MIN_USAGE_FOR_EXPLOITATION = 5    // Mindestens 5 Nutzungen
    }
    
    // =========================================================================
    // Event Tracking
    // =========================================================================
    
    /**
     * Startet ein neues Interventions-Event und gibt die ID zurück.
     * Wird aufgerufen wenn Intervention beginnt (LISTENING → SOOTHING).
     */
    suspend fun startInterventionEvent(
        sessionId: Long,
        zStart: Float,
        soundType: SoundType,
        level: InterventionLevel,
        baselineMode: BaselineMode,
        triggerType: String = TriggerTypes.AUTO,
        dZdtAtTrigger: Float? = null,
        wasExploration: Boolean = false
    ): Long {
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val contextTag = ContextTags.fromHourOfDay(hour)
        
        // Zähle vorherige Events in dieser Session
        val eventIndex = learningDao.getEventCountForSession(sessionId) + 1
        
        val event = InterventionEventEntity(
            sessionId = sessionId,
            timestamp = now,
            zStart = zStart,
            zEnd = zStart,  // Wird später aktualisiert
            zPeak = zStart,
            deltaZ = 0f,
            durationSec = 0,
            effectivenessScore = 0f,
            soundType = soundType.name,
            levelUsed = level.name,
            baselineMode = baselineMode.name,
            contextTag = contextTag,
            hourOfDay = hour,
            eventIndexInSession = eventIndex,
            wasExploration = wasExploration,
            wasEffective = false,
            wasHighlyEffective = false,
            triggerType = triggerType,
            dZdtAtTrigger = dZdtAtTrigger
        )
        
        val eventId = learningDao.insertInterventionEvent(event)
        Log.d(TAG, "Started intervention event #$eventId: zStart=$zStart, sound=$soundType, context=$contextTag")
        
        return eventId
    }
    
    /**
     * Aktualisiert den Peak Z-Wert während der Intervention.
     * Wird regelmäßig aufgerufen während SOOTHING aktiv ist.
     */
    suspend fun updateEventPeak(eventId: Long, currentZ: Float) {
        learningDao.getInterventionEvent(eventId)?.let { event ->
            if (currentZ > event.zPeak) {
                learningDao.updateInterventionEvent(event.copy(zPeak = currentZ))
            }
        }
    }
    
    /**
     * Beendet ein Interventions-Event und berechnet alle Metriken.
     * Wird aufgerufen wenn Intervention endet (COOLDOWN → LISTENING).
     */
    suspend fun endInterventionEvent(
        eventId: Long,
        zEnd: Float
    ) {
        val event = learningDao.getInterventionEvent(eventId) ?: return
        
        val now = System.currentTimeMillis()
        val durationSec = ((now - event.timestamp) / 1000).toInt()
        val deltaZ = event.zStart - zEnd
        
        // Effectiveness Score berechnen (deltaZ pro Minute, normalisiert)
        val effectivenessScore = if (durationSec > 0) {
            (deltaZ / durationSec.toFloat()) * 60f  // Pro Minute normalisiert
        } else {
            0f
        }
        
        // Effectiveness Flags
        val wasEffective = deltaZ >= DELTA_Z_EFFECTIVE
        val wasHighlyEffective = deltaZ >= DELTA_Z_HIGHLY_EFFECTIVE && 
                                  durationSec <= HIGHLY_EFFECTIVE_MAX_DURATION
        
        val updatedEvent = event.copy(
            zEnd = zEnd,
            deltaZ = deltaZ,
            durationSec = durationSec,
            effectivenessScore = effectivenessScore,
            wasEffective = wasEffective,
            wasHighlyEffective = wasHighlyEffective
        )
        
        learningDao.updateInterventionEvent(updatedEvent)
        
        Log.d(TAG, "Ended intervention event #$eventId: deltaZ=$deltaZ, duration=${durationSec}s, " +
                   "effective=$wasEffective, highlyEffective=$wasHighlyEffective")
        
        // Profile Score aktualisieren
        updateProfileScore(updatedEvent)
    }
    
    // =========================================================================
    // Profile Score Management
    // =========================================================================
    
    /**
     * Aktualisiert den Score für das verwendete Profil basierend auf dem Event-Outcome.
     */
    private suspend fun updateProfileScore(event: InterventionEventEntity) {
        val existing = learningDao.getProfileScore(
            soundType = event.soundType,
            startLevel = event.levelUsed,
            baselineMode = event.baselineMode,
            contextTag = event.contextTag
        )
        
        val updatedScore = if (existing != null) {
            // Exponential Moving Average für Score
            val newEffectivenessScore = existing.effectivenessScore * SCORE_DECAY + 
                                         event.effectivenessScore * SCORE_NEW_WEIGHT
            
            // Running Averages
            val newCount = existing.usageCount + 1
            val newAvgDeltaZ = (existing.avgDeltaZ * existing.usageCount + event.deltaZ) / newCount
            val newAvgDuration = (existing.avgDurationSec * existing.usageCount + event.durationSec) / newCount
            
            existing.copy(
                effectivenessScore = newEffectivenessScore,
                usageCount = newCount,
                successCount = existing.successCount + if (event.wasEffective) 1 else 0,
                highSuccessCount = existing.highSuccessCount + if (event.wasHighlyEffective) 1 else 0,
                avgDeltaZ = newAvgDeltaZ,
                avgDurationSec = newAvgDuration,
                lastUpdated = System.currentTimeMillis(),
                lastUsed = System.currentTimeMillis()
            )
        } else {
            // Neues Profil erstellen
            SoundProfileScoreEntity(
                soundType = event.soundType,
                startLevel = event.levelUsed,
                baselineMode = event.baselineMode,
                contextTag = event.contextTag,
                effectivenessScore = event.effectivenessScore,
                usageCount = 1,
                successCount = if (event.wasEffective) 1 else 0,
                highSuccessCount = if (event.wasHighlyEffective) 1 else 0,
                avgDeltaZ = event.deltaZ,
                avgDurationSec = event.durationSec.toFloat(),
                lastUpdated = System.currentTimeMillis(),
                lastUsed = System.currentTimeMillis()
            )
        }
        
        learningDao.upsertProfileScore(updatedScore)
        
        Log.d(TAG, "Updated profile score: ${event.soundType}/${event.levelUsed}/${event.contextTag} " +
                   "-> score=${updatedScore.effectivenessScore}, count=${updatedScore.usageCount}")
    }
    
    // =========================================================================
    // Profile Selection (Exploration vs. Exploitation)
    // =========================================================================
    
    /**
     * Wählt das beste Profil für den aktuellen Kontext.
     * Implementiert Epsilon-Greedy: 80% Exploitation, 20% Exploration
     * 
     * @return Pair<SoundType, InterventionLevel> und ob es Exploration war
     */
    suspend fun selectBestProfile(
        baselineMode: BaselineMode,
        contextTag: String = getCurrentContextTag()
    ): ProfileSelection {
        
        // Entscheide: Exploration oder Exploitation?
        val shouldExplore = Math.random() < EXPLORATION_RATE
        
        if (shouldExplore) {
            // Exploration: Wähle ein wenig genutztes Profil
            val explorationProfile = learningDao.getExplorationProfileForContext(
                contextTag = contextTag,
                minUsage = MIN_USAGE_FOR_EXPLOITATION
            ) ?: learningDao.getExplorationProfile(MIN_USAGE_FOR_EXPLOITATION)
            
            if (explorationProfile != null) {
                Log.d(TAG, "Exploration: selecting ${explorationProfile.soundType}/${explorationProfile.startLevel}")
                return ProfileSelection(
                    soundType = SoundType.valueOf(explorationProfile.soundType),
                    level = InterventionLevel.valueOf(explorationProfile.startLevel),
                    isExploration = true,
                    confidence = 0f
                )
            }
        }
        
        // Exploitation: Wähle das beste bekannte Profil
        val bestProfile = learningDao.getBestProfileForContextAndBaseline(
            contextTag = contextTag,
            baselineMode = baselineMode.name
        ) ?: learningDao.getBestProfileForContext(contextTag)
        
        if (bestProfile != null && bestProfile.usageCount >= MIN_USAGE_FOR_EXPLOITATION) {
            val confidence = minOf(1f, bestProfile.usageCount / 20f)  // Max confidence bei 20+ Nutzungen
            Log.d(TAG, "Exploitation: selecting ${bestProfile.soundType}/${bestProfile.startLevel} " +
                       "with score=${bestProfile.effectivenessScore}, confidence=$confidence")
            return ProfileSelection(
                soundType = SoundType.valueOf(bestProfile.soundType),
                level = InterventionLevel.valueOf(bestProfile.startLevel),
                isExploration = false,
                confidence = confidence
            )
        }
        
        // Fallback: Default Profil
        Log.d(TAG, "No profile data, using defaults")
        return ProfileSelection(
            soundType = SoundType.BROWN_NOISE,
            level = InterventionLevel.LEVEL_2,
            isExploration = true,
            confidence = 0f
        )
    }
    
    /**
     * Bestimmt den aktuellen Context-Tag basierend auf Uhrzeit
     */
    fun getCurrentContextTag(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return ContextTags.fromHourOfDay(hour)
    }
    
    /**
     * Prüft ob HIGH_ACTIVITY Context vorliegt (3+ Events in 2 Stunden)
     */
    suspend fun checkHighActivityContext(): Boolean {
        val twoHoursAgo = System.currentTimeMillis() - (2 * 60 * 60 * 1000)
        val recentEvents = learningDao.getEventCountSince(twoHoursAgo)
        return recentEvents >= 3
    }
    
    // =========================================================================
    // Statistics & Analytics
    // =========================================================================
    
    /**
     * Holt alle Profil-Scores als Flow für die UI
     */
    fun observeProfileScores(): Flow<List<ProfileScoreInfo>> {
        return learningDao.observeAllProfileScores().map { entities ->
            entities.map { it.toProfileScoreInfo() }
        }
    }
    
    /**
     * Holt die KPIs der letzten 7 Tage
     */
    suspend fun getWeeklyKPIs(): LearningKPIs {
        val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        
        val avgDeltaZ = learningDao.getAvgDeltaZByContext(getCurrentContextTag(), weekAgo) ?: 0f
        val avgTimeToCalm = learningDao.getAvgTimeToCalm(weekAgo) ?: 0f
        val successfulCount = learningDao.getSuccessfulInterventionCount(weekAgo)
        val highlyEffectiveCount = learningDao.getHighlyEffectiveInterventionCount(weekAgo)
        val predictiveRate = learningDao.getPredictiveInterventionRate(weekAgo) ?: 0f
        val totalEvents = learningDao.getEventCountSince(weekAgo)
        
        return LearningKPIs(
            avgDeltaZ = avgDeltaZ,
            avgTimeToCalm = avgTimeToCalm,
            successRate = if (totalEvents > 0) successfulCount.toFloat() / totalEvents else 0f,
            highSuccessRate = if (totalEvents > 0) highlyEffectiveCount.toFloat() / totalEvents else 0f,
            predictiveRate = predictiveRate,
            totalInterventions = totalEvents
        )
    }
    
    /**
     * Holt Events für eine Session
     */
    suspend fun getEventsForSession(sessionId: Long): List<InterventionEventInfo> {
        return learningDao.getInterventionEventsForSession(sessionId).map { it.toEventInfo() }
    }
    
    // =========================================================================
    // Cleanup
    // =========================================================================
    
    /**
     * Löscht alte Daten (älter als 90 Tage)
     */
    suspend fun cleanupOldData(keepDays: Int = 90) {
        val cutoff = System.currentTimeMillis() - (keepDays * 24 * 60 * 60 * 1000L)
        learningDao.deleteOldInterventionEvents(cutoff)
        learningDao.deleteUnusedProfiles(cutoff)
        Log.d(TAG, "Cleaned up data older than $keepDays days")
    }
    
    /**
     * Setzt alle Learning-Daten zurück
     */
    suspend fun resetAllLearning() {
        learningDao.clearAllInterventionEvents()
        learningDao.clearAllProfileScores()
        Log.d(TAG, "Reset all learning data")
    }
    
    // =========================================================================
    // Extension Functions
    // =========================================================================
    
    private fun SoundProfileScoreEntity.toProfileScoreInfo() = ProfileScoreInfo(
        soundType = SoundType.valueOf(soundType),
        level = InterventionLevel.valueOf(startLevel),
        baselineMode = BaselineMode.valueOf(baselineMode),
        contextTag = contextTag,
        effectivenessScore = effectivenessScore,
        usageCount = usageCount,
        successRate = if (usageCount > 0) successCount.toFloat() / usageCount else 0f,
        avgDeltaZ = avgDeltaZ,
        avgDurationSec = avgDurationSec
    )
    
    private fun InterventionEventEntity.toEventInfo() = InterventionEventInfo(
        id = id,
        timestamp = Instant.ofEpochMilli(timestamp),
        zStart = zStart,
        zEnd = zEnd,
        deltaZ = deltaZ,
        durationSec = durationSec,
        soundType = SoundType.valueOf(soundType),
        level = InterventionLevel.valueOf(levelUsed),
        wasEffective = wasEffective,
        wasExploration = wasExploration,
        triggerType = triggerType
    )
}

// =========================================================================
// Data Classes für UI
// =========================================================================

/**
 * Ergebnis der Profilauswahl
 */
data class ProfileSelection(
    val soundType: SoundType,
    val level: InterventionLevel,
    val isExploration: Boolean,
    val confidence: Float  // 0-1, wie sicher sind wir dass das gut ist
)

/**
 * Profil-Score Info für UI
 */
data class ProfileScoreInfo(
    val soundType: SoundType,
    val level: InterventionLevel,
    val baselineMode: BaselineMode,
    val contextTag: String,
    val effectivenessScore: Float,
    val usageCount: Int,
    val successRate: Float,
    val avgDeltaZ: Float,
    val avgDurationSec: Float
)

/**
 * Einzelnes Event Info für UI
 */
data class InterventionEventInfo(
    val id: Long,
    val timestamp: Instant,
    val zStart: Float,
    val zEnd: Float,
    val deltaZ: Float,
    val durationSec: Int,
    val soundType: SoundType,
    val level: InterventionLevel,
    val wasEffective: Boolean,
    val wasExploration: Boolean,
    val triggerType: String
)

/**
 * KPIs für die letzte Woche
 */
data class LearningKPIs(
    val avgDeltaZ: Float,
    val avgTimeToCalm: Float,
    val successRate: Float,
    val highSuccessRate: Float,
    val predictiveRate: Float,
    val totalInterventions: Int
)
