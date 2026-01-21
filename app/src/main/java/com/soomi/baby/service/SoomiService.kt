package com.soomi.baby.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.soomi.baby.R
import com.soomi.baby.SoomiApplication
import com.soomi.baby.audio.AudioFeatureExtractor
import com.soomi.baby.audio.AudioInputEngine
import com.soomi.baby.audio.AudioOutputEngine
import com.soomi.baby.audio.InterventionEngine
import com.soomi.baby.domain.model.*
import com.soomi.baby.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * SoomiService
 *
 * Foreground service that runs overnight baby monitoring.
 *
 * PRIVACY:
 * - Processes audio in memory only
 * - Never records or stores raw audio
 * - Shows notification to user that service is running
 * - All processing is local, no network required
 *
 * Battery optimization:
 * - Uses efficient audio processing
 * - Holds partial wake lock only when needed
 * - Releases resources when stopped
 * 
 * AUDIO FEEDBACK PREVENTION:
 * - Informs AudioFeatureExtractor when playback is active
 * - Uses playback level to adjust noise baseline
 */
class SoomiService : Service() {

    companion object {
        private const val TAG = "SoomiService"
        const val ACTION_START = "com.soomi.baby.START"
        const val ACTION_STOP = "com.soomi.baby.STOP"
        const val ACTION_PANIC_STOP = "com.soomi.baby.PANIC_STOP"
        const val ACTION_SOOTHE_NOW = "com.soomi.baby.SOOTHE_NOW"

        private const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            try {
                val intent = Intent(context, SoomiService::class.java).apply {
                    action = ACTION_START
                }
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service", e)
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, SoomiService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service", e)
            }
        }
    }

    // Binder for activity communication
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): SoomiService = this@SoomiService
    }

    // Components
    private var audioInput: AudioInputEngine? = null
    private var audioOutput: AudioOutputEngine? = null
    private var featureExtractor: AudioFeatureExtractor? = null
    private var interventionEngine: InterventionEngine? = null

    // Repositories
    private val app by lazy { application as SoomiApplication }
    private val sessionRepository by lazy { app.sessionRepository }
    private val settingsRepository by lazy { app.settingsRepository }
    private val learningRepository by lazy { app.learningRepository }

    // Session state
    private var currentSessionId: Long? = null
    private var processingJob: Job? = null
    private var serviceScope: CoroutineScope? = null

    // Wake lock for overnight operation
    private var wakeLock: PowerManager.WakeLock? = null

    // Observable state
    private val _currentScore = MutableStateFlow(UnrestScore(0f))
    val currentScore: StateFlow<UnrestScore> = _currentScore.asStateFlow()

    private val _sessionState = MutableStateFlow(SoomiState.IDLE)
    val sessionState: StateFlow<SoomiState> = _sessionState.asStateFlow()

    private val _currentLevel = MutableStateFlow(InterventionLevel.OFF)
    val currentLevel: StateFlow<InterventionLevel> = _currentLevel.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // Stats tracking
    private var sessionStartTime = 0L
    private var totalSoothingMs = 0L
    private var soothingStartTime = 0L
    private var unrestEventCount = 0
    private var peakScore = 0f
    private var manualInterventionCount = 0
    private var panicStopCount = 0
    
    // Track current playback state for feedback prevention
    private var isPlaybackActive = false
    private var currentPlaybackLevel = 0f

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        try {
            // Create a new scope for this service instance
            serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

            // Initialize audio components
            audioInput = AudioInputEngine(this)
            audioOutput = AudioOutputEngine()
            featureExtractor = AudioFeatureExtractor.createDefault()

            // Initialize intervention engine with learning callback
            interventionEngine = InterventionEngine(
                audioOutput = audioOutput!!,
                learningCallback = createLearningCallback()
            )

            // Observe intervention engine state
            serviceScope?.launch {
                interventionEngine?.state?.collect { state ->
                    _sessionState.value = state
                    handleStateChange(state)
                }
            }

            serviceScope?.launch {
                interventionEngine?.currentLevel?.collect { level ->
                    _currentLevel.value = level
                    // Inform feature extractor about playback level
                    updatePlaybackState(level)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> startSession()
            ACTION_STOP -> stopSession()
            ACTION_PANIC_STOP -> panicStop()
            ACTION_SOOTHE_NOW -> manualSoothe()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        try {
            stopSession()
            serviceScope?.cancel()
            serviceScope = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
        super.onDestroy()
    }
    
    /**
     * Update playback state and inform feature extractor
     * This helps prevent audio feedback from affecting baby detection
     */
    private fun updatePlaybackState(level: InterventionLevel) {
        val wasPlaying = isPlaybackActive
        isPlaybackActive = level != InterventionLevel.OFF
        currentPlaybackLevel = level.volumeMultiplier
        
        // Inform feature extractor
        featureExtractor?.setPlaybackActive(isPlaybackActive, currentPlaybackLevel)
        
        if (wasPlaying != isPlaybackActive) {
            Log.d(TAG, "Playback state changed: active=$isPlaybackActive, level=$currentPlaybackLevel")
        }
    }

    /**
     * Start a new monitoring session
     */
    fun startSession() {
        if (_isRunning.value) {
            Log.d(TAG, "Session already running")
            return
        }

        Log.d(TAG, "Starting session")

        try {
            // Start foreground service with notification
            startForegroundWithNotification()

            // Acquire wake lock for overnight operation
            acquireWakeLock()

            serviceScope?.launch {
                try {
                    // Get current settings
                    val baselineMode = settingsRepository.baselineMode.first()
                    val config = settingsRepository.thresholdConfig.first()

                    // Create session in database
                    currentSessionId = sessionRepository.startSession(baselineMode)

                    // Reset stats
                    resetStats()
                    sessionStartTime = System.currentTimeMillis()

                    // Configure intervention engine
                    interventionEngine?.config = config
                    interventionEngine?.setBaselineMode(baselineMode)

                    // Start audio processing
                    audioInput?.startCapture()
                    interventionEngine?.start()
                    
                    // Initial playback state
                    updatePlaybackState(_currentLevel.value)

                    _isRunning.value = true

                    // Start processing audio
                    startAudioProcessing()

                } catch (e: SecurityException) {
                    Log.e(TAG, "Permission denied", e)
                    stopSelf()
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting session", e)
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in startSession", e)
        }
    }

    /**
     * Stop the current session
     */
    fun stopSession() {
        if (!_isRunning.value) {
            Log.d(TAG, "Session not running, nothing to stop")
            stopForegroundAndSelf()
            return
        }

        Log.d(TAG, "Stopping session")

        _isRunning.value = false

        try {
            // Stop audio processing first
            processingJob?.cancel()
            processingJob = null
            
            audioInput?.stopCapture()
            interventionEngine?.stop()
            
            // Clear playback state
            isPlaybackActive = false
            currentPlaybackLevel = 0f

            // Finalize session in database (in a separate scope that won't be cancelled)
            val sessionId = currentSessionId
            val soothingMs = totalSoothingMs
            val events = unrestEventCount
            val peak = peakScore
            val manual = manualInterventionCount
            val panic = panicStopCount
            
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    sessionId?.let { id ->
                        // Flush any remaining samples
                        sessionRepository.flushSamples()

                        // Update final stats
                        val finalSoothingSeconds = soothingMs / 1000
                        sessionRepository.updateSessionStats(
                            sessionId = id,
                            unrestEvents = events,
                            totalSoothingSeconds = finalSoothingSeconds,
                            peakUnrestScore = peak,
                            manualInterventions = manual,
                            panicStops = panic
                        )

                        // End session
                        sessionRepository.endSession(id)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving session data", e)
                }
            }

            currentSessionId = null

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio components", e)
        }

        // Release wake lock
        releaseWakeLock()

        // Stop foreground service
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground", e)
        }
    }

    /**
     * Panic stop - immediate silence
     */
    fun panicStop() {
        panicStopCount++
        try {
            interventionEngine?.panicStop()
            isPlaybackActive = false
            currentPlaybackLevel = 0f
            featureExtractor?.setPlaybackActive(false, 0f)
            updateNotification("Paused - tap to resume")
        } catch (e: Exception) {
            Log.e(TAG, "Error in panicStop", e)
        }
    }

    /**
     * Resume from paused state
     */
    fun resume() {
        try {
            interventionEngine?.resume()
            updateNotification("Monitoring locally")
        } catch (e: Exception) {
            Log.e(TAG, "Error in resume", e)
        }
    }

    /**
     * Manual soothe trigger
     */
    fun manualSoothe() {
        manualInterventionCount++
        try {
            interventionEngine?.manualSoothe()
        } catch (e: Exception) {
            Log.e(TAG, "Error in manualSoothe", e)
        }
    }

    /**
     * Change baseline mode during session
     */
    fun setBaselineMode(mode: BaselineMode) {
        try {
            interventionEngine?.setBaselineMode(mode)
            serviceScope?.launch {
                settingsRepository.setBaselineMode(mode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting baseline mode", e)
        }
    }

    /**
     * Main audio processing loop
     */
    private fun startAudioProcessing() {
        processingJob = serviceScope?.launch {
            try {
                audioInput?.audioSamples?.collect { samples ->
                    if (!_isRunning.value) return@collect

                    // Extract features and compute score
                    val features = featureExtractor?.extractFeatures(samples) ?: return@collect
                    val scoreValue = featureExtractor?.computeUnrestScore(features) ?: return@collect
                    val trend = featureExtractor?.getScoreTrend() ?: UnrestScore.Trend.STABLE

                    val score = UnrestScore(
                        value = scoreValue,
                        trend = trend,
                        timestamp = System.currentTimeMillis()
                    )

                    _currentScore.value = score

                    // Track peak
                    if (scoreValue > peakScore) {
                        peakScore = scoreValue
                    }

                    // Feed to intervention engine
                    interventionEngine?.processScore(score)

                    // Record sample for history
                    currentSessionId?.let { sessionId ->
                        sessionRepository.recordScoreSample(
                            sessionId = sessionId,
                            score = scoreValue,
                            state = _sessionState.value
                        )
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Audio processing cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio processing", e)
            }
        }
    }

    /**
     * Handle state changes for tracking
     */
    private fun handleStateChange(state: SoomiState) {
        try {
            when (state) {
                SoomiState.EARLY_SMOOTH, SoomiState.CRISIS -> {
                    // Started soothing
                    if (soothingStartTime == 0L) {
                        soothingStartTime = System.currentTimeMillis()
                        unrestEventCount++
                    }
                    updateNotification(if (state == SoomiState.CRISIS) "Soothing (high)" else "Soothing")
                }
                SoomiState.BASELINE, SoomiState.COOLDOWN -> {
                    // Stopped soothing
                    if (soothingStartTime > 0) {
                        totalSoothingMs += System.currentTimeMillis() - soothingStartTime
                        soothingStartTime = 0L
                    }
                    updateNotification("Monitoring locally")
                }
                SoomiState.PAUSED -> {
                    if (soothingStartTime > 0) {
                        totalSoothingMs += System.currentTimeMillis() - soothingStartTime
                        soothingStartTime = 0L
                    }
                }
                else -> {}
            }

            // Log FSM transition
            currentSessionId?.let { sessionId ->
                sessionRepository.logFsmTransition(
                    sessionId = sessionId,
                    fromState = _sessionState.value,
                    toState = state,
                    scoreAtTransition = _currentScore.value.value
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling state change", e)
        }
    }

    /**
     * Create learning callback for intervention engine
     */
    private fun createLearningCallback() = object : InterventionEngine.LearningCallback {
        override fun onInterventionComplete(
            soundType: SoundType,
            level: InterventionLevel,
            baselineMode: BaselineMode,
            effective: Boolean,
            deltaZ: Float
        ) {
            serviceScope?.launch {
                try {
                    learningRepository.recordInterventionOutcome(
                        soundType = soundType,
                        baselineMode = baselineMode,
                        effective = effective,
                        deltaZ = deltaZ
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error recording intervention outcome", e)
                }
            }
        }

        override fun getBestSoundType(baselineMode: BaselineMode): SoundType {
            return try {
                runBlocking {
                    learningRepository.getBestSoundType(baselineMode)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting best sound type", e)
                SoundType.BROWN_NOISE
            }
        }
    }

    /**
     * Start foreground service with notification
     */
    private fun startForegroundWithNotification() {
        try {
            val notification = createNotification("Monitoring locally")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground", e)
        }
    }

    /**
     * Create notification for foreground service
     */
    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Panic stop action
        val panicIntent = Intent(this, SoomiService::class.java).apply {
            action = ACTION_PANIC_STOP
        }
        val panicPendingIntent = PendingIntent.getService(
            this, 1, panicIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SoomiApplication.CHANNEL_SERVICE)
            .setContentTitle("SOOMI")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", panicPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Update notification text
     */
    private fun updateNotification(text: String) {
        try {
            val notification = createNotification(text)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }

    /**
     * Acquire wake lock for overnight operation
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SOOMI::MonitoringWakeLock"
            ).apply {
                acquire(8 * 60 * 60 * 1000L)  // 8 hours max
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock", e)
        }
    }

    /**
     * Release wake lock
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
    }

    /**
     * Reset session stats
     */
    private fun resetStats() {
        totalSoothingMs = 0
        soothingStartTime = 0
        unrestEventCount = 0
        peakScore = 0f
        manualInterventionCount = 0
        panicStopCount = 0
        featureExtractor?.reset()
    }
}
