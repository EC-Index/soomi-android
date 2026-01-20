package com.soomi.baby.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soomi.baby.data.repository.LearningRepository
import com.soomi.baby.data.repository.SettingsRepository
import com.soomi.baby.domain.model.ThresholdConfig
import com.soomi.baby.ui.components.SettingsSlider
import com.soomi.baby.ui.components.SettingsToggle
import com.soomi.baby.ui.theme.SoomiCalm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    LaunchedEffect(Unit) {
        viewModel.loadSettings()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Privacy Info Card
            PrivacyInfoCard()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // --- Sensitivity Section ---
            SectionHeader("Sensitivity")
            
            SettingsSlider(
                label = "Early Detection Threshold",
                value = uiState.config.zEarlyThreshold,
                onValueChange = { viewModel.setZEarlyThreshold(it) },
                valueRange = 5f..40f,
                valueLabel = "${uiState.config.zEarlyThreshold.toInt()}"
            )
            
            Text(
                text = "Lower = more sensitive, triggers sooner",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            SettingsSlider(
                label = "Crisis Threshold",
                value = uiState.config.zCrisisThreshold,
                onValueChange = { viewModel.setZCrisisThreshold(it) },
                valueRange = 60f..95f,
                valueLabel = "${uiState.config.zCrisisThreshold.toInt()}"
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // --- Audio Volume Section ---
            SectionHeader("Audio")
            
            SettingsSlider(
                label = "Maximum Volume",
                value = uiState.config.volumeCap,
                onValueChange = { viewModel.setVolumeCap(it) },
                valueRange = 0.3f..0.95f,
                valueLabel = "${(uiState.config.volumeCap * 100).toInt()}%"
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // --- Telemetry Section ---
            SectionHeader("Analytics")
            
            SettingsToggle(
                title = "Share Anonymous Usage Data",
                description = "Help improve SOOMI by sharing anonymous statistics (no audio data)",
                checked = uiState.telemetryEnabled,
                onCheckedChange = { viewModel.setTelemetryEnabled(it) }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // --- Data Section ---
            SectionHeader("Data")
            
            OutlinedButton(
                onClick = { viewModel.showResetDialog() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset Learning Data")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // Reset confirmation dialog
    if (uiState.showResetDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissResetDialog() },
            title = { Text("Reset Learning?") },
            text = { 
                Text("This will clear all learned intervention preferences. SOOMI will start fresh.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetLearning()
                        viewModel.dismissResetDialog()
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissResetDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun PrivacyInfoCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = SoomiCalm.copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = SoomiCalm,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Privacy First",
                    style = MaterialTheme.typography.titleSmall,
                    color = SoomiCalm
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "All audio processing happens on your device. Nothing is recorded, saved, or sent anywhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

data class SettingsUiState(
    val config: ThresholdConfig = ThresholdConfig(),
    val telemetryEnabled: Boolean = false,
    val showResetDialog: Boolean = false
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val learningRepository: LearningRepository
) {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState
    
    private val scope = CoroutineScope(Dispatchers.Main)
    
    fun loadSettings() {
        scope.launch {
            val config = settingsRepository.thresholdConfig.first()
            val telemetry = settingsRepository.telemetryEnabled.first()
            
            _uiState.value = SettingsUiState(
                config = config, 
                telemetryEnabled = telemetry
            )
        }
    }
    
    fun setZEarlyThreshold(value: Float) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(zEarlyThreshold = value)
        )
        scope.launch { settingsRepository.setZEarlyThreshold(value) }
    }
    
    fun setZCrisisThreshold(value: Float) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(zCrisisThreshold = value)
        )
        scope.launch { settingsRepository.setZCrisisThreshold(value) }
    }
    
    fun setVolumeCap(value: Float) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(volumeCap = value)
        )
        scope.launch { settingsRepository.setVolumeCap(value) }
    }
    
    fun setTelemetryEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(telemetryEnabled = enabled)
        scope.launch { settingsRepository.setTelemetryEnabled(enabled) }
    }
    
    fun showResetDialog() { 
        _uiState.value = _uiState.value.copy(showResetDialog = true) 
    }
    
    fun dismissResetDialog() { 
        _uiState.value = _uiState.value.copy(showResetDialog = false) 
    }
    
    fun resetLearning() { 
        scope.launch { learningRepository.resetLearning() } 
    }
}
