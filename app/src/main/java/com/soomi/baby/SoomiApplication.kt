package com.soomi.baby

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.soomi.baby.data.local.SoomiDatabase
import com.soomi.baby.data.preferences.SoomiPreferences
import com.soomi.baby.data.repository.SessionRepository
import com.soomi.baby.data.repository.SettingsRepository
import com.soomi.baby.data.repository.LearningRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * SOOMI Application class
 * 
 * Provides dependency injection via manual service locator pattern.
 * This keeps dependencies minimal while maintaining testability.
 * 
 * Privacy note: No analytics SDKs initialized by default.
 * All data processing happens locally on-device.
 */
class SoomiApplication : Application() {

    // Application-scoped coroutine scope for background work
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Lazy initialization of components
    val database: SoomiDatabase by lazy { SoomiDatabase.getInstance(this) }
    val preferences: SoomiPreferences by lazy { SoomiPreferences(this) }
    
    // Repositories
    val sessionRepository: SessionRepository by lazy { 
        SessionRepository(database.sessionDao(), applicationScope) 
    }
    val settingsRepository: SettingsRepository by lazy { 
        SettingsRepository(preferences) 
    }
    val learningRepository: LearningRepository by lazy { 
        LearningRepository(database.learningDao(), applicationScope) 
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        // Main service channel - required for foreground service
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "SOOMI Active Session",
            NotificationManager.IMPORTANCE_LOW  // Low importance = no sound, minimal visual
        ).apply {
            description = "Shows when SOOMI is monitoring locally"
            setShowBadge(false)
        }

        // Alert channel for parent notifications
        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "Parent Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Important alerts about baby's sleep"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(alertChannel)
    }

    companion object {
        const val CHANNEL_SERVICE = "soomi_service"
        const val CHANNEL_ALERTS = "soomi_alerts"
    }
}
