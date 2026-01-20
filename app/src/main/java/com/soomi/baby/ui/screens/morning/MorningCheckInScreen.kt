package com.soomi.baby.ui.screens.morning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soomi.baby.domain.model.AnnoyingRating
import com.soomi.baby.domain.model.HelpedRating
import com.soomi.baby.domain.model.MorningFeedback
import com.soomi.baby.ui.theme.*

/**
 * Morning Check-in Screen
 * 
 * Quick 3-question feedback form shown after a session.
 * Helps SOOMI learn what works for this baby.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorningCheckInScreen(
    sessionId: Long,
    unrestEvents: Int,
    soothingMinutes: Int,
    onSubmit: (MorningFeedback) -> Unit,
    onSkip: () -> Unit
) {
    var helpedRating by remember { mutableStateOf<HelpedRating?>(null) }
    var annoyingRating by remember { mutableStateOf<AnnoyingRating?>(null) }
    var notes by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Good Morning!") },
                actions = {
                    TextButton(onClick = onSkip) {
                        Text("Skip")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Session summary
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Last Night",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            value = unrestEvents.toString(),
                            label = "Events",
                            icon = Icons.Default.Waves
                        )
                        StatItem(
                            value = "${soothingMinutes}m",
                            label = "Soothing",
                            icon = Icons.Default.Timer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Question 1: Did SOOMI help?
            QuestionSection(
                question = "Did SOOMI help your baby sleep better?",
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HelpedRating.entries.forEach { rating ->
                        RatingChip(
                            text = when (rating) {
                                HelpedRating.YES -> "Yes"
                                HelpedRating.A_BIT -> "A bit"
                                HelpedRating.NO -> "No"
                                HelpedRating.NOT_SURE -> "Not sure"
                            },
                            icon = when (rating) {
                                HelpedRating.YES -> Icons.Default.ThumbUp
                                HelpedRating.A_BIT -> Icons.Default.ThumbsUpDown
                                HelpedRating.NO -> Icons.Default.ThumbDown
                                HelpedRating.NOT_SURE -> Icons.Default.Help
                            },
                            selected = helpedRating == rating,
                            onClick = { helpedRating = rating },
                            color = when (rating) {
                                HelpedRating.YES -> SoomiCalm
                                HelpedRating.A_BIT -> SoomiRising
                                HelpedRating.NO -> SoomiCrisis
                                HelpedRating.NOT_SURE -> SoomiOnBackgroundMuted
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Question 2: Was it annoying?
            QuestionSection(
                question = "Was the sound ever annoying or too loud?",
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnnoyingRating.entries.forEach { rating ->
                        RatingChip(
                            text = when (rating) {
                                AnnoyingRating.NO -> "No"
                                AnnoyingRating.A_LITTLE -> "A little"
                                AnnoyingRating.YES -> "Yes"
                            },
                            icon = null,
                            selected = annoyingRating == rating,
                            onClick = { annoyingRating = rating },
                            color = when (rating) {
                                AnnoyingRating.NO -> SoomiCalm
                                AnnoyingRating.A_LITTLE -> SoomiRising
                                AnnoyingRating.YES -> SoomiCrisis
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Question 3: Notes (optional)
            QuestionSection(
                question = "Anything else? (optional)",
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Any notes for yourself...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Submit button
            Button(
                onClick = {
                    onSubmit(
                        MorningFeedback(
                            sessionId = sessionId,
                            helpedRating = helpedRating ?: HelpedRating.NOT_SURE,
                            annoyingRating = annoyingRating ?: AnnoyingRating.NO,
                            notes = notes.takeIf { it.isNotBlank() }
                        )
                    )
                },
                enabled = helpedRating != null && annoyingRating != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Submit Feedback", style = MaterialTheme.typography.titleMedium)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    icon: ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuestionSection(
    question: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = question,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
private fun RatingChip(
    text: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (selected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = if (selected) {
            ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(color, color)
                )
            )
        } else null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
