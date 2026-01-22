package com.soomi.baby.audio

import android.util.Log
import com.soomi.baby.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * InterventionEngine v2.7
 * 
 * Simplified Finite State Machine (FSM) for intervention management:
 * 
 * States:
 * - STOPPED:   Session not running
 * - LISTENING: Monitoring, only baseline playing
 * - SOOTHING:  Intervention sound active
 * - COOLDOWN:  Baby calm, sound fades out continuously
 * 
 * Transitions:
 * - LISTENING → SOOTHING: Unruhe >= START_THRESHOLD for CONFIRM time
 * - SOOTHING → COOLDOWN:  Unruhe <= CALM_THRESHOLD for CALM_CONFIRM time
 * - COOLDOWN → LISTENING: Countdown reaches 0
 * - COOLDOWN → SOOTHING:  Unruhe >= RETRIGGER_THRESHOLD (re-trigger)
 * 
 * v2.7 Changes:
 * - Sound fadeout during cooldown (100% → 0% continuously)
 * - Configurable cooldown duration from settings
 * - If baby cries again during cooldown, volume goes back up
 */
class InterventionEngine(
    private val audioOutput: AudioOutputEngine,
    private val learningCallback: LearningCallback? = null
) {
    companion object {
        private const val TAG = "InterventionEngine"
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
    
    // Baseline mode (separate layer)
    private var baselineMode = BaselineMode.OFF
    
    // Timing tracking
    private var stateEntryTime = 0L
    private var soothingStartTime = 0L
    private var lastScoreTime = 0L
    
    // Confirmation tracking
    private var aboveStartThresholdSince: Long? = null
    private var belowCalmThresholdSince: Long? = null
    private var aboveRetriggerThresholdSince: Long? = null
    
    // Escalation tracking
    private var escalationCount = 0
    private var currentEventStartScore = 0f
    
    // v2.7: Fadeout tracking
    private var cooldownStartVolume = 0f
    private var cooldownTotalSeconds = 0
    
    /**
     * Callback interface for learning integration
     */
    interface LearningCallback {
        fun onInterventionComplete(
            soundType: SoundType,
            level: InterventionLevel,
            baselineMode: BaselineMode,
            effective: Boolean,
            deltaZ: Float
        )
        fun getBestSoundType(baselineMode: BaselineMode): SoundType
    }
    
    /**
     * Set baseline mode (OFF/GENTLE/MEDIUM)
     */
    fun setBaselineMode(mode: BaselineMode) {
        baselineMode = mode
        if (_state.value == SoomiState.LISTENING) {
            applyBaselineOutput()
        }
    }
    
    /**
     * Start the engine - enters LISTENING state
     */
    fun start() {
        if (_state.value != SoomiState.STOPPED) return
        
        Log.d(TAG, "Starting engine")
        audioOutput.initialize()
        
        // Get best sound type from learning
        val bestSound = learningCallback?.getBestSoundType(baselineMode) ?: SoundType.BROWN_NOISE
        _currentSound.value = bestSound
        
        transitionTo(SoomiState.LISTENING)
        applyBaselineOutput()
        
        if (baselineMode != BaselineMode.OFF) {
            audioOutput.start()
        }
    }
    
    /**
     * Stop the engine - returns to STOPPED
     */
    fun stop() {
        Log.d(TAG, "Stopping engine")
        audioOutput.stop()
        transitionTo(SoomiState.STOPPED)
        resetTracking()
        _cooldownRemaining.value = 0
    }
    
    /**
     * Panic stop - immediate silence
     */
    fun panicStop() {
        Log.d(TAG, "Panic stop!")
        audioOutput.forceStop()
        transitionTo(SoomiState.LISTENING)
        resetTracking()
        _cooldownRemaining.value = 0
        
        // Re-apply baseline if needed
        if (baselineMode != BaselineMode.OFF) {
            applyBaselineOutput()
            audioOutput.start()
        }
    }
    
    /**
     * Process a new unrest score - main FSM update
     * Call this every ~500ms with the current score
     */
    fun processScore(score: UnrestScore) {
        val currentTime = System.currentTimeMillis()
        lastScoreTime = currentTime
        
        when (_state.value) {
            SoomiState.STOPPED, SoomiState.IDLE -> {
                // Do nothing
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
        }
    }
    
    /**
     * Update cooldown timer - call this every second
     * v2.7: Also handles continuous sound fadeout
     */
    fun updateCooldownTimer() {
        if (_state.value == SoomiState.COOLDOWN && _cooldownRemaining.value > 0) {
            _cooldownRemaining.value = _cooldownRemaining.value - 1
            
            // v2.7: Calculate and apply fadeout volume
            if (cooldownTotalSeconds > 0) {
                val progress = _cooldownRemaining.value.toFloat() / cooldownTotalSeconds.toFloat()
                val fadeoutVolume = cooldownStartVolume * progress
                
                // Apply the faded volume (but don't go below baseline)
                val baselineVolume = when (baselineMode) {
                    BaselineMode.OFF -> 0f
                    BaselineMode.GENTLE -> InterventionLevel.LEVEL_1.volumeMultiplier
                    BaselineMode.MEDIUM -> InterventionLevel.LEVEL_2.volumeMultiplier
                }
                val finalVolume = maxOf(fadeoutVolume, baselineVolume)
                audioOutput.setVolumeRaw(finalVolume)
                
                Log.d(TAG, "Cooldown fadeout: ${(_cooldownRemaining.value)}s remaining, volume: ${(finalVolume * 100).toInt()}%")
            }
            
            if (_cooldownRemaining.value <= 0) {
                // Cooldown complete - stop intervention
                endIntervention()
            }
        }
    }
    
    /**
     * Manual soothe trigger
     */
    fun manualSoothe() {
        if (_state.value == SoomiState.STOPPED) return
        
        Log.d(TAG, "Manual soothe triggered")
        startIntervention(0f)
    }
    
    // =========================================================================
    // State Handlers
    // =========================================================================
    
    private fun handleListeningState(score: UnrestScore, currentTime: Long) {
        // Check if unrest is above start threshold
        if (score.value >= config.startThreshold) {
            if (aboveStartThresholdSince == null) {
                aboveStartThresholdSince = currentTime
                Log.d(TAG, "Unrest above start threshold, starting confirm timer")
            }
            
            // Check if confirmed
            val confirmDuration = currentTime - (aboveStartThresholdSince ?: currentTime)
            if (confirmDuration >= config.startConfirmSec * 1000) {
                Log.d(TAG, "Start threshold confirmed, starting intervention")
                startIntervention(score.value)
            }
        } else {
            // Reset confirmation timer
            aboveStartThresholdSince = null
        }
    }
    
    private fun handleSoothingState(score: UnrestScore, currentTime: Long) {
        // Check minimum soothing time
        val soothingDuration = currentTime - soothingStartTime
        if (soothingDuration < config.minSoothingSec * 1000) {
            // Still in minimum soothing period, don't check for calm yet
            return
        }
        
        // Check if baby is calm
        if (score.value <= config.calmThreshold) {
            if (belowCalmThresholdSince == null) {
                belowCalmThresholdSince = currentTime
                Log.d(TAG, "Unrest below calm threshold, starting confirm timer")
            }
            
            // Check if confirmed
            val confirmDuration = currentTime - (belowCalmThresholdSince ?: currentTime)
            if (confirmDuration >= config.calmConfirmSec * 1000) {
                Log.d(TAG, "Calm confirmed, starting cooldown")
                startCooldown()
            }
        } else {
            // Reset calm confirmation
            belowCalmThresholdSince = null
            
            // Check for escalation (if still high)
            if (score.value >= config.startThreshold && escalationCount < config.maxEscalationsPerEvent) {
                // Could escalate level here if needed
            }
        }
    }
    
    private fun handleCooldownState(score: UnrestScore, currentTime: Long) {
        // Check for retrigger
        if (score.value >= config.retriggerThreshold) {
            if (aboveRetriggerThresholdSince == null) {
                aboveRetriggerThresholdSince = currentTime
                Log.d(TAG, "Unrest above retrigger threshold during cooldown")
            }
            
            // Check if confirmed
            val confirmDuration = currentTime - (aboveRetriggerThresholdSince ?: currentTime)
            if (confirmDuration >= config.retriggerConfirmSec * 1000) {
                Log.d(TAG, "Retrigger confirmed, cancelling cooldown")
                cancelCooldownAndRetrigger()
            }
        } else {
            aboveRetriggerThresholdSince = null
        }
    }
    
    // =========================================================================
    // State Transitions
    // =========================================================================
    
    private fun startIntervention(triggerScore: Float) {
        transitionTo(SoomiState.SOOTHING)
        soothingStartTime = System.currentTimeMillis()
        currentEventStartScore = triggerScore
        escalationCount = 0
        
        // Reset confirmation timers
        aboveStartThresholdSince = null
        belowCalmThresholdSince = null
        aboveRetriggerThresholdSince = null
        
        // Start audio at Level 2
        _currentLevel.value = InterventionLevel.LEVEL_2
        audioOutput.setSoundType(_currentSound.value)
        audioOutput.setVolume(_currentLevel.value)
        
        if (!audioOutput.isPlaying()) {
            audioOutput.start()
        }
        
        Log.d(TAG, "Intervention started at level ${_currentLevel.value}")
    }
    
    private fun startCooldown() {
        transitionTo(SoomiState.COOLDOWN)
        
        // v2.7: Store starting volume and total duration for fadeout calculation
        cooldownStartVolume = _currentLevel.value.volumeMultiplier
        cooldownTotalSeconds = config.cooldownSec
        _cooldownRemaining.value = config.cooldownSec
        
        // Reset confirmation timers
        belowCalmThresholdSince = null
        aboveRetriggerThresholdSince = null
        
        // Sound continues playing at current volume (will fade out via updateCooldownTimer)
        Log.d(TAG, "Cooldown started: ${config.cooldownSec}s, fadeout from ${(cooldownStartVolume * 100).toInt()}%")
    }
    
    private fun cancelCooldownAndRetrigger() {
        transitionTo(SoomiState.SOOTHING)
        _cooldownRemaining.value = 0
        
        // Reset confirmation timers
        aboveRetriggerThresholdSince = null
        belowCalmThresholdSince = null
        
        // v2.7: Restore full intervention volume
        audioOutput.setVolume(_currentLevel.value)
        
        Log.d(TAG, "Cooldown cancelled, back to soothing at full volume")
    }
    
    private fun endIntervention() {
        Log.d(TAG, "Intervention ended")
        
        // Report to learning
        val deltaZ = currentEventStartScore - 0f // Could track current score
        learningCallback?.onInterventionComplete(
            soundType = _currentSound.value,
            level = _currentLevel.value,
            baselineMode = baselineMode,
            effective = true,
            deltaZ = deltaZ
        )
        
        transitionTo(SoomiState.LISTENING)
        _cooldownRemaining.value = 0
        resetTracking()
        
        // Apply baseline output
        applyBaselineOutput()
        
        if (baselineMode == BaselineMode.OFF) {
            audioOutput.stop()
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
        Log.d(TAG, "State transition: $oldState -> $newState")
    }
    
    private fun resetTracking() {
        aboveStartThresholdSince = null
        belowCalmThresholdSince = null
        aboveRetriggerThresholdSince = null
        escalationCount = 0
        currentEventStartScore = 0f
        cooldownStartVolume = 0f
        cooldownTotalSeconds = 0
    }
}
