package com.soomi.baby.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soomi.baby.domain.model.BaselineMode
import com.soomi.baby.domain.model.SoomiState
import com.soomi.baby.domain.model.UnrestScore
import com.soomi.baby.ui.theme.*

/**
 * Large unrest score display with animated color
 */
@Composable
fun UnrestScoreDisplay(
    score: UnrestScore,
    modifier: Modifier = Modifier
) {
    val scoreColor by animateColorAsState(
        targetValue = when {
            score.value < 10 -> SoomiCalm
            score.value < 30 -> SoomiRising.copy(alpha = 0.7f)
            score.value < 60 -> SoomiRising
            score.value < 80 -> SoomiCrisis.copy(alpha = 0.8f)
            else -> SoomiCrisis
        },
        animationSpec = tween(500),
        label = "scoreColor"
    )
    
    val trendIcon = when (score.trend) {
        UnrestScore.Trend.RISING -> Icons.Default.TrendingUp
        UnrestScore.Trend.FALLING -> Icons.Default.TrendingDown
        UnrestScore.Trend.STABLE -> Icons.Default.TrendingFlat
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // Score number
        Text(
            text = score.value.toInt().toString(),
            style = MaterialTheme.typography.displayLarge,
            color = scoreColor,
            fontWeight = FontWeight.Light
        )
        
        // Trend indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = trendIcon,
                contentDescription = "Trend",
                tint = scoreColor.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (score.trend) {
                    UnrestScore.Trend.RISING -> "Rising"
                    UnrestScore.Trend.FALLING -> "Calming"
                    UnrestScore.Trend.STABLE -> "Stable"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scoreColor.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Status label with icon and pulsing animation for active states
 */
@Composable
fun StatusDisplay(
    state: SoomiState,
    modifier: Modifier = Modifier
) {
    val (label, color, icon, shouldPulse) = when (state) {
        SoomiState.STOPPED -> StatusInfo("Stopped", SoomiOnBackgroundMuted, Icons.Default.Stop, false)
        SoomiState.LISTENING -> StatusInfo("Listening", SoomiCalm, Icons.Default.Hearing, false)
        SoomiState.SOOTHING -> StatusInfo("Soothing", SoomiSoothing, Icons.Default.Waves, true)
        SoomiState.COOLDOWN -> StatusInfo("Cooldown", SoomiRising, Icons.Default.HourglassEmpty, false)
        SoomiState.BASELINE -> StatusInfo("Baseline", SoomiCalm, Icons.Default.GraphicEq, false)
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (shouldPulse) 0.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    Surface(
        color = color.copy(alpha = 0.15f * alpha),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color.copy(alpha = alpha),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = color.copy(alpha = alpha)
            )
        }
    }
}

private data class StatusInfo(
    val label: String,
    val color: Color,
    val icon: ImageVector,
    val shouldPulse: Boolean
)

/**
 * Baseline mode selector (OFF / Gentle / Medium)
 */
@Composable
fun BaselineModeSelector(
    selectedMode: BaselineMode,
    onModeSelected: (BaselineMode) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BaselineMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = enabled) { onModeSelected(mode) }
            ) {
                Text(
                    text = when (mode) {
                        BaselineMode.OFF -> "Off"
                        BaselineMode.GENTLE -> "Gentle"
                        BaselineMode.MEDIUM -> "Medium"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
                )
            }
        }
    }
}

/**
 * Primary action button (Start/Stop session)
 */
@Composable
fun PrimaryActionButton(
    isRunning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isRunning) SoomiCrisis else SoomiCalm,
        animationSpec = tween(300),
        label = "buttonColor"
    )
    
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.height(56.dp)
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isRunning) "Stop Session" else "Start Session",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/**
 * Secondary action buttons (Calm Now, Stop)
 */
@Composable
fun SecondaryActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.secondary,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.linearGradient(listOf(color.copy(alpha = 0.5f), color.copy(alpha = 0.5f)))
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Privacy notice text
 */
@Composable
fun PrivacyNotice(
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = SoomiCalm.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Runs locally. No recordings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Night summary card for progress view
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightSummaryCard(
    date: String,
    unrestEvents: Int,
    soothingMinutes: Int,
    helpedRating: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = date,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$unrestEvents events • ${soothingMinutes}min soothing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Feedback indicator
            helpedRating?.let {
                val (icon, color) = when (it) {
                    "YES" -> Icons.Default.ThumbUp to SoomiCalm
                    "A_BIT" -> Icons.Default.ThumbsUpDown to SoomiRising
                    "NO" -> Icons.Default.ThumbDown to SoomiCrisis
                    else -> Icons.Default.Help to SoomiOnBackgroundMuted
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "Feedback: $it",
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Settings slider with label
 */
@Composable
fun SettingsSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
