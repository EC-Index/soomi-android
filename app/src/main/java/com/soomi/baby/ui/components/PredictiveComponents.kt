package com.soomi.baby.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soomi.baby.audio.Trend
import com.soomi.baby.domain.model.SoomiState
import com.soomi.baby.ui.theme.*

/**
 * SOOMI v3.0 - Trend Indicator
 * 
 * Zeigt den aktuellen dZ/dt Trend visuell an.
 * Pfeil nach oben/unten mit Farbe je nach Intensität.
 */
@Composable
fun TrendIndicator(
    trend: Trend,
    gradient: Float,
    modifier: Modifier = Modifier
) {
    val (icon, color, description) = when (trend) {
        Trend.RISING_FAST -> Triple(Icons.Default.KeyboardDoubleArrowUp, SoomiCrisis, "Schnell steigend")
        Trend.RISING -> Triple(Icons.Default.KeyboardArrowUp, SoomiRising, "Steigend")
        Trend.RISING_SLOW -> Triple(Icons.Default.TrendingUp, SoomiRising.copy(alpha = 0.7f), "Leicht steigend")
        Trend.STABLE -> Triple(Icons.Default.TrendingFlat, SoomiCalm, "Stabil")
        Trend.FALLING_SLOW -> Triple(Icons.Default.TrendingDown, SoomiCalm.copy(alpha = 0.7f), "Leicht fallend")
        Trend.FALLING -> Triple(Icons.Default.KeyboardArrowDown, SoomiCalm, "Fallend")
        Trend.FALLING_FAST -> Triple(Icons.Default.KeyboardDoubleArrowDown, SoomiPrimary, "Schnell fallend")
    }
    
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(300),
        label = "trendColor"
    )
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(animatedColor.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = animatedColor,
            modifier = Modifier.size(20.dp)
        )
        
        Column {
            Text(
                text = description,
                style = MaterialTheme.typography.labelMedium,
                color = animatedColor,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${if (gradient >= 0) "+" else ""}${"%.1f".format(gradient)}/s",
                style = MaterialTheme.typography.labelSmall,
                color = animatedColor.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * SOOMI v3.0 - Predictive Status Badge
 * 
 * Zeigt an wenn PREDICTIVE State aktiv ist.
 * Pulsierendes Badge mit Info.
 */
@Composable
fun PredictiveStatusBadge(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isActive) return
    
    val infiniteTransition = rememberInfiniteTransition(label = "predictivePulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SoomiPredict.copy(alpha = alpha * 0.2f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Pulsierender Punkt
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(SoomiPredict.copy(alpha = alpha))
        )
        
        Text(
            text = "Früherkennung aktiv",
            style = MaterialTheme.typography.labelMedium,
            color = SoomiPredict,
            fontWeight = FontWeight.SemiBold
        )
        
        Icon(
            imageVector = Icons.Default.Visibility,
            contentDescription = null,
            tint = SoomiPredict,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * SOOMI v3.0 - Erweiterte Status-Anzeige
 * 
 * Zeigt State, Trend und Prediction zusammen.
 */
@Composable
fun EnhancedStatusDisplay(
    state: SoomiState,
    trend: Trend,
    gradient: Float,
    isPredictive: Boolean,
    isExploring: Boolean,
    cooldownRemaining: Int,
    modifier: Modifier = Modifier
) {
    val stateColor = when (state) {
        SoomiState.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
        SoomiState.LISTENING, SoomiState.IDLE, SoomiState.BASELINE -> SoomiCalm
        SoomiState.PREDICTIVE -> SoomiPredict
        SoomiState.SOOTHING -> SoomiCrisis
        SoomiState.COOLDOWN -> SoomiCooldown
    }
    
    val animatedColor by animateColorAsState(
        targetValue = stateColor,
        animationSpec = tween(300),
        label = "stateColor"
    )
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Hauptstatus
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status-Punkt (pulsierend wenn aktiv)
            if (state.isActive()) {
                val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )
                
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(animatedColor.copy(alpha = pulseAlpha))
                )
            }
            
            Text(
                text = state.displayNameDe(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = animatedColor
            )
            
            // Cooldown Timer
            if (state == SoomiState.COOLDOWN && cooldownRemaining > 0) {
                Text(
                    text = "(${cooldownRemaining}s)",
                    style = MaterialTheme.typography.titleMedium,
                    color = animatedColor.copy(alpha = 0.7f)
                )
            }
        }
        
        // Predictive Badge
        PredictiveStatusBadge(isActive = isPredictive)
        
        // Exploration Badge
        if (isExploring) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoomiLearn.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = null,
                    tint = SoomiLearn,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Exploration",
                    style = MaterialTheme.typography.labelSmall,
                    color = SoomiLearn
                )
            }
        }
        
        // Trend Indicator (nur wenn Session aktiv)
        if (state.isActive() && state != SoomiState.STOPPED) {
            TrendIndicator(
                trend = trend,
                gradient = gradient
            )
        }
    }
}

/**
 * SOOMI v3.0 - Gradient Visualizer
 * 
 * Zeigt den dZ/dt als visuellen Balken an.
 */
@Composable
fun GradientVisualizer(
    gradient: Float,
    modifier: Modifier = Modifier
) {
    // Gradient normalisieren (-10 bis +10 -> -1 bis +1)
    val normalizedGradient = (gradient / 10f).coerceIn(-1f, 1f)
    
    val color = when {
        normalizedGradient > 0.5f -> SoomiCrisis
        normalizedGradient > 0.2f -> SoomiRising
        normalizedGradient < -0.5f -> SoomiPrimary
        normalizedGradient < -0.2f -> SoomiCalm
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(200),
        label = "gradientColor"
    )
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Trend",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Balken
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Mittelmarkierung
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.outline)
            )
            
            // Gradient-Anzeige
            val barWidth = kotlin.math.abs(normalizedGradient) * 0.5f
            val alignment = if (normalizedGradient >= 0) Alignment.CenterStart else Alignment.CenterEnd
            val offset = if (normalizedGradient >= 0) 0.5f else 0.5f - barWidth
            
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = barWidth)
                    .fillMaxHeight()
                    .align(alignment)
                    .padding(start = if (normalizedGradient >= 0) (0.5f * 200).dp else 0.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(animatedColor)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Wert
        Text(
            text = "${if (gradient >= 0) "+" else ""}${"%.1f".format(gradient)}/s",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = animatedColor
        )
    }
}

// Zusätzliche Farben für v3.0
val SoomiPredict = Color(0xFF00BCD4)  // Cyan für Predictive
val SoomiLearn = Color(0xFFFF9800)    // Orange für Learning/Exploration
val SoomiCooldown = Color(0xFF2196F3) // Blau für Cooldown
