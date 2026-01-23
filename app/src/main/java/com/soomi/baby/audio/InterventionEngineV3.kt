package com.soomi.baby.audio

import android.util.Log
import com.soomi.baby.data.local.entity.TriggerTypes
import com.soomi.baby.data.repository.LearningRepositoryV3
import com.soomi.baby.data.repository.ProfileSelection
import com.soomi.baby.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * InterventionEngine v3.0
 * 
 * Erweiterte Finite State Machine mit:
 * - Closed-Loop Learning (deltaZ-basiert)
 * - Adaptive Profile Selection
 * - Exploration vs. Exploitation
 * - Event Tracking für Analytics
 * 
 * States:
 * - STOPPED:   Session nicht aktiv
 * - LISTENING: Überwachung, nur Baseline
 * - SOOTHING:  Intervention aktiv
 * - COOLDOWN:  Baby ruhig, Sound fadet aus
 * 
 * v3.0 Änderungen:
 * - Integration mit LearningRepositoryV3
 * - Event Tracking mit start/end
 * - Profile Selection vor Intervention
 * - Z-Wert Tracking während Intervention
 */
class InterventionEngineV3(
    private val audioOutput: AudioOutputEngine,
    private val learningRepository: LearningRepositoryV3,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "InterventionEngineV3"
    }
    
    // Configuration
    var config = InterventionConfig()
    
    // Current state
    private val _state = MutableStateFlow(SoomiState.STOPPED)
    val state: StateFlow<SoomiState> = _state.asStateFlow()
    
    private val _currentLevel = MutableStateFlow(InterventionLevel.OFF)
    val currentLevel: StateFlow<InterventionLevel> = _currentLevel.asStateFlow()
    
    private val _currentSound = MutableStateFlow(SoundType.BROWN_NOISE)
    val currentSound: StateFlow<SoundType> = _currentSound.asStateFlow()
    
    private val _cooldownRemaining = MutableStateFlow(0)
    val cooldownRemaining: StateFlow<Int> = _cooldownRemaining.asStateFlow()
    
    // v3.0: Current intervention tracking
    private val _currentEventId = MutableStateFlow<Long?>(null)
    val currentEventId: StateFlow<Long?> = _currentEventId.asStateFlow()
    
    private val _isExploring = MutableStateFlow(false)
    val isExploring: StateFlow<Boolean> = _isExploring.asStateFlow()
    
    // Baseline mode
    private var baselineMode = BaselineMode.OFF
    
    // Session tracking
    private var currentSessionId: Long = 0
    
    // Timing tracking
    private var stateEntryTime = 0L
    private var soothingStartTime = 0L
    private var lastScoreTime = 0L
    
    // v3.0: Z-Wert Tracking für deltaZ
    private var interventionStartZ = 0f
    private var interventionPeakZ = 0f
    private var lastKnownZ = 0f
    
    // Confirmation tracking
    private var aboveStartThresholdSince: Long? = null
    private var belowCalmThresholdSince: Long? = null
    private var aboveRetriggerThresholdSince: Long? = null
    
    // Escalation tracking
    private var escalationCount = 0
    
    // Fadeout tracking
    private var cooldownStartVolume = 0f
    private var cooldownTotalSeconds = 0
    
    // =========================================================================
    // Public API
    // =========================================================================
    
    /**
     * Setzt die aktuelle Session ID (wird vom Service aufgerufen)
     */
    fun setSessionId(sessionId: Long) {
        currentSessionId = sessionId
        Log.d(TAG, "Session ID set to $sessionId")
    }
    
    /**
     * Setzt den Baseline Mode
     */
    fun setBaselineMode(mode: BaselineMode) {
        baselineMode = mode
        if (_state.value == SoomiState.LISTENING) {
            applyBaselineOutput()
        }
    }
    
    /**
     * Startet die Engine - wechselt zu LISTENING
     */
    fun start() {
        if (_state.value != SoomiState.STOPPED) return
        
        Log.d(TAG, "Starting engine v3.0")
        audioOutput.initialize()
        
        // v3.0: Bestes Profil für Sound-Typ holen (für Baseline)
        scope.launch {
            val profile = learningRepository.selectBestProfile(baselineMode)
            _currentSound.value = profile.soundType
            Log.d(TAG, "Selected initial sound: ${profile.soundType}")
        }
        
        transitionTo(SoomiState.LISTENING)
        applyBaselineOutput()
        
        if (baselineMode != BaselineMode.OFF) {
            audioOutput.start()
        }
    }
    
    /**
     * Stoppt die Engine
     */
    fun stop() {
        Log.d(TAG, "Stopping engine")
        
        // Falls eine Intervention läuft, beenden
        scope.launch {
            endCurrentInterventionEvent()
        }
        
        audioOutput.stop()
        transitionTo(SoomiState.STOPPED)
        resetTracking()
        _cooldownRemaining.value = 0
    }
    
    /**
     * Panic Stop - sofortige Stille
     */
    fun panicStop() {
        Log.d(TAG, "Panic stop!")
        
        scope.launch {
            endCurrentInterventionEvent()
        }
        
        audioOutput.forceStop()
        transitionTo(SoomiState.LISTENING)
        resetTracking()
        _cooldownRemaining.value = 0
        
        if (baselineMode != BaselineMode.OFF) {
            applyBaselineOutput()
            audioOutput.start()
        }
    }
    
    /**
     * Verarbeitet einen neuen Unruhe-Score - Haupt-FSM Update
     * Wird alle ~500ms aufgerufen
     */
    fun processScore(score: UnrestScore) {
        val currentTime = System.currentTimeMillis()
        lastScoreTime = currentTime
        lastKnownZ = score.value
        
        // v3.0: Peak tracking während Intervention
        if (_state.value == SoomiState.SOOTHING && score.value > interventionPeakZ) {
            interventionPeakZ = score.value
            // Update peak in database
            _currentEventId.value?.let { eventId ->
                scope.launch {
                    learningRepository.updateEventPeak(eventId, score.value)
                }
            }
        }
        
        when (_state.value) {
            SoomiState.STOPPED, SoomiState.IDLE -> {
                // Nichts tun
            }
            SoomiState.LISTENING, SoomiState.BASELINE -> {
                handleListeningState(score, currentTime)
            }
            SoomiState.SOOTHING -> {
                handleSoothingState(score, currentTime)
            }
            SoomiState.COOLDOWN -> {
                handleCooldownState(score, currentTime)
            }
            else -> {
                // Future states (PREDICTIVE) hier behandeln
            }
        }
    }
    
    /**
     * Cooldown Timer Update - jede Sekunde aufrufen
     */
    fun updateCooldownTimer() {
        if (_state.value == SoomiState.COOLDOWN && _cooldownRemaining.value > 0) {
            _cooldownRemaining.value = _cooldownRemaining.value - 1
            
            // Fadeout berechnen
            if (cooldownTotalSeconds > 0) {
                val progress = _cooldownRemaining.value.toFloat() / cooldownTotalSeconds.toFloat()
                val fadeoutVolume = cooldownStartVolume * progress
                
                val baselineVolume = when (baselineMode) {
                    BaselineMode.OFF -> 0f
                    BaselineMode.GENTLE -> InterventionLevel.LEVEL_1.volumeMultiplier
                    BaselineMode.MEDIUM -> InterventionLevel.LEVEL_2.volumeMultiplier
                }
                val finalVolume = maxOf(fadeoutVolume, baselineVolume)
                audioOutput.setVolumeRaw(finalVolume)
                
                Log.d(TAG, "Cooldown fadeout: ${_cooldownRemaining.value}s, volume: ${(finalVolume * 100).toInt()}%")
            }
            
            if (_cooldownRemaining.value <= 0) {
                endIntervention()
            }
        }
    }
    
    /**
     * Manuelle Beruhigung auslösen
     */
    fun manualSoothe() {
        if (_state.value == SoomiState.STOPPED) return
        
        Log.d(TAG, "Manual soothe triggered")
        startIntervention(lastKnownZ, TriggerTypes.MANUAL)
    }
    
    // =========================================================================
    // State Handlers
    // =========================================================================
    
    private fun handleListeningState(score: UnrestScore, currentTime: Long) {
        if (score.value >= config.startThreshold) {
            if (aboveStartThresholdSince == null) {
                aboveStartThresholdSince = currentTime
                Log.d(TAG, "Unrest above threshold, starting confirm timer")
            }
            
            val confirmDuration = currentTime - (aboveStartThresholdSince ?: currentTime)
            if (confirmDuration >= config.startConfirmSec * 1000) {
                Log.d(TAG, "Threshold confirmed, starting intervention")
                startIntervention(score.value, TriggerTypes.AUTO)
            }
        } else {
            aboveStartThresholdSince = null
        }
    }
    
    private fun handleSoothingState(score: UnrestScore, currentTime: Long) {
        val soothingDuration = currentTime - soothingStartTime
        if (soothingDuration < config.minSoothingSec * 1000) {
            return
        }
        
        if (score.value <= config.calmThreshold) {
            if (belowCalmThresholdSince == null) {
                belowCalmThresholdSince = currentTime
                Log.d(TAG, "Unrest below calm threshold, starting confirm timer")
            }
            
            val confirmDuration = currentTime - (belowCalmThresholdSince ?: currentTime)
            if (confirmDuration >= config.calmConfirmSec * 1000) {
                Log.d(TAG, "Calm confirmed, starting cooldown")
                startCooldown()
            }
        } else {
            belowCalmThresholdSince = null
        }
    }
    
    private fun handleCooldownState(score: UnrestScore, currentTime: Long) {
        if (score.value >= config.retriggerThreshold) {
            if (aboveRetriggerThresholdSince == null) {
                aboveRetriggerThresholdSince = currentTime
                Log.d(TAG, "Retrigger threshold reached during cooldown")
            }
            
            val confirmDuration = currentTime - (aboveRetriggerThresholdSince ?: currentTime)
            if (confirmDuration >= config.retriggerConfirmSec * 1000) {
                Log.d(TAG, "Retrigger confirmed")
                cancelCooldownAndRetrigger()
            }
        } else {
            aboveRetriggerThresholdSince = null
        }
    }
    
    // =========================================================================
    // State Transitions
    // =========================================================================
    
    private fun startIntervention(triggerScore: Float, triggerType: String) {
        transitionTo(SoomiState.SOOTHING)
        soothingStartTime = System.currentTimeMillis()
        
        // v3.0: Z-Wert Tracking initialisieren
        interventionStartZ = triggerScore
        interventionPeakZ = triggerScore
        
        // Reset timers
        aboveStartThresholdSince = null
        belowCalmThresholdSince = null
        aboveRetriggerThresholdSince = null
        escalationCount = 0
        
        // v3.0: Profil auswählen via Learning
        scope.launch {
            val profile = learningRepository.selectBestProfile(baselineMode)
            _currentSound.value = profile.soundType
            _currentLevel.value = profile.level
            _isExploring.value = profile.isExploration
            
            // Audio starten
            audioOutput.setSoundType(profile.soundType)
            audioOutput.setVolume(profile.level)
            
            if (!audioOutput.isPlaying()) {
                audioOutput.start()
            }
            
            // Event in DB starten
            val eventId = learningRepository.startInterventionEvent(
                sessionId = currentSessionId,
                zStart = triggerScore,
                soundType = profile.soundType,
                level = profile.level,
                baselineMode = baselineMode,
                triggerType = triggerType,
                wasExploration = profile.isExploration
            )
            _currentEventId.value = eventId
            
            Log.d(TAG, "Intervention started: event=$eventId, sound=${profile.soundType}, " +
                       "level=${profile.level}, exploration=${profile.isExploration}")
        }
    }
    
    private fun startCooldown() {
        transitionTo(SoomiState.COOLDOWN)
        
        cooldownStartVolume = _currentLevel.value.volumeMultiplier
        cooldownTotalSeconds = config.cooldownSec
        _cooldownRemaining.value = config.cooldownSec
        
        belowCalmThresholdSince = null
        aboveRetriggerThresholdSince = null
        
        Log.d(TAG, "Cooldown started: ${config.cooldownSec}s")
    }
    
    private fun cancelCooldownAndRetrigger() {
        // v3.0: Event beenden bevor neues startet
        scope.launch {
            endCurrentInterventionEvent()
        }
        
        transitionTo(SoomiState.SOOTHING)
        _cooldownRemaining.value = 0
        
        aboveRetriggerThresholdSince = null
        belowCalmThresholdSince = null
        
        // Neues Event starten
        startIntervention(lastKnownZ, TriggerTypes.RETRIGGER)
    }
    
    private fun endIntervention() {
        Log.d(TAG, "Intervention ended")
        
        // v3.0: Event in DB beenden
        scope.launch {
            endCurrentInterventionEvent()
        }
        
        transitionTo(SoomiState.LISTENING)
        _cooldownRemaining.value = 0
        resetTracking()
        
        applyBaselineOutput()
        
        if (baselineMode == BaselineMode.OFF) {
            audioOutput.stop()
        }
    }
    
    /**
     * v3.0: Beendet das aktuelle Event in der Datenbank
     */
    private suspend fun endCurrentInterventionEvent() {
        _currentEventId.value?.let { eventId ->
            learningRepository.endInterventionEvent(
                eventId = eventId,
                zEnd = lastKnownZ
            )
            _currentEventId.value = null
            _isExploring.value = false
            Log.d(TAG, "Ended event $eventId with zEnd=$lastKnownZ")
        }
    }
    
    // =========================================================================
    // Helpers
    // =========================================================================
    
    private fun applyBaselineOutput() {
        val level = when (baselineMode) {
            BaselineMode.OFF -> InterventionLevel.OFF
            BaselineMode.GENTLE -> InterventionLevel.LEVEL_1
            BaselineMode.MEDIUM -> InterventionLevel.LEVEL_2
        }
        _currentLevel.value = level
        audioOutput.setVolume(level)
    }
    
    private fun transitionTo(newState: SoomiState) {
        val oldState = _state.value
        _state.value = newState
        stateEntryTime = System.currentTimeMillis()
        Log.d(TAG, "State: $oldState -> $newState")
    }
    
    private fun resetTracking() {
        aboveStartThresholdSince = null
        belowCalmThresholdSince = null
        aboveRetriggerThresholdSince = null
        escalationCount = 0
        interventionStartZ = 0f
        interventionPeakZ = 0f
        cooldownStartVolume = 0f
        cooldownTotalSeconds = 0
    }
}
