package com.soomi.baby.ui.screens.tonight

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soomi.baby.audio.Trend
import com.soomi.baby.domain.model.*
import com.soomi.baby.ui.components.*
import com.soomi.baby.ui.theme.*

/**
 * SOOMI v3.0 - Erweitertes Unruhewert-Meter mit Trend-Anzeige
 * 
 * Zeigt:
 * - Z-Wert (0-100) mit Farbzonen
 * - Trend-Indikator (dZ/dt)
 * - Prediction: "In ~15s über Schwelle"
 */
@Composable
fun UnruhewertMeterV3(
    value: Float,
    gradient: Float,
    trend: Trend,
    predictedTimeToThreshold: Float?,
    isSessionActive: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedValue by animateFloatAsState(
        targetValue = if (isSessionActive) value else 0f,
        animationSpec = tween(300),
        label = "unruhewert"
    )
    
    // Zone und Farbe
    val (zoneLabel, zoneColor) = when {
        animatedValue < 35f -> "Ruhig" to SoomiCalm
        animatedValue < 70f -> "Aktiv" to SoomiRising
        else -> "Unruhig" to SoomiCrisis
    }
    
    val animatedColor by animateColorAsState(
        targetValue = if (isSessionActive) zoneColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        animationSpec = tween(500),
        label = "zoneColor"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Label
        Text(
            text = "Unruhewert",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Hauptanzeige: Z-Wert + Trend
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Z-Wert
            Text(
                text = if (isSessionActive) animatedValue.toInt().toString() else "—",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Light,
                color = animatedColor
            )
            
            // Trend-Pfeil (v3.0)
            if (isSessionActive && kotlin.math.abs(gradient) > 0.5f) {
                Spacer(modifier = Modifier.width(8.dp))
                
                val trendIcon = when {
                    gradient > 3f -> Icons.Default.KeyboardDoubleArrowUp
                    gradient > 0.5f -> Icons.Default.KeyboardArrowUp
                    gradient < -3f -> Icons.Default.KeyboardDoubleArrowDown
                    gradient < -0.5f -> Icons.Default.KeyboardArrowDown
                    else -> Icons.Default.TrendingFlat
                }
                
                val trendColor = when {
                    gradient > 3f -> SoomiCrisis
                    gradient > 0.5f -> SoomiRising
                    gradient < -3f -> SoomiPrimary
                    gradient < -0.5f -> SoomiCalm
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                
                Icon(
                    imageVector = trendIcon,
                    contentDescription = "Trend",
                    tint = trendColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        // Zone-Label
        Text(
            text = if (isSessionActive) zoneLabel else "Bereit",
            style = MaterialTheme.typography.titleMedium,
            color = animatedColor.copy(alpha = 0.8f)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Farbiger Balken mit Zonen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Zonen-Hintergrund
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                        .background(SoomiCalm.copy(alpha = 0.2f))
                )
                Box(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                        .background(SoomiRising.copy(alpha = 0.2f))
                )
                Box(
                    modifier = Modifier
                        .weight(0.30f)
                        .fillMaxHeight()
                        .background(SoomiCrisis.copy(alpha = 0.2f))
                )
            }
            
            // Aktueller Wert
            if (isSessionActive && animatedValue > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = (animatedValue / 100f).coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(6.dp))
                        .background(animatedColor)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Zonen-Legende
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ZoneLegendItem("Ruhig", SoomiCalm, "0-35")
            ZoneLegendItem("Aktiv", SoomiRising, "35-70")
            ZoneLegendItem("Unruhig", SoomiCrisis, "70-100")
        }
        
        // v3.0: Prediction Info
        if (isSessionActive && predictedTimeToThreshold != null && predictedTimeToThreshold > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            
            PredictionBadge(
                secondsToThreshold = predictedTimeToThreshold,
                trend = trend
            )
        }
    }
}

@Composable
private fun ZoneLegendItem(
    label: String,
    color: Color,
    range: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = "$label ($range)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * v3.0: Prediction Badge
 * Zeigt voraussichtliche Zeit bis Intervention
 */
@Composable
private fun PredictionBadge(
    secondsToThreshold: Float,
    trend: Trend
) {
    val isRising = trend.isRising()
    
    if (!isRising || secondsToThreshold > 120) return
    
    val urgencyColor = when {
        secondsToThreshold < 10 -> SoomiCrisis
        secondsToThreshold < 30 -> SoomiRising
        else -> SoomiPredict
    }
    
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(urgencyColor.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            tint = urgencyColor,
            modifier = Modifier.size(16.dp)
        )
        
        Text(
            text = "~${secondsToThreshold.toInt()}s bis Intervention",
            style = MaterialTheme.typography.labelMedium,
            color = urgencyColor,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * v3.0: Erweiterte Status-Karte mit Learning Info
 */
@Composable
fun StatusCardV3(
    state: SoomiState,
    trend: Trend,
    gradient: Float,
    isPredictive: Boolean,
    isExploring: Boolean,
    cooldownRemaining: Int,
    soundType: SoundType,
    level: InterventionLevel,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status Header
            EnhancedStatusDisplay(
                state = state,
                trend = trend,
                gradient = gradient,
                isPredictive = isPredictive,
                isExploring = isExploring,
                cooldownRemaining = cooldownRemaining
            )
            
            // Sound & Level Info (nur wenn aktiv)
            if (state.isIntervening() || state == SoomiState.COOLDOWN) {
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Sound
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Sound",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = soundType.displayNameDe(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Level
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Level",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = level.displayNameDe(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Volume
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Lautstärke",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(level.volumeMultiplier * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * v3.0: Learning Insights Card
 * Zeigt Informationen über das Lernsystem
 */
@Composable
fun LearningInsightsCard(
    avgDeltaZ: Float,
    successRate: Float,
    predictiveRate: Float,
    totalInterventions: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = SoomiLearn.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = SoomiLearn,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Lernfortschritt",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SoomiLearn
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InsightMetric(
                    label = "Ø deltaZ",
                    value = "${"%.1f".format(avgDeltaZ)}",
                    isGood = avgDeltaZ > 25
                )
                InsightMetric(
                    label = "Erfolgsrate",
                    value = "${(successRate * 100).toInt()}%",
                    isGood = successRate > 0.7f
                )
                InsightMetric(
                    label = "Predictive",
                    value = "${(predictiveRate).toInt()}%",
                    isGood = predictiveRate > 30
                )
                InsightMetric(
                    label = "Gesamt",
                    value = "$totalInterventions",
                    isGood = null
                )
            }
        }
    }
}

@Composable
private fun InsightMetric(
    label: String,
    value: String,
    isGood: Boolean?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when (isGood) {
                true -> SoomiCalm
                false -> SoomiRising
                null -> MaterialTheme.colorScheme.onSurface
            }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
