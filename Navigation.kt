package com.soomi.baby.ui.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.soomi.baby.SoomiApplication
import com.soomi.baby.ui.screens.morning.MorningCheckInScreen
import com.soomi.baby.ui.screens.onboarding.OnboardingScreen
import com.soomi.baby.ui.screens.progress.ProgressScreen
import com.soomi.baby.ui.screens.progress.ProgressViewModel
import com.soomi.baby.ui.screens.settings.SettingsScreen
import com.soomi.baby.ui.screens.settings.SettingsViewModel
import com.soomi.baby.ui.screens.soundlibrary.SoundLibraryScreen
import com.soomi.baby.ui.screens.soundlibrary.SoundLibraryViewModel
import com.soomi.baby.ui.screens.tonight.TonightScreen
import com.soomi.baby.ui.screens.tonight.TonightViewModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object Routes {
    const val ONBOARDING = "onboarding"
    const val TONIGHT = "tonight"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val SOUND_LIBRARY = "sound_library"
    const val MORNING_CHECKIN = "morning_checkin/{sessionId}/{unrestEvents}/{soothingMinutes}"
    
    fun morningCheckIn(sessionId: Long, unrestEvents: Int, soothingMinutes: Int): String {
        return "morning_checkin/$sessionId/$unrestEvents/$soothingMinutes"
    }
}

@Composable
fun SoomiNavHost(
    app: SoomiApplication,
    navController: NavHostController = rememberNavController()
) {
    val startDestination = remember {
        val onboardingComplete = runBlocking { app.settingsRepository.onboardingComplete.first() }
        if (onboardingComplete) Routes.TONIGHT else Routes.ONBOARDING
    }
    
    val initialLanguage = remember {
        runBlocking { app.settingsRepository.appLanguage.first() }
    }
    
    // Shared audio output for test sounds across screens
    val testAudioOutput = remember { com.soomi.baby.audio.AudioOutputEngine() }
    
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                initialLanguage = initialLanguage,
                onComplete = { selectedLanguage ->
                    GlobalScope.launch { 
                        app.settingsRepository.setAppLanguage(selectedLanguage)
                        app.settingsRepository.setOnboardingComplete(true) 
                    }
                    navController.navigate(Routes.TONIGHT) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                }
            )
        }
        
        composable(Routes.TONIGHT) {
            val viewModel = remember { TonightViewModel(app.settingsRepository) }
            TonightScreen(
                viewModel = viewModel,
                onNavigateToProgress = { navController.navigate(Routes.PROGRESS) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToSoundLibrary = { navController.navigate(Routes.SOUND_LIBRARY) }
            )
        }
        
        composable(Routes.SOUND_LIBRARY) {
            val viewModel = remember { SoundLibraryViewModel(app.settingsRepository) }
            SoundLibraryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onTestSound = { profile ->
                    // Play test sound for 2 seconds at Level 1
                    testAudioOutput.stop()
                    testAudioOutput.startWithProfile(profile)
                    testAudioOutput.setVolume(0.3f) // Level 1
                },
                onStopSound = {
                    testAudioOutput.stop()
                }
            )
        }
        
        composable(Routes.PROGRESS) {
            val viewModel = remember { ProgressViewModel(app.sessionRepository) }
            ProgressScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNightClick = { }
            )
        }
        
        composable(Routes.SETTINGS) {
            val viewModel = remember { SettingsViewModel(app.settingsRepository, app.learningRepository) }
            SettingsScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
        }
        
        composable(Routes.MORNING_CHECKIN) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId")?.toLongOrNull() ?: 0L
            val unrestEvents = backStackEntry.arguments?.getString("unrestEvents")?.toIntOrNull() ?: 0
            val soothingMinutes = backStackEntry.arguments?.getString("soothingMinutes")?.toIntOrNull() ?: 0
            
            MorningCheckInScreen(
                sessionId = sessionId,
                unrestEvents = unrestEvents,
                soothingMinutes = soothingMinutes,
                onSubmit = { feedback ->
                    GlobalScope.launch { app.sessionRepository.saveFeedback(feedback) }
                    navController.popBackStack()
                },
                onSkip = { navController.popBackStack() }
            )
        }
    }
}
