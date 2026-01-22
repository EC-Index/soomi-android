package com.soomi.baby.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.soomi.baby.domain.model.BaselineMode
import com.soomi.baby.domain.model.SoundProfile
import com.soomi.baby.domain.model.ThresholdConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * DataStore-based preferences for SOOMI settings
 * 
 * Stores user preferences that should persist across app restarts.
 * 
 * v2.7: Added cooldownSeconds for configurable cooldown duration
 */

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "soomi_settings")

class SoomiPreferences(private val context: Context) {
    
    // Keys
    private object Keys {
        val BASELINE_MODE = stringPreferencesKey("baseline_mode")
        val Z_EARLY_THRESHOLD = floatPreferencesKey("z_early_threshold")
        val Z_CRISIS_THRESHOLD = floatPreferencesKey("z_crisis_threshold")
        val Z_STOP_THRESHOLD = floatPreferencesKey("z_stop_threshold")
        val VOLUME_CAP = floatPreferencesKey("volume_cap")
        val TELEMETRY_ENABLED = booleanPreferencesKey("telemetry_enabled")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val LAST_SESSION_PROMPTED = longPreferencesKey("last_session_prompted")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val AUDIO_OUTPUT_MODE = stringPreferencesKey("audio_output_mode")
        val SINGLE_PHONE_MODE = booleanPreferencesKey("single_phone_mode")
        
        // v2.6: Sound Profile
        val SOUND_PROFILE = stringPreferencesKey("sound_profile")
        
        // v2.7: Configurable Cooldown Duration
        val COOLDOWN_SECONDS = intPreferencesKey("cooldown_seconds")
    }
    
    // --- Baseline Mode ---
    
    val baselineMode: Flow<BaselineMode> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            val value = prefs[Keys.BASELINE_MODE] ?: BaselineMode.GENTLE.name
            try {
                BaselineMode.valueOf(value)
            } catch (e: IllegalArgumentException) {
                BaselineMode.GENTLE
            }
        }
    
    suspend fun setBaselineMode(mode: BaselineMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BASELINE_MODE] = mode.name
        }
    }
    
    // --- Sound Profile (v2.6) ---
    
    val soundProfile: Flow<SoundProfile> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            val value = prefs[Keys.SOUND_PROFILE] ?: SoundProfile.DEFAULT.name
            try {
                val profile = SoundProfile.valueOf(value)
                // If stored profile is Pro but Pro is disabled, fall back to default
                if (profile.isPro && !SoundProfile.IS_PRO_ENABLED) {
                    SoundProfile.DEFAULT
                } else {
                    profile
                }
            } catch (e: IllegalArgumentException) {
                SoundProfile.DEFAULT
            }
        }
    
    suspend fun setSoundProfile(profile: SoundProfile) {
        // Don't allow setting Pro profiles if Pro is not enabled
        val profileToSave = if (profile.isPro && !SoundProfile.IS_PRO_ENABLED) {
            SoundProfile.DEFAULT
        } else {
            profile
        }
        context.dataStore.edit { prefs ->
            prefs[Keys.SOUND_PROFILE] = profileToSave.name
        }
    }
    
    // --- Threshold Config ---
    
    val thresholdConfig: Flow<ThresholdConfig> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            ThresholdConfig(
                zEarlyThreshold = prefs[Keys.Z_EARLY_THRESHOLD] ?: 15f,
                zCrisisThreshold = prefs[Keys.Z_CRISIS_THRESHOLD] ?: 80f,
                zStopThreshold = prefs[Keys.Z_STOP_THRESHOLD] ?: 10f,
                volumeCap = prefs[Keys.VOLUME_CAP] ?: 0.85f
            )
        }
    
    suspend fun setZEarlyThreshold(value: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.Z_EARLY_THRESHOLD] = value.coerceIn(5f, 40f)
        }
    }
    
    suspend fun setZCrisisThreshold(value: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.Z_CRISIS_THRESHOLD] = value.coerceIn(60f, 95f)
        }
    }
    
    suspend fun setZStopThreshold(value: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.Z_STOP_THRESHOLD] = value.coerceIn(5f, 20f)
        }
    }
    
    suspend fun setVolumeCap(value: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.VOLUME_CAP] = value.coerceIn(0.3f, 0.95f)
        }
    }
    
    // --- Cooldown Seconds (v2.7) ---
    
    val cooldownSeconds: Flow<Int> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            prefs[Keys.COOLDOWN_SECONDS] ?: 45  // Default 45 seconds
        }
    
    suspend fun setCooldownSeconds(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.COOLDOWN_SECONDS] = value.coerceIn(10, 120)
        }
    }
    
    // --- Telemetry ---
    
    val telemetryEnabled: Flow<Boolean> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            prefs[Keys.TELEMETRY_ENABLED] ?: false  // Default OFF
        }
    
    suspend fun setTelemetryEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TELEMETRY_ENABLED] = enabled
        }
    }
    
    // --- Onboarding ---
    
    val onboardingComplete: Flow<Boolean> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            prefs[Keys.ONBOARDING_COMPLETE] ?: false
        }
    
    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETE] = complete
        }
    }
    
    // --- Morning Check-in Tracking ---
    
    val lastSessionPrompted: Flow<Long> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            prefs[Keys.LAST_SESSION_PROMPTED] ?: 0L
        }
    
    suspend fun setLastSessionPrompted(sessionId: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_SESSION_PROMPTED] = sessionId
        }
    }
    
    // --- Language ---
    
    val appLanguage: Flow<String> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            prefs[Keys.APP_LANGUAGE] ?: ""  // Empty = not set yet
        }
    
    suspend fun setAppLanguage(language: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE] = language
        }
    }
    
    // --- Audio Output Mode ---
    
    val audioOutputMode: Flow<String> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            prefs[Keys.AUDIO_OUTPUT_MODE] ?: "AUTO"  // AUTO, PHONE_SPEAKER, BLUETOOTH
        }
    
    suspend fun setAudioOutputMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUDIO_OUTPUT_MODE] = mode
        }
    }
    
    // --- Single Phone Mode ---
    
    val singlePhoneMode: Flow<Boolean> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            prefs[Keys.SINGLE_PHONE_MODE] ?: true  // Default ON (recommended)
        }
    
    suspend fun setSinglePhoneMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SINGLE_PHONE_MODE] = enabled
        }
    }
    
    // --- Clear All ---
    
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
