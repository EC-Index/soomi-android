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
import java.util.Timer
import java.util.TimerTask

/**
 * SoomiService v2.6
 *
 * Foreground service for overnight baby monitoring.
 * 
 * v2.6 Changes:
 * - New state machine: STOPPED, LISTENING, SOOTHING, COOLDOWN
 * - Cooldown timer with countdown
 * - Sound continues during cooldown
 *
 * PRIVACY:
 * - Processes audio in memory only
 * - Never records or stores raw audio
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
    
    // Cooldown timer
    private var cooldownTimer: Timer? = null

    // Wake lock
    private var wakeLock: PowerManager.WakeLock? = null

    // Observable state
    private val _currentScore = MutableStateFlow(UnrestScore(0f))
    val currentScore: StateFlow<UnrestScore> = _currentScore.asStateFlow()

    private val _sessionState = MutableStateFlow(SoomiState.STOPPED)
    val sessionState: StateFlow<SoomiState> = _sessionState.asStateFlow()

    private val _currentLevel = MutableStateFlow(InterventionLevel.OFF)
    val currentLevel: StateFlow<InterventionLevel> = _currentLevel.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _cooldownRemaining = MutableStateFlow(0)
    val cooldownRemaining: StateFlow<Int> = _cooldownRemaining.asStateFlow()

    // Stats tracking
    private var sessionStartTime = 0L
    private var totalSoothingMs = 0L
    private var soothingStartTime = 0L
    private var unrestEventCount = 0
    private var peakScore = 0f
    private var manualInterventionCount = 0
    private var panicStopCount = 0
    
    private var isPlaybackActive = false
    private var currentPlaybackLevel = 0f

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        try {
            serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

            audioInput = AudioInputEngine(this)
            audioOutput = AudioOutputEngine()
            featureExtractor = AudioFeatureExtractor.createDefault()

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
                    updatePlaybackState(level)
                }
            }
            
            serviceScope?.launch {
                interventionEngine?.cooldownRemaining?.collect { remaining ->
                    _cooldownRemaining.value = remaining
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
            cooldownTimer?.cancel()
            serviceScope?.cancel()
            serviceScope = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
        super.onDestroy()
    }
    
    private fun updatePlaybackState(level: InterventionLevel) {
        val wasPlaying = isPlaybackActive
        isPlaybackActive = level != InterventionLevel.OFF
        currentPlaybackLevel = level.volumeMultiplier
        
        featureExtractor?.setPlaybackActive(isPlaybackActive, currentPlaybackLevel)
        
        if (wasPlaying != isPlaybackActive) {
            Log.d(TAG, "Playback state changed: active=$isPlaybackActive, level=$currentPlaybackLevel")
        }
    }

    fun startSession() {
        if (_isRunning.value) {
            Log.d(TAG, "Session already running")
            return
        }

        Log.d(TAG, "Starting session")

        try {
            startForegroundWithNotification()
            acquireWakeLock()

            serviceScope?.launch {
                try {
                    val baselineMode = settingsRepository.baselineMode.first()
                    val config = settingsRepository.thresholdConfig.first()

                    currentSessionId = sessionRepository.startSession(baselineMode)

                    resetStats()
                    sessionStartTime = System.currentTimeMillis()

                    // Configure intervention engine with new config
                    interventionEngine?.config = InterventionConfig(
                        startThreshold = 70f,
                        calmThreshold = 35f,
                        retriggerThreshold = 55f,
                        startConfirmSec = 2,
                        calmConfirmSec = 3,
                        cooldownSec = 20,
                        retriggerConfirmSec = 1,
                        minSoothingSec = 10,
                        maxEscalationsPerEvent = 2,
                        volumeCap = config.volumeCap
                    )
                    interventionEngine?.setBaselineMode(baselineMode)

                    audioInput?.startCapture()
                    interventionEngine?.start()
                    
                    // Start cooldown timer (runs every second)
                    startCooldownTimer()
                    
                    updatePlaybackState(_currentLevel.value)

                    _isRunning.value = true

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

    fun stopSession() {
        if (!_isRunning.value) {
            Log.d(TAG, "Session not running")
            stopForegroundAndSelf()
            return
        }

        Log.d(TAG, "Stopping session")

        _isRunning.value = false

        try {
            processingJob?.cancel()
            processingJob = null
            
            cooldownTimer?.cancel()
            cooldownTimer = null
            
            audioInput?.stopCapture()
            interventionEngine?.stop()
            
            isPlaybackActive = false
            currentPlaybackLevel = 0f

            val sessionId = currentSessionId
            val soothingMs = totalSoothingMs
            val events = unrestEventCount
            val peak = peakScore
            val manual = manualInterventionCount
            val panic = panicStopCount
            
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    sessionId?.let { id ->
                        sessionRepository.flushSamples()

                        val finalSoothingSeconds = soothingMs / 1000
                        sessionRepository.updateSessionStats(
                            sessionId = id,
                            unrestEvents = events,
                            totalSoothingSeconds = finalSoothingSeconds,
                            peakUnrestScore = peak,
                            manualInterventions = manual,
                            panicStops = panic
                        )

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

        releaseWakeLock()
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

    fun panicStop() {
        panicStopCount++
        try {
            interventionEngine?.panicStop()
            _cooldownRemaining.value = 0
            isPlaybackActive = false
            currentPlaybackLevel = 0f
            featureExtractor?.setPlaybackActive(false, 0f)
            updateNotification("Pausiert - tippen zum Fortsetzen")
        } catch (e: Exception) {
            Log.e(TAG, "Error in panicStop", e)
        }
    }

    fun manualSoothe() {
        manualInterventionCount++
        try {
            interventionEngine?.manualSoothe()
        } catch (e: Exception) {
            Log.e(TAG, "Error in manualSoothe", e)
        }
    }

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
    
    private fun startCooldownTimer() {
        cooldownTimer?.cancel()
        cooldownTimer = Timer()
        cooldownTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                interventionEngine?.updateCooldownTimer()
            }
        }, 1000, 1000) // Every second
    }

    private fun startAudioProcessing() {
        processingJob = serviceScope?.launch {
            try {
                audioInput?.audioSamples?.collect { samples ->
                    if (!_isRunning.value) return@collect

                    val features = featureExtractor?.extractFeatures(samples) ?: return@collect
                    val scoreValue = featureExtractor?.computeUnrestScore(features) ?: return@collect
                    val trend = featureExtractor?.getScoreTrend() ?: UnrestScore.Trend.STABLE

                    val score = UnrestScore(
                        value = scoreValue,
                        trend = trend,
                        timestamp = System.currentTimeMillis()
                    )

                    _currentScore.value = score

                    if (scoreValue > peakScore) {
                        peakScore = scoreValue
                    }

                    interventionEngine?.processScore(score)

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

    private fun handleStateChange(state: SoomiState) {
        try {
            when (state) {
                SoomiState.SOOTHING -> {
                    if (soothingStartTime == 0L) {
                        soothingStartTime = System.currentTimeMillis()
                        unrestEventCount++
                    }
                    updateNotification("Beruhigung aktiv")
                }
                SoomiState.COOLDOWN -> {
                    updateNotification("Abklingzeit läuft")
                }
                SoomiState.LISTENING, SoomiState.BASELINE -> {
                    if (soothingStartTime > 0) {
                        totalSoothingMs += System.currentTimeMillis() - soothingStartTime
                        soothingStartTime = 0L
                    }
                    updateNotification("Überwacht lokal")
                }
                SoomiState.STOPPED, SoomiState.IDLE -> {
                    if (soothingStartTime > 0) {
                        totalSoothingMs += System.currentTimeMillis() - soothingStartTime
                        soothingStartTime = 0L
                    }
                }
            }

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

    private fun startForegroundWithNotification() {
        try {
            val notification = createNotification("Überwacht lokal")

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

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val panicIntent = Intent(this, SoomiService::class.java).apply {
            action = ACTION_PANIC_STOP
        }
        val panicPendingIntent = PendingIntent.getService(
            this, 1, panicIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SoomiApplication.CHANNEL_SERVICE)
            .setContentTitle("soomi Baby")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", panicPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val notification = createNotification(text)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SOOMI::MonitoringWakeLock"
            ).apply {
                acquire(8 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock", e)
        }
    }

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
