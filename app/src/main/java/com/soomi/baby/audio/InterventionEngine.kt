package com.soomi.baby.audio

import com.soomi.baby.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * InterventionEngine
 * 
 * Finite State Machine (FSM) that manages the intervention lifecycle:
 * - Monitors unrest scores
 * - Triggers interventions based on thresholds
 * - Evaluates effectiveness
 * - Manages cooldowns
 * 
 * States: IDLE → BASELINE → EARLY_SMOOTH/CRISIS → COOLDOWN → BASELINE
 *                    ↓            ↓
 *                 PAUSED ←───────┘
 * 
 * Design principles:
 * - Conservative escalation (prefer pattern switch over volume increase)
 * - Quick crisis response for sudden crying
 * - Effectiveness-based adaptation
 * - Cooldown to prevent flapping
 */
class InterventionEngine(
    private val audioOutput: AudioOutputEngine,
    private val learningCallback: LearningCallback? = null
) {
    // Configuration (can be updated from settings)
    var config = ThresholdConfig()
        set(value) {
            field = value
            audioOutput.setVolumeRaw(value.volumeCap)
        }
    
    // Current state
    private val _state = MutableStateFlow(SoomiState.IDLE)
    val state: StateFlow<SoomiState> = _state.asStateFlow()
    
    private val _currentLevel = MutableStateFlow(InterventionLevel.OFF)
    val currentLevel: StateFlow<InterventionLevel> = _currentLevel.asStateFlow()
    
    private val _currentSound = MutableStateFlow(SoundType.BROWN_NOISE)
    val currentSound: StateFlow<SoundType> = _currentSound.asStateFlow()
    
    // Baseline mode setting
    private var baselineMode = BaselineMode.OFF
    
    // Timing tracking
    private var stateEntryTime = 0L
    private var interventionStartTime = 0L
    private var lastEvaluationTime = 0L
    private var cooldownEndTime = 0L
    
    // Score tracking for effectiveness measurement
    private val recentScores = ArrayDeque<Float>(20)
    private var preInterventionMeanZ = 0f
    private var confirmationCount = 0
    private var crisisRiseTracking = false
    private var crisisRiseStartScore = 0f
    private var crisisRiseStartTime = 0L
    
    // Escalation tracking (prevent endless escalation)
    private var escalationCount = 0
    private val maxEscalations = 2
    
    // Pattern switch tracking
    private var patternSwitchUsed = false
    
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
        if (_state.value == SoomiState.BASELINE) {
            applyBaselineOutput()
        }
    }
    
    /**
     * Start the engine - enters BASELINE state
     */
    fun start() {
        if (_state.value != SoomiState.IDLE) return
        
        audioOutput.initialize()
        audioOutput.start()
        
        // Get best sound type from learning (if available)
        val bestSound = learningCallback?.getBestSoundType(baselineMode) ?: SoundType.BROWN_NOISE
        _currentSound.value = bestSound
        audioOutput.setSoundType(bestSound)
        
        transitionTo(SoomiState.BASELINE)
        applyBaselineOutput()
    }
    
    /**
     * Stop the engine - returns to IDLE
     */
    fun stop() {
        audioOutput.stop()
        transitionTo(SoomiState.IDLE)
        resetTracking()
    }
    
    /**
     * Panic stop - immediate silence and pause
     */
    fun panicStop() {
        audioOutput.forceStop()
        transitionTo(SoomiState.PAUSED)
        resetTracking()
    }
    
    /**
     * Resume from paused state
     */
    fun resume() {
        if (_state.value != SoomiState.PAUSED) return
        
        audioOutput.start()
        transitionTo(SoomiState.BASELINE)
        applyBaselineOutput()
    }
    
    /**
     * Manual "Soothe Now" trigger - enters CRISIS mode temporarily
     * 
     * @param maxDurationMs Maximum duration before auto-return to baseline
     */
    fun manualSoothe(maxDurationMs: Long = 60000) {
        if (_state.value == SoomiState.IDLE) return
        
        transitionTo(SoomiState.CRISIS)
        _currentLevel.value = InterventionLevel.LEVEL_2
        audioOutput.setVolume(InterventionLevel.LEVEL_2)
        
        // Will auto-return to baseline after evaluation period
    }
    
    /**
     * Process a new unrest score - main FSM update
     * 
     * Call this method each time a new Z score is computed (every ~500ms)
     */
    fun processScore(score: UnrestScore) {
        // Track recent scores
        recentScores.addLast(score.value)
        if (recentScores.size > 20) recentScores.removeFirst()
        
        val currentTime = System.currentTimeMillis()
        
        when (_state.value) {
            SoomiState.IDLE -> {
                // Do nothing - not running
            }
            
            SoomiState.BASELINE -> {
                handleBaselineState(score, currentTime)
            }
            
            SoomiState.EARLY_SMOOTH -> {
                handleEarlySmoothState(score, currentTime)
            }
            
            SoomiState.CRISIS -> {
                handleCrisisState(score, currentTime)
            }
            
            SoomiState.COOLDOWN -> {
                handleCooldownState(score, currentTime)
            }
            
            SoomiState.PAUSED -> {
                // Wait for manual resume
            }
        }
    }
    
    /**
     * Handle BASELINE state - monitoring for unrest
     */
    private fun handleBaselineState(score: UnrestScore, currentTime: Long) {
        // Check for crisis condition (sudden spike)
        if (checkCrisisCondition(score, currentTime)) {
            enterCrisisMode(currentTime)
            return
        }
        
        // Check for early intervention condition
        if (score.value >= config.zEarlyThreshold) {
            confirmationCount++
            
            // Require sustained elevation (2+ windows or 2 seconds)
            val timeSinceEntry = currentTime - stateEntryTime
            if (confirmationCount >= 2 || timeSinceEntry >= config.earlyConfirmationWindowMs) {
                enterEarlySmoothMode(currentTime)
            }
        } else {
            confirmationCount = 0
        }
    }
    
    /**
     * Handle EARLY_SMOOTH state - gentle intervention active
     */
    private fun handleEarlySmoothState(score: UnrestScore, currentTime: Long) {
        // Check for crisis escalation
        if (checkCrisisCondition(score, currentTime)) {
            enterCrisisMode(currentTime)
            return
        }
        
        // Check for successful calming
        if (score.value < config.zStopThreshold) {
            val stableDuration = currentTime - stateEntryTime
            if (stableDuration >= 5000) {  // Stable for 5 seconds
                exitIntervention(currentTime, true)
                return
            }
        }
        
        // Evaluate effectiveness periodically
        if (currentTime - lastEvaluationTime >= config.evaluationTimeMs) {
            evaluateEffectiveness(currentTime)
        }
    }
    
    /**
     * Handle CRISIS state - stronger intervention
     */
    private fun handleCrisisState(score: UnrestScore, currentTime: Long) {
        // Check for successful calming
        if (score.value < config.zStopThreshold) {
            val stableDuration = currentTime - stateEntryTime
            if (stableDuration >= 3000) {  // Shorter stable period for crisis
                exitIntervention(currentTime, true)
                return
            }
        }
        
        // Check for step-down to early smooth
        if (score.value < config.zCrisisThreshold * 0.7f && score.value >= config.zEarlyThreshold) {
            val duration = currentTime - stateEntryTime
            if (duration >= 5000) {  // Maintain for 5s before step-down
                stepDownFromCrisis(currentTime)
                return
            }
        }
        
        // Evaluate effectiveness
        if (currentTime - lastEvaluationTime >= config.evaluationTimeMs) {
            evaluateEffectiveness(currentTime)
        }
    }
    
    /**
     * Handle COOLDOWN state - waiting before re-intervention
     */
    private fun handleCooldownState(score: UnrestScore, currentTime: Long) {
        // Allow immediate crisis response even during cooldown
        if (score.value >= config.zCrisisThreshold) {
            enterCrisisMode(currentTime)
            return
        }
        
        // Check if cooldown complete
        if (currentTime >= cooldownEndTime) {
            transitionTo(SoomiState.BASELINE)
            applyBaselineOutput()
            resetTracking()
        }
    }
    
    /**
     * Check if crisis condition is met (fast rise or very high score)
     */
    private fun checkCrisisCondition(score: UnrestScore, currentTime: Long): Boolean {
        // Direct threshold check
        if (score.value >= config.zCrisisThreshold * 1.1f) {  // 10% above threshold = immediate
            return true
        }
        
        // Fast rise detection
        if (score.value >= config.zCrisisThreshold) {
            if (!crisisRiseTracking) {
                crisisRiseTracking = true
                crisisRiseStartScore = recentScores.firstOrNull() ?: score.value
                crisisRiseStartTime = currentTime - (recentScores.size * 500L)  // Approximate
            }
            
            val riseTime = currentTime - crisisRiseStartTime
            val riseAmount = score.value - crisisRiseStartScore
            
            // Fast rise: jumped 60+ points in under 1 second
            if (riseAmount >= 60 && riseTime <= config.crisisRiseTimeMs) {
                return true
            }
            
            // Sustained high: above threshold for over 1 second
            if (riseTime >= config.crisisRiseTimeMs) {
                return true
            }
        } else {
            crisisRiseTracking = false
        }
        
        return false
    }
    
    /**
     * Enter early smooth intervention mode
     */
    private fun enterEarlySmoothMode(currentTime: Long) {
        transitionTo(SoomiState.EARLY_SMOOTH)
        
        // Capture pre-intervention baseline
        preInterventionMeanZ = calculateRecentMean()
        interventionStartTime = currentTime
        lastEvaluationTime = currentTime
        escalationCount = 0
        patternSwitchUsed = false
        
        // Set intervention level based on baseline
        val level = when (baselineMode) {
            BaselineMode.OFF -> InterventionLevel.LEVEL_1
            BaselineMode.GENTLE -> InterventionLevel.LEVEL_2
            BaselineMode.MEDIUM -> InterventionLevel.LEVEL_2
        }
        
        _currentLevel.value = level
        audioOutput.setVolume(level)
    }
    
    /**
     * Enter crisis intervention mode
     */
    private fun enterCrisisMode(currentTime: Long) {
        transitionTo(SoomiState.CRISIS)
        
        // Capture baseline if not already in intervention
        if (preInterventionMeanZ == 0f) {
            preInterventionMeanZ = calculateRecentMean()
        }
        
        interventionStartTime = currentTime
        lastEvaluationTime = currentTime
        
        // Fast attack - jump to level 2 or 3
        val level = InterventionLevel.LEVEL_3
        _currentLevel.value = level
        audioOutput.setVolume(level)
    }
    
    /**
     * Step down from crisis to early smooth
     */
    private fun stepDownFromCrisis(currentTime: Long) {
        transitionTo(SoomiState.EARLY_SMOOTH)
        
        // Reduce level by one
        val newLevel = _currentLevel.value.previous()
        _currentLevel.value = newLevel
        audioOutput.setVolume(newLevel)
        
        lastEvaluationTime = currentTime
    }
    
    /**
     * Evaluate intervention effectiveness
     */
    private fun evaluateEffectiveness(currentTime: Long) {
        lastEvaluationTime = currentTime
        
        val currentMeanZ = calculateRecentMean()
        val deltaZ = preInterventionMeanZ - currentMeanZ
        
        if (deltaZ >= config.improvementThreshold) {
            // Effective! Hold current level for minimum time, then step down
            // (Step down will happen naturally as score drops)
        } else {
            // Not effective - try alternatives before escalating
            handleIneffectiveIntervention(currentTime)
        }
    }
    
    /**
     * Handle case where current intervention isn't working
     */
    private fun handleIneffectiveIntervention(currentTime: Long) {
        // Strategy order:
        // 1. Pattern switch (if not already tried)
        // 2. Limited escalation (max 2 times)
        // 3. Give up and enter cooldown
        
        if (!patternSwitchUsed) {
            // Try switching sound pattern
            val newSound = when (_currentSound.value) {
                SoundType.BROWN_NOISE -> SoundType.SHUSH_PULSE
                SoundType.PINK_NOISE -> SoundType.BROWN_NOISE
                SoundType.SHUSH_PULSE -> SoundType.BROWN_NOISE
            }
            _currentSound.value = newSound
            audioOutput.setSoundType(newSound)
            patternSwitchUsed = true
            return
        }
        
        if (escalationCount < maxEscalations) {
            // Limited escalation
            val newLevel = _currentLevel.value.next()
            if (newLevel != _currentLevel.value) {
                _currentLevel.value = newLevel
                audioOutput.setVolume(newLevel)
                escalationCount++
            } else {
                // Already at max - give up
                exitIntervention(currentTime, false)
            }
            return
        }
        
        // All options exhausted - enter cooldown
        exitIntervention(currentTime, false)
    }
    
    /**
     * Exit intervention mode
     */
    private fun exitIntervention(currentTime: Long, wasEffective: Boolean) {
        val finalDeltaZ = preInterventionMeanZ - calculateRecentMean()
        
        // Report to learning system
        learningCallback?.onInterventionComplete(
            soundType = _currentSound.value,
            level = _currentLevel.value,
            baselineMode = baselineMode,
            effective = wasEffective,
            deltaZ = finalDeltaZ
        )
        
        // Enter cooldown
        transitionTo(SoomiState.COOLDOWN)
        cooldownEndTime = currentTime + config.cooldownTimeMs
        
        // Return to baseline output
        applyBaselineOutput()
    }
    
    /**
     * Apply baseline output level
     */
    private fun applyBaselineOutput() {
        val level = when (baselineMode) {
            BaselineMode.OFF -> InterventionLevel.OFF
            BaselineMode.GENTLE -> InterventionLevel.LEVEL_1
            BaselineMode.MEDIUM -> InterventionLevel.LEVEL_2
        }
        _currentLevel.value = level
        audioOutput.setVolume(level)
    }
    
    /**
     * Transition to new state
     */
    private fun transitionTo(newState: SoomiState) {
        _state.value = newState
        stateEntryTime = System.currentTimeMillis()
    }
    
    /**
     * Calculate mean of recent scores
     */
    private fun calculateRecentMean(): Float {
        return if (recentScores.isNotEmpty()) {
            recentScores.average().toFloat()
        } else {
            0f
        }
    }
    
    /**
     * Reset all tracking variables
     */
    private fun resetTracking() {
        recentScores.clear()
        preInterventionMeanZ = 0f
        confirmationCount = 0
        crisisRiseTracking = false
        escalationCount = 0
        patternSwitchUsed = false
    }
}
