package com.soomi.baby.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SOOMI v3.0 - Erweiterte Farbpalette
 */

// === Primäre Markenfarben ===
val SoomiPrimary = Color(0xFF6B5CE7)        // Lila - Hauptfarbe
val SoomiSecondary = Color(0xFF9C8AFF)      // Helles Lila
val SoomiPrimaryDark = Color(0xFF4A3DB8)    // Dunkles Lila

// === Status-Farben ===
val SoomiCalm = Color(0xFF4CAF50)           // Grün - Ruhig (Z 0-35)
val SoomiRising = Color(0xFFFFC107)         // Gelb/Amber - Aktiv (Z 35-70)
val SoomiCrisis = Color(0xFFF44336)         // Rot - Unruhig (Z 70-100)

// === v3.0 Neue Farben ===
val SoomiPredict = Color(0xFF00BCD4)        // Cyan - Predictive State
val SoomiCooldown = Color(0xFF2196F3)       // Blau - Cooldown
val SoomiLearn = Color(0xFFFF9800)          // Orange - Learning/Exploration
val SoomiSuccess = Color(0xFF8BC34A)        // Hellgrün - Erfolg

// === Trend-Farben ===
val SoomiTrendUp = Color(0xFFFF5722)        // Deep Orange - Schnell steigend
val SoomiTrendDown = Color(0xFF00E676)      // Grün - Schnell fallend
val SoomiTrendStable = Color(0xFF9E9E9E)    // Grau - Stabil

// === Hintergrund-Farben ===
val SoomiBackground = Color(0xFF121212)
val SoomiSurface = Color(0xFF1E1E1E)
val SoomiSurfaceVariant = Color(0xFF2D2D2D)

// === Text-Farben ===
val SoomiOnPrimary = Color(0xFFFFFFFF)
val SoomiOnSurface = Color(0xFFE0E0E0)
val SoomiOnSurfaceVariant = Color(0xFF9E9E9E)

// === Gradient-Farben für Charts ===
val SoomiGradientStart = Color(0xFF6B5CE7)
val SoomiGradientEnd = Color(0xFF00BCD4)

/**
 * Utility: Farbe für Z-Wert Zone
 */
fun getZoneColor(zValue: Float): Color {
    return when {
        zValue < 35f -> SoomiCalm
        zValue < 70f -> SoomiRising
        else -> SoomiCrisis
    }
}

/**
 * Utility: Farbe für Gradient/Trend
 */
fun getTrendColor(gradient: Float): Color {
    return when {
        gradient > 5f -> SoomiCrisis
        gradient > 2f -> SoomiRising
        gradient > 0.5f -> SoomiRising.copy(alpha = 0.7f)
        gradient < -5f -> SoomiPrimary
        gradient < -2f -> SoomiCalm
        gradient < -0.5f -> SoomiCalm.copy(alpha = 0.7f)
        else -> SoomiTrendStable
    }
}

/**
 * Utility: Farbe für State
 */
fun getStateColor(state: com.soomi.baby.domain.model.SoomiState): Color {
    return when (state) {
        com.soomi.baby.domain.model.SoomiState.STOPPED -> SoomiOnSurfaceVariant
        com.soomi.baby.domain.model.SoomiState.IDLE -> SoomiOnSurfaceVariant
        com.soomi.baby.domain.model.SoomiState.BASELINE -> SoomiCalm
        com.soomi.baby.domain.model.SoomiState.LISTENING -> SoomiCalm
        com.soomi.baby.domain.model.SoomiState.PREDICTIVE -> SoomiPredict
        com.soomi.baby.domain.model.SoomiState.SOOTHING -> SoomiCrisis
        com.soomi.baby.domain.model.SoomiState.COOLDOWN -> SoomiCooldown
    }
}

/**
 * Utility: Farbe für Effectiveness Score
 */
fun getEffectivenessColor(score: Float): Color {
    return when {
        score > 30f -> SoomiSuccess
        score > 15f -> SoomiCalm
        score > 0f -> SoomiRising
        else -> SoomiCrisis
    }
}
