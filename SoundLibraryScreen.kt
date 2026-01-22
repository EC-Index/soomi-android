package com.soomi.baby.ui.screens.soundlibrary

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soomi.baby.data.repository.SettingsRepository
import com.soomi.baby.domain.model.SoundProfile
import com.soomi.baby.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Sound Library Screen - v2.6
 * 
 * Allows users to select a sound profile from the library.
 * Opened from the music note icon on TonightScreen.
 * 
 * Features:
 * - Free profiles: selectable
 * - Pro profiles: locked with "PRO" badge
 * - Test Sound button for each profile
 * - Pro Coming Soon dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundLibraryScreen(
    viewModel: SoundLibraryViewModel,
    onNavigateBack: () -> Unit,
    onTestSound: (SoundProfile) -> Unit,
    onStopSound: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    LaunchedEffect(Unit) {
        viewModel.loadCurrentProfile()
    }
    
    // Pro Coming Soon Dialog
    if (uiState.showProDialog) {
        ProComingSoonDialog(
            onDismiss = { viewModel.dismissProDialog() }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Klangbibliothek",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Wähle einen Klang für Hintergrund und Beruhigung",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Free Profiles Section
            item {
                SectionHeader(
                    title = "Verfügbare Klänge",
                    icon = Icons.Default.MusicNote
                )
            }
            
            items(SoundProfile.freeProfiles()) { profile ->
                SoundProfileCard(
                    profile = profile,
                    isSelected = uiState.selectedProfile == profile,
                    isPlaying = uiState.playingProfile == profile,
                    onSelect = { viewModel.selectProfile(profile) },
                    onTestSound = { 
                        if (uiState.playingProfile == profile) {
                            onStopSound()
                            viewModel.stopTestSound()
                        } else {
                            onTestSound(profile)
                            viewModel.playTestSound(profile)
                        }
                    }
                )
            }
            
            // Pro Profiles Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = "Pro Klänge",
                    icon = Icons.Default.Stars,
                    badge = "BALD VERFÜGBAR"
                )
            }
            
            items(SoundProfile.proProfiles()) { profile ->
                SoundProfileCard(
                    profile = profile,
                    isSelected = false,
                    isPlaying = false,
                    isLocked = true,
                    onSelect = { viewModel.showProDialog() },
                    onTestSound = { viewModel.showProDialog() }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        badge?.let {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = SoomiRising.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = SoomiRising,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun SoundProfileCard(
    profile: SoundProfile,
    isSelected: Boolean,
    isPlaying: Boolean,
    isLocked: Boolean = false,
    onSelect: () -> Unit,
    onTestSound: () -> Unit
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isLocked -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        else -> Color.Transparent
    }
    
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        isLocked -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(borderColor, borderColor)
            )
        ) else null,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon / Selection indicator
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    )
            ) {
                if (isLocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Gesperrt",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                } else if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Ausgewählt",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Waves,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Profile info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isLocked) 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else 
                            MaterialTheme.colorScheme.onSurface
                    )
                    if (isLocked) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = SoomiSecondary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "PRO",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SoomiSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = profile.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLocked)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Test sound button
            if (!isLocked) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onTestSound,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                ) {
                    if (isPlaying) {
                        PlayingIndicator()
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Testton abspielen",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "playing")
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = 6f,
                targetValue = 14f,
                animationSpec = infiniteRepeatable(
                    animation = tween(250, delayMillis = index * 80),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$index"
            )
            
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun ProComingSoonDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Stars,
                contentDescription = null,
                tint = SoomiSecondary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "Pro kommt bald",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = "Zusätzliche Klänge werden in einer zukünftigen Version verfügbar sein.\n\nMöchtest du benachrichtigt werden?",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Warteliste beitreten")
            }
        }
    )
}

// ============================================================================
// ViewModel
// ============================================================================

data class SoundLibraryUiState(
    val selectedProfile: SoundProfile = SoundProfile.DEFAULT,
    val playingProfile: SoundProfile? = null,
    val showProDialog: Boolean = false,
    val isLoading: Boolean = false
)

class SoundLibraryViewModel(
    private val settingsRepository: SettingsRepository
) {
    private val _uiState = MutableStateFlow(SoundLibraryUiState())
    val uiState: StateFlow<SoundLibraryUiState> = _uiState
    
    private val scope = CoroutineScope(Dispatchers.Main)
    private var testSoundJob: kotlinx.coroutines.Job? = null
    
    fun loadCurrentProfile() {
        scope.launch {
            val profile = settingsRepository.soundProfile.first()
            _uiState.value = _uiState.value.copy(selectedProfile = profile)
        }
    }
    
    fun selectProfile(profile: SoundProfile) {
        if (profile.isPro && !SoundProfile.IS_PRO_ENABLED) {
            showProDialog()
            return
        }
        
        _uiState.value = _uiState.value.copy(selectedProfile = profile)
        scope.launch {
            settingsRepository.setSoundProfile(profile)
        }
    }
    
    fun playTestSound(profile: SoundProfile) {
        // Stop any currently playing test sound
        testSoundJob?.cancel()
        
        _uiState.value = _uiState.value.copy(playingProfile = profile)
        
        // Auto-stop after 2 seconds
        testSoundJob = scope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(playingProfile = null)
        }
    }
    
    fun stopTestSound() {
        testSoundJob?.cancel()
        _uiState.value = _uiState.value.copy(playingProfile = null)
    }
    
    fun showProDialog() {
        _uiState.value = _uiState.value.copy(showProDialog = true)
    }
    
    fun dismissProDialog() {
        _uiState.value = _uiState.value.copy(showProDialog = false)
    }
}
