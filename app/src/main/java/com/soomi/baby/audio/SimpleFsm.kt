package com.soomi.baby.audio

import android.util.Log
import com.soomi.baby.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SOOMI v2.7 MVP - Simplified Finite State Machine
 * 
 * 4 States:
 * - STOPPED:   Session aus
 * - LISTENING: Session aktiv, nur Baseline (wenn gewählt)
 * - SOOTHING:  Intervention läuft (Baby unruhig)
 * - COOLDOWN:  Baby ruhig, aber Sound läuft noch weiter mit Countdown
 * 
 * Transitions:
 * - LISTENING → SOOTHING: Unruhe ≥ 70 für 1-2s
 * - SOOTHING → COOLDOWN:  Unruhe ≤ 35 für 3s
 * - COOLDOWN → LISTENING: Timer bei 0 (Sound stoppt)
 * - COOLDOWN → SOOTHING:  Unruhe ≥ 55 (Retrigger, Sound bleibt an)
 * 
 * Future (v3.0): Learning & Cascading interventions per patent
 */
class SimpleFsm(
    private val audioOutput: AudioOutputEngine,
    private val onStateChange: ((SoomiState, SoomiState) -> Unit)? = null
) {
    companion object {
        private const val TAG = "SimpleFsm"
    }
    
    // Configuration
    var thresholds = FsmThresholds()
    
    // Current state
    private val _state = MutableStateFlow(SoomiState.STOPPED)
    val state: StateFlow<SoomiState> = _state.asStateFlow()
    
    // Intervention level
    private val _interventionLevel = MutableStateFlow(InterventionLevel.OFF)
    val interventionLevel: StateFlow<InterventionLevel> = _interventionLevel.asStateFlow()
    
    // Cooldown timer
    private val _cooldownRemainingMs = MutableStateFlow(0L)
    val cooldownRemainingMs: StateFlow<Long> = _cooldownRemainingMs.asStateFlow()
    
    // Baseline mode
    private var baselineMode = BaselineMode.OFF
    private var currentSoundType = SoundType.BROWN_NOISE
    
    // Transition tracking
    private var soothingTriggerStartTime: Long? = null
    private var cooldownTriggerStartTime: Long? = null
    private var cooldownStartTime: Long? = null
    
    // Stats
    private var soothingStartTime: Long? = null
    private var _totalSoothingMs = 0L
    val totalSoothingMs: Long get() = _totalSoothingMs
    
    private var _soothingEventCount = 0
    val soothingEventCount: Int get() = _soothingEventCount
    
    private var _peakScore = 0f
    val peakScore: Float get() = _peakScore
    
    // ========================================================================
    // PUBLIC API
    // ========================================================================
    
    /**
     * Start session - transitions from STOPPED to LISTENING
     */
    fun startSession(baseline: BaselineMode, soundType: SoundType = SoundType.BROWN_NOISE) {
        if (_state.value != SoomiState.STOPPED && _state.value != SoomiState.IDLE) {
            Log.w(TAG, "Cannot start: already in state ${_state.value}")
            return
        }
        
        Log.i(TAG, "Starting session with baseline=$baseline, sound=$soundType")
        
        baselineMode = baseline
        currentSoundType = soundType
        resetStats()
        
        // Start audio output
        audioOutput.start(soundType)
        applyBaselineLevel()
        
        transitionTo(SoomiState.LISTENING)
    }
    
    /**
     * Stop session - transitions to STOPPED
     */
    fun stopSession() {
        Log.i(TAG, "Stopping session from state ${_state.value}")
        
        // Finalize soothing time if currently soothing
        if (_state.value == SoomiState.SOOTHING && soothingStartTime != null) {
            _totalSoothingMs += System.currentTimeMillis() - soothingStartTime!!
        }
        
        audioOutput.stop()
        _interventionLevel.value = InterventionLevel.OFF
        _cooldownRemainingMs.value = 0L
        
        transitionTo(SoomiState.STOPPED)
        resetTracking()
    }
    
    /**
     * Process a new unrest score - main FSM tick
     * Call this every ~500ms with the current score
     */
    fun processScore(score: UnrestScore) {
        if (_state.value == SoomiState.STOPPED || _state.value == SoomiState.IDLE) return
        
        val now = System.currentTimeMillis()
        
        // Track peak
        if (score.value > _peakScore) {
            _peakScore = score.value
        }
        
        when (_state.value) {
            SoomiState.LISTENING, SoomiState.BASELINE -> handleListeningState(score, now)
            SoomiState.SOOTHING -> handleSoothingState(score, now)
            SoomiState.COOLDOWN -> handleCooldownState(score, now)
            SoomiState.STOPPED, SoomiState.IDLE -> { /* Do nothing */ }
        }
    }
    
    /**
     * Update cooldown timer - call every second when in COOLDOWN
     */
    fun tickCooldown() {
        if (_state.value != SoomiState.COOLDOWN) return
        
        val startTime = cooldownStartTime ?: return
        val elapsed = System.currentTimeMillis() - startTime
        val remaining = (thresholds.cooldownDurationMs - elapsed).coerceAtLeast(0)
        
        _cooldownRemainingMs.value = remaining
        
        if (remaining <= 0) {
            // Timer expired → LISTENING
            Log.i(TAG, "Cooldown timer expired, transitioning to LISTENING")
            exitCooldown()
        }
    }
    
    /**
     * Set baseline mode (can change during session)
     */
    fun setBaselineMode(mode: BaselineMode) {
        baselineMode = mode
        if (_state.value == SoomiState.LISTENING || _state.value == SoomiState.BASELINE) {
            applyBaselineLevel()
        }
    }
    
    /**
     * Set sound type (can change during session)
     */
    fun setSoundType(type: SoundType) {
        currentSoundType = type
        if (_state.value.isSessionActive) {
            audioOutput.setSoundType(type)
        }
    }
    
    /**
     * Manual soothe trigger - immediately enter SOOTHING
     */
    fun manualSoothe() {
        if (_state.value == SoomiState.STOPPED || _state.value == SoomiState.IDLE) return
        
        Log.i(TAG, "Manual soothe triggered")
        enterSoothing()
    }
    
    // ========================================================================
    // STATE HANDLERS
    // ========================================================================
    
    /**
     * LISTENING state: Monitor for unrest
     * Transition to SOOTHING if Unruhe ≥ 70 for 1-2s
     */
    private fun handleListeningState(score: UnrestScore, now: Long) {
        if (score.value >= thresholds.soothingTriggerThreshold) {
            // Score is high enough
            if (soothingTriggerStartTime == null) {
                soothingTriggerStartTime = now
                Log.d(TAG, "LISTENING: Score ${score.value} >= ${thresholds.soothingTriggerThreshold}, starting trigger timer")
            } else {
                val duration = now - soothingTriggerStartTime!!
                if (duration >= thresholds.soothingTriggerDurationMs) {
                    // Threshold met for required duration → SOOTHING
                    Log.i(TAG, "LISTENING → SOOTHING: Score ${score.value} held for ${duration}ms")
                    enterSoothing()
                }
            }
        } else {
            // Score dropped below threshold, reset timer
            if (soothingTriggerStartTime != null) {
                Log.d(TAG, "LISTENING: Score ${score.value} dropped below threshold, resetting timer")
                soothingTriggerStartTime = null
            }
        }
    }
    
    /**
     * SOOTHING state: Active intervention
     * Transition to COOLDOWN if Unruhe ≤ 35 for 3s
     */
    private fun handleSoothingState(score: UnrestScore, now: Long) {
        if (score.value <= thresholds.cooldownTriggerThreshold) {
            // Score is low enough
            if (cooldownTriggerStartTime == null) {
                cooldownTriggerStartTime = now
                Log.d(TAG, "SOOTHING: Score ${score.value} <= ${thresholds.cooldownTriggerThreshold}, starting cooldown trigger timer")
            } else {
                val duration = now - cooldownTriggerStartTime!!
                if (duration >= thresholds.cooldownTriggerDurationMs) {
                    // Baby calm for required duration → COOLDOWN
                    Log.i(TAG, "SOOTHING → COOLDOWN: Score ${score.value} held for ${duration}ms")
                    enterCooldown()
                }
            }
        } else {
            // Score still high, reset cooldown trigger timer
            if (cooldownTriggerStartTime != null) {
                Log.d(TAG, "SOOTHING: Score ${score.value} above threshold, resetting timer")
                cooldownTriggerStartTime = null
            }
        }
    }
    
    /**
     * COOLDOWN state: Sound still playing, waiting
     * - Timer expires → LISTENING (sound stops)
     * - Unruhe ≥ 55 → SOOTHING (retrigger, sound stays on)
     */
    private fun handleCooldownState(score: UnrestScore, now: Long) {
        // Check for retrigger
        if (score.value >= thresholds.retriggerThreshold) {
            Log.i(TAG, "COOLDOWN → SOOTHING: Retrigger at score ${score.value}")
            enterSoothingFromRetrigger()
            return
        }
        
        // Timer check is done in tickCooldown()
    }
    
    // ========================================================================
    // STATE TRANSITIONS
    // ========================================================================
    
    private fun enterSoothing() {
        val fromState = _state.value
        
        // Track soothing start
        soothingStartTime = System.currentTimeMillis()
        _soothingEventCount++
        
        // Reset tracking
        soothingTriggerStartTime = null
        cooldownTriggerStartTime = null
        
        // Set intervention level
        val level = when (baselineMode) {
            BaselineMode.OFF -> InterventionLevel.LEVEL_2
            BaselineMode.GENTLE -> InterventionLevel.LEVEL_3
            BaselineMode.MEDIUM -> InterventionLevel.LEVEL_3
        }
        _interventionLevel.value = level
        audioOutput.setLevel(level)
        
        // Start audio if not already playing
        if (!audioOutput.isPlaying()) {
            audioOutput.start(currentSoundType)
        }
        
        transitionTo(SoomiState.SOOTHING)
    }
    
    private fun enterSoothingFromRetrigger() {
        // Retrigger: sound is already playing, just update state
        val fromState = _state.value
        
        // Don't reset soothingStartTime - continuous soothing
        cooldownStartTime = null
        _cooldownRemainingMs.value = 0L
        cooldownTriggerStartTime = null
        
        // Keep current intervention level (already high)
        
        transitionTo(SoomiState.SOOTHING)
    }
    
    private fun enterCooldown() {
        val fromState = _state.value
        
        // Track soothing time
        if (soothingStartTime != null) {
            _totalSoothingMs += System.currentTimeMillis() - soothingStartTime!!
            soothingStartTime = null
        }
        
        // Reset tracking
        cooldownTriggerStartTime = null
        
        // Start cooldown timer
        cooldownStartTime = System.currentTimeMillis()
        _cooldownRemainingMs.value = thresholds.cooldownDurationMs
        
        // Keep sound playing at current level during cooldown
        // (Sound continues to play)
        
        transitionTo(SoomiState.COOLDOWN)
    }
    
    private fun exitCooldown() {
        // Timer expired, stop intervention sound
        cooldownStartTime = null
        _cooldownRemainingMs.value = 0L
        
        // Return to baseline level
        applyBaselineLevel()
        
        transitionTo(SoomiState.LISTENING)
    }
    
    private fun transitionTo(newState: SoomiState) {
        val oldState = _state.value
        if (oldState == newState) return
        
        Log.i(TAG, "State transition: $oldState → $newState")
        _state.value = newState
        
        onStateChange?.invoke(oldState, newState)
    }
    
    // ========================================================================
    // HELPERS
    // ========================================================================
    
    private fun applyBaselineLevel() {
        val level = when (baselineMode) {
            BaselineMode.OFF -> InterventionLevel.OFF
            BaselineMode.GENTLE -> InterventionLevel.LEVEL_1
            BaselineMode.MEDIUM -> InterventionLevel.LEVEL_2
        }
        _interventionLevel.value = level
        audioOutput.setLevel(level)
        
        // If baseline is OFF, stop audio
        if (baselineMode == BaselineMode.OFF) {
            audioOutput.stop()
        } else if (!audioOutput.isPlaying()) {
            audioOutput.start(currentSoundType)
        }
    }
    
    private fun resetTracking() {
        soothingTriggerStartTime = null
        cooldownTriggerStartTime = null
        cooldownStartTime = null
        soothingStartTime = null
    }
    
    private fun resetStats() {
        _totalSoothingMs = 0L
        _soothingEventCount = 0
        _peakScore = 0f
        resetTracking()
    }
}
