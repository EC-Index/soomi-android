package com.soomi.baby.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * SOOMI Color Palette
 * 
 * Calming, soft colors appropriate for a baby app.
 * Night mode is default since app is used at night.
 */

// Primary - Soft lavender/purple (calming)
val SoomiPrimary = Color(0xFF8B7EC8)
val SoomiPrimaryDark = Color(0xFF6B5CA8)
val SoomiOnPrimary = Color(0xFFFFFFFF)

// Secondary - Soft teal (trust, calm)
val SoomiSecondary = Color(0xFF6BB8A8)
val SoomiSecondaryDark = Color(0xFF4B9888)

// Background - Very dark blue-gray (night mode friendly)
val SoomiBackground = Color(0xFF1A1B2E)
val SoomiSurface = Color(0xFF252640)
val SoomiSurfaceVariant = Color(0xFF2F3050)

// Status colors
val SoomiCalm = Color(0xFF6BB8A8)      // Green-teal for calm
val SoomiRising = Color(0xFFE8B86D)     // Soft amber for rising
val SoomiCrisis = Color(0xFFE87D7D)     // Soft red for crisis
val SoomiSoothing = Color(0xFF8B7EC8)   // Purple for active soothing

// Text
val SoomiOnBackground = Color(0xFFE8E8F0)
val SoomiOnBackgroundMuted = Color(0xFFA0A0B0)

// Night mode dark theme (default)
private val DarkColorScheme = darkColorScheme(
    primary = SoomiPrimary,
    onPrimary = SoomiOnPrimary,
    primaryContainer = SoomiPrimaryDark,
    secondary = SoomiSecondary,
    onSecondary = SoomiOnPrimary,
    secondaryContainer = SoomiSecondaryDark,
    background = SoomiBackground,
    onBackground = SoomiOnBackground,
    surface = SoomiSurface,
    onSurface = SoomiOnBackground,
    surfaceVariant = SoomiSurfaceVariant,
    onSurfaceVariant = SoomiOnBackgroundMuted
)

// Light theme (for daytime use)
private val LightColorScheme = lightColorScheme(
    primary = SoomiPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E0FF),
    secondary = SoomiSecondaryDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0F0E8),
    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF1A1B2E),
    surface = Color.White,
    onSurface = Color(0xFF1A1B2E),
    surfaceVariant = Color(0xFFF0F0F5),
    onSurfaceVariant = Color(0xFF606070)
)

@Composable
fun SoomiTheme(
    darkTheme: Boolean = true,  // Default to dark for night use
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
