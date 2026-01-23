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
 * InterventionEngine v3.0 - Phase 2
 * 
 * Vollständige FSM mit PREDICTIVE State:
 * 
 * States:
 * - STOPPED:    Session nicht aktiv
 * - LISTENING:  Überwachung, nur Baseline
 * - PREDICTIVE: Soft-Start durch Trend-Erkennung (NEU)
 * - SOOTHING:   Volle Intervention aktiv
 * - COOLDOWN:   Baby ruhig, Sound fadet aus
 * 
 * Transitions:
 * - LISTENING → PREDICTIVE:  dZ/dt > 5 UND Z < 50
 * - LISTENING → SOOTHING:    Z ≥ 50 (direkt, ohne Predictive)
 * - PREDICTIVE → SOOTHING:   Z ≥ 35 (eskaliert)
 * - PREDICTIVE → LISTENING:  dZ/dt < 1 (Trend gestoppt)
 * - SOOTHING → COOLDOWN:     Z ≤ 25 für 5s
 * - COOLDOWN → LISTENING:    Timer = 0
 * - COOLDOWN → SOOTHING:     Z ≥ 60 (Retrigger)
 */
class InterventionEngineV3Full(
    private val audioOutput: AudioOutputEngine,
    private val learningRepository: LearningRepositoryV3,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "InterventionEngineV3"
    }
    
    // Configuration
    var config = InterventionConfig()
    
    // Gradient Calculator für dZ/dt
    private val gradientCalculator = GradientCalculator(
        windowSize = 5,
        measurementIntervalMs = 500
    )
    
    // =========================================================================
    // State Flows
    // =========================================================================
    
    private val _state = MutableStateFlow(SoomiState.STOPPED)
    val state: StateFlow<SoomiState> = _state.asStateFlow()
    
    private val _currentLevel = MutableStateFlow(InterventionLevel.OFF)
    val currentLevel: StateFlow<InterventionLevel> = _currentLevel.asStateFlow()
    
    private val _currentSound = MutableStateFlow(SoundType.BROWN_NOISE)
    val currentSound: StateFlow<SoundType> = _currentSound.asStateFlow()
    
    private val _cooldownRemaining = MutableStateFlow(0)
    val cooldownRemaining: StateFlow<Int> = _cooldownRemaining.asStateFlow()
    
    // v3.0 Phase 2: Gradient & Trend Flows
    val currentGradient: StateFlow<Float> = gradientCalculator.smoothedGradient
    val currentTrend: StateFlow<Trend> = gradientCalculator.trend
    
    // v3.0: Event Tracking
    private val _currentEventId = MutableStateFlow<Long?>(null)
    val currentEventId: StateFlow<Long?> = _currentEventId.asStateFlow()
    
    private val _isExploring = MutableStateFlow(false)
    val isExploring: StateFlow<Boolean> = _isExploring.asStateFlow()
    
    private val _isPredictive = MutableStateFlow(false)
    val isPredictive: StateFlow<Boolean> = _isPredictive.asStateFlow()
    
    // =========================================================================
    // Internal State
    // =========================================================================
    
    private var baselineMode = BaselineMode.OFF
    private var currentSessionId: Long = 0
    
    // Timing
    private var stateEntryTime = 0L
    private var soothingStartTime = 0L
    private var predictiveStartTime = 0L
    private var lastScoreTime = 0L
    
    // Z-Wert Tracking
    private var interventionStartZ = 0f
    private var interventionPeakZ = 0f
    private var lastKnownZ = 0f
    
    // Confirmation Timers
    private var aboveStartThresholdSince: Long? = null
    private var belowCalmThresholdSince: Long? = null
    private var aboveRetriggerThresholdSince: Long? = null
    private var abovePredictiveThresholdSince: Long? = null
    
    // Fadeout
    private var cooldownStartVolume = 0f
    private var cooldownTotalSeconds = 0
    
    // =========================================================================
    // Public API
    // =========================================================================
    
    fun setSessionId(sessionId: Long) {
        currentSessionId = sessionId
        Log.d(TAG, "Session ID: $sessionId")
    }
    
    fun setBaselineMode(mode: BaselineMode) {
        baselineMode = mode
        if (_state.value == SoomiState.LISTENING) {
            applyBaselineOutput()
        }
    }
    
    fun start() {
        if (_state.value != SoomiState.STOPPED) return
        
        Log.d(TAG, "Starting engine v3.0 with Predictive")
        audioOutput.initialize()
        gradientCalculator.reset()
        
        scope.launch {
            val profile = learningRepository.selectBestProfile(baselineMode)
            _currentSound.value = profile.soundType
        }
        
        transitionTo(SoomiState.LISTENING)
        applyBaselineOutput()
        
        if (baselineMode != BaselineMode.OFF) {
            audioOutput.start()
        }
    }
    
    fun stop() {
        Log.d(TAG, "Stopping engine")
        
        scope.launch {
            endCurrentInterventionEvent()
        }
        
        audioOutput.stop()
        gradientCalculator.reset()
        transitionTo(SoomiState.STOPPED)
        resetTracking()
        _cooldownRemaining.value = 0
    }
    
    fun panicStop() {
        Log.d(TAG, "Panic stop!")
        
        scope.launch {
            endCurrentInterventionEvent()
        }
        
        audioOutput.forceStop()
        gradientCalculator.reset()
        transitionTo(SoomiState.LISTENING)
        resetTracking()
        _cooldownRemaining.value = 0
        
        if (baselineMode != BaselineMode.OFF) {
            applyBaselineOutput()
            audioOutput.start()
        }
    }
    
    /**
     * Hauptmethode: Verarbeitet neuen Z-Wert
     * Wird alle ~500ms aufgerufen
     */
    fun processScore(score: UnrestScore) {
        val currentTime = System.currentTimeMillis()
        lastScoreTime = currentTime
        lastKnownZ = score.value
        
        // Gradient berechnen
        gradientCalculator.addMeasurement(score.value, currentTime)
        
        // Peak Tracking während Intervention
        if ((_state.value == SoomiState.SOOTHING || _state.value == SoomiState.PREDICTIVE) 
            && score.value > interventionPeakZ) {
            interventionPeakZ = score.value
            _currentEventId.value?.let { eventId ->
                scope.launch {
                    learningRepository.updateEventPeak(eventId, score.value)
                }
            }
        }
        
        // State-spezifische Verarbeitung
        when (_state.value) {
            SoomiState.STOPPED, SoomiState.IDLE -> {
                // Nichts tun
            }
            SoomiState.LISTENING, SoomiState.BASELINE -> {
                handleListeningState(score, currentTime)
            }
            SoomiState.PREDICTIVE -> {
                handlePredictiveState(score, currentTime)
            }
            SoomiState.SOOTHING -> {
                handleSoothingState(score, currentTime)
            }
            SoomiState.COOLDOWN -> {
                handleCooldownState(score, currentTime)
            }
        }
    }
    
    fun updateCooldownTimer() {
        if (_state.value == SoomiState.COOLDOWN && _cooldownRemaining.value > 0) {
            _cooldownRemaining.value = _cooldownRemaining.value - 1
            
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
            }
            
            if (_cooldownRemaining.value <= 0) {
                endIntervention()
            }
        }
    }
    
    fun manualSoothe() {
        if (_state.value == SoomiState.STOPPED) return
        Log.d(TAG, "Manual soothe")
        startFullIntervention(lastKnownZ, TriggerTypes.MANUAL)
    }
    
    // =========================================================================
    // State Handlers
    // =========================================================================
    
    private fun handleListeningState(score: UnrestScore, currentTime: Long) {
        val gradient = gradientCalculator.smoothedGradient.value
        
        // Option 1: Direkt zu SOOTHING wenn Z sehr hoch
        if (score.value >= config.startThreshold) {
            if (aboveStartThresholdSince == null) {
                aboveStartThresholdSince = currentTime
            }
            
            val confirmDuration = currentTime - (aboveStartThresholdSince ?: currentTime)
            if (confirmDuration >= config.startConfirmSec * 1000) {
                Log.d(TAG, "Z=${score.value} ≥ ${config.startThreshold}, direct SOOTHING")
                startFullIntervention(score.value, TriggerTypes.AUTO)
                return
            }
        } else {
            aboveStartThresholdSince = null
        }
        
        // Option 2: Zu PREDICTIVE wenn Gradient hoch (und Z noch nicht kritisch)
        if (config.predictiveEnabled && score.value < config.startThreshold) {
            if (gradientCalculator.shouldTriggerPredictive(
                    currentZ = score.value,
                    threshold = config.predictiveDzDtThreshold,
                    maxZ = config.startThreshold
                )) {
                if (abovePredictiveThresholdSince == null) {
                    abovePredictiveThresholdSince = currentTime
                }
                
                // Kurze Bestätigung (1 Sekunde)
                val confirmDuration = currentTime - (abovePredictiveThresholdSince ?: currentTime)
                if (confirmDuration >= 1000) {
                    Log.d(TAG, "dZ/dt=${"%.1f".format(gradient)} → PREDICTIVE")
                    startPredictiveIntervention(score.value, gradient)
                    return
                }
            } else {
                abovePredictiveThresholdSince = null
            }
        }
    }
    
    private fun handlePredictiveState(score: UnrestScore, currentTime: Long) {
        val gradient = gradientCalculator.smoothedGradient.value
        
        // Option 1: Eskalieren zu SOOTHING wenn Z steigt
        if (score.value >= config.predictiveZThreshold) {
            Log.d(TAG, "PREDICTIVE: Z=${score.value} ≥ ${config.predictiveZThreshold} → SOOTHING")
            escalatePredictiveToSoothing()
            return
        }
        
        // Option 2: Zurück zu LISTENING wenn Trend sich beruhigt
        if (gradientCalculator.hasCalmingTrend(config.predictiveDzDtCalmThreshold)) {
            val predictiveDuration = currentTime - predictiveStartTime
            // Mindestens 5 Sekunden in PREDICTIVE bleiben
            if (predictiveDuration >= 5000) {
                Log.d(TAG, "PREDICTIVE: dZ/dt=${"%.1f".format(gradient)} < ${config.predictiveDzDtCalmThreshold} → LISTENING")
                endPredictiveIntervention(success = true)
                return
            }
        }
        
        // Option 3: Timeout nach 60 Sekunden → Eskalieren
        val predictiveDuration = currentTime - predictiveStartTime
        if (predictiveDuration >= 60000) {
            Log.d(TAG, "PREDICTIVE: Timeout (60s) → SOOTHING")
            escalatePredictiveToSoothing()
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
            }
            
            val confirmDuration = currentTime - (belowCalmThresholdSince ?: currentTime)
            if (confirmDuration >= config.calmConfirmSec * 1000) {
                Log.d(TAG, "SOOTHING: Z=${score.value} ≤ ${config.calmThreshold} → COOLDOWN")
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
            }
            
            val confirmDuration = currentTime - (aboveRetriggerThresholdSince ?: currentTime)
            if (confirmDuration >= config.retriggerConfirmSec * 1000) {
                Log.d(TAG, "COOLDOWN: Retrigger Z=${score.value}")
                cancelCooldownAndRetrigger()
            }
        } else {
            aboveRetriggerThresholdSince = null
        }
    }
    
    // =========================================================================
    // State Transitions
    // =========================================================================
    
    /**
     * Startet PREDICTIVE Intervention (sanft, Level 1)
     */
    private fun startPredictiveIntervention(triggerZ: Float, dZdt: Float) {
        transitionTo(SoomiState.PREDICTIVE)
        predictiveStartTime = System.currentTimeMillis()
        _isPredictive.value = true
        
        interventionStartZ = triggerZ
        interventionPeakZ = triggerZ
        
        resetConfirmationTimers()
        
        scope.launch {
            // Bei PREDICTIVE immer Level 1 (sanft)
            val profile = learningRepository.selectBestProfile(baselineMode)
            _currentSound.value = profile.soundType
            _currentLevel.value = InterventionLevel.LEVEL_1  // Immer sanft
            _isExploring.value = profile.isExploration
            
            audioOutput.setSoundType(profile.soundType)
            audioOutput.setVolume(InterventionLevel.LEVEL_1)
            
            if (!audioOutput.isPlaying()) {
                audioOutput.start()
            }
            
            // Event starten
            val eventId = learningRepository.startInterventionEvent(
                sessionId = currentSessionId,
                zStart = triggerZ,
                soundType = profile.soundType,
                level = InterventionLevel.LEVEL_1,
                baselineMode = baselineMode,
                triggerType = TriggerTypes.PREDICTIVE,
                dZdtAtTrigger = dZdt,
                wasExploration = profile.isExploration
            )
            _currentEventId.value = eventId
            
            Log.d(TAG, "PREDICTIVE started: event=$eventId, Z=$triggerZ, dZ/dt=$dZdt")
        }
    }
    
    /**
     * Eskaliert von PREDICTIVE zu SOOTHING (Level 2)
     */
    private fun escalatePredictiveToSoothing() {
        transitionTo(SoomiState.SOOTHING)
        soothingStartTime = System.currentTimeMillis()
        _isPredictive.value = false
        
        // Level erhöhen
        _currentLevel.value = InterventionLevel.LEVEL_2
        audioOutput.setVolume(InterventionLevel.LEVEL_2)
        
        Log.d(TAG, "Escalated PREDICTIVE → SOOTHING at Level 2")
    }
    
    /**
     * Beendet PREDICTIVE erfolgreich (zurück zu LISTENING)
     */
    private fun endPredictiveIntervention(success: Boolean) {
        scope.launch {
            endCurrentInterventionEvent()
        }
        
        transitionTo(SoomiState.LISTENING)
        _isPredictive.value = false
        resetTracking()
        
        applyBaselineOutput()
        
        if (baselineMode == BaselineMode.OFF) {
            audioOutput.stop()
        }
        
        Log.d(TAG, "PREDICTIVE ended successfully, back to LISTENING")
    }
    
    /**
     * Startet volle Intervention (SOOTHING, Level 2)
     */
    private fun startFullIntervention(triggerZ: Float, triggerType: String) {
        transitionTo(SoomiState.SOOTHING)
        soothingStartTime = System.currentTimeMillis()
        _isPredictive.value = false
        
        interventionStartZ = triggerZ
        interventionPeakZ = triggerZ
        
        resetConfirmationTimers()
        
        scope.launch {
            val profile = learningRepository.selectBestProfile(baselineMode)
            _currentSound.value = profile.soundType
            _currentLevel.value = profile.level
            _isExploring.value = profile.isExploration
            
            audioOutput.setSoundType(profile.soundType)
            audioOutput.setVolume(profile.level)
            
            if (!audioOutput.isPlaying()) {
                audioOutput.start()
            }
            
            val eventId = learningRepository.startInterventionEvent(
                sessionId = currentSessionId,
                zStart = triggerZ,
                soundType = profile.soundType,
                level = profile.level,
                baselineMode = baselineMode,
                triggerType = triggerType,
                wasExploration = profile.isExploration
            )
            _currentEventId.value = eventId
            
            Log.d(TAG, "Full intervention started: event=$eventId, level=${profile.level}")
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
        scope.launch {
            endCurrentInterventionEvent()
        }
        
        _cooldownRemaining.value = 0
        aboveRetriggerThresholdSince = null
        
        startFullIntervention(lastKnownZ, TriggerTypes.RETRIGGER)
    }
    
    private fun endIntervention() {
        Log.d(TAG, "Intervention ended")
        
        scope.launch {
            endCurrentInterventionEvent()
        }
        
        transitionTo(SoomiState.LISTENING)
        _cooldownRemaining.value = 0
        _isPredictive.value = false
        resetTracking()
        
        applyBaselineOutput()
        
        if (baselineMode == BaselineMode.OFF) {
            audioOutput.stop()
        }
    }
    
    private suspend fun endCurrentInterventionEvent() {
        _currentEventId.value?.let { eventId ->
            learningRepository.endInterventionEvent(
                eventId = eventId,
                zEnd = lastKnownZ
            )
            _currentEventId.value = null
            _isExploring.value = false
            Log.d(TAG, "Ended event $eventId, zEnd=$lastKnownZ")
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
        Log.d(TAG, "State: $oldState → $newState")
    }
    
    private fun resetConfirmationTimers() {
        aboveStartThresholdSince = null
        belowCalmThresholdSince = null
        aboveRetriggerThresholdSince = null
        abovePredictiveThresholdSince = null
    }
    
    private fun resetTracking() {
        resetConfirmationTimers()
        interventionStartZ = 0f
        interventionPeakZ = 0f
        cooldownStartVolume = 0f
        cooldownTotalSeconds = 0
    }
    
    /**
     * Gibt Debug-Info zurück
     */
    fun getDebugInfo(): EngineDebugInfo {
        return EngineDebugInfo(
            state = _state.value,
            level = _currentLevel.value,
            sound = _currentSound.value,
            lastZ = lastKnownZ,
            gradient = gradientCalculator.smoothedGradient.value,
            trend = gradientCalculator.trend.value,
            isPredictive = _isPredictive.value,
            isExploring = _isExploring.value,
            currentEventId = _currentEventId.value,
            cooldownRemaining = _cooldownRemaining.value,
            gradientDebug = gradientCalculator.getDebugInfo()
        )
    }
}

/**
 * Debug-Info für die Engine
 */
data class EngineDebugInfo(
    val state: SoomiState,
    val level: InterventionLevel,
    val sound: SoundType,
    val lastZ: Float,
    val gradient: Float,
    val trend: Trend,
    val isPredictive: Boolean,
    val isExploring: Boolean,
    val currentEventId: Long?,
    val cooldownRemaining: Int,
    val gradientDebug: GradientDebugInfo
)
