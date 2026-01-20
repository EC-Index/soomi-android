package com.soomi.baby.ui.screens.tonight

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soomi.baby.audio.AudioOutputEngine
import com.soomi.baby.domain.model.*
import com.soomi.baby.service.SoomiService
import com.soomi.baby.ui.components.*
import com.soomi.baby.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * SOOMI 2.0 - Tonight Screen
 * 
 * Minimale Reize, maximale Wirkung.
 * 
 * Features:
 * - Z-Wert Anzeige (Calm/Active/Panic)
 * - Lautstärke-Anzeige (Quiet/Loud/Crying)
 * - Baseline Sound startet sofort bei Auswahl
 * - Ein großer Start/Stop Session Button
 */
@Composable
fun TonightScreen(
    viewModel: TonightViewModel,
    onNavigateToProgress: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Sound test dialog state
    var showSoundDialog by remember { mutableStateOf(false) }

    // Permission handling
    var hasPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            viewModel.startListening()
        }
    }

    // Check permission on load
    LaunchedEffect(Unit) {
        hasPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.startListening()
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        viewModel.bindService(context)
        onDispose {
            viewModel.unbindService(context)
            viewModel.stopListening()
        }
    }

    // Sound test dialog
    if (showSoundDialog) {
        SoundTestDialog(
            onDismiss = { 
                viewModel.stopTestSound()
                showSoundDialog = false 
            },
            onPlaySound = { soundType, volume ->
                viewModel.playTestSound(soundType, volume)
            },
            onStopSound = {
                viewModel.stopTestSound()
            }
        )
    }

    Scaffold(
        topBar = {
            TonightTopBar(
                onSoundTestClick = { showSoundDialog = true },
                onProgressClick = onNavigateToProgress,
                onSettingsClick = onNavigateToSettings
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
            Spacer(modifier = Modifier.height(24.dp))

            // === STATUS 1: Z-WERT / UNRUHE-LEVEL ===
            UnrestStatusDisplay(
                state = uiState.state,
                score = uiState.currentScore,
                isSessionActive = uiState.isRunning,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // === STATUS 2: LAUTSTÄRKE IM RAUM ===
            if (hasPermission) {
                SoundLevelBarometer(
                    level = uiState.soundLevel,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Request permission card
                PermissionRequestCard(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // === BASELINE SOUND SELECTOR ===
            Text(
                text = "Baseline Sound",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            BaselineModeSelector2(
                selectedMode = uiState.baselineMode,
                onModeSelected = { mode ->
                    viewModel.setBaselineModeWithSound(mode)
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            // === MAIN ACTION BUTTON ===
            SessionButton(
                isRunning = uiState.isRunning,
                hasPermission = hasPermission,
                onClick = {
                    if (uiState.isRunning) {
                        viewModel.stopSession(context)
                    } else {
                        if (hasPermission) {
                            viewModel.startSession(context)
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Privacy notice + Version
            PrivacyNoticeWithVersion()

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Z-Wert / Unruhe Status Display
 * Zeigt: Calm, Active, oder Panic (rot)
 */
@Composable
fun UnrestStatusDisplay(
    state: SoomiState,
    score: UnrestScore,
    isSessionActive: Boolean,
    modifier: Modifier = Modifier
) {
    // Bestimme Status basierend auf Z-Wert
    val (statusText, statusColor, shouldPulse) = when {
        !isSessionActive -> Triple("Bereit", SoomiOnBackgroundMuted, false)
        score.value < 20f -> Triple("Calm", SoomiCalm, false)
        score.value < 60f -> Triple("Active", SoomiRising, false)
        else -> Triple("Panic", SoomiCrisis, true)
    }

    val animatedColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = tween(500),
        label = "statusColor"
    )

    // Pulsing animation for Panic state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (shouldPulse) 0.6f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Großes Status-Oval
        Surface(
            color = animatedColor.copy(alpha = 0.15f * alpha),
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = animatedColor.copy(alpha = alpha)
                    )
                    if (isSessionActive) {
                        Text(
                            text = "Z: ${score.value.toInt()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = animatedColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Lautstärke-Anzeige im Raum
 */
@Composable
fun SoundLevelBarometer(
    level: Float,
    modifier: Modifier = Modifier
) {
    val animatedLevel by animateFloatAsState(
        targetValue = level,
        animationSpec = tween(100),
        label = "level"
    )

    val barColor = when {
        level < 0.3f -> SoomiCalm
        level < 0.6f -> SoomiRising
        level < 0.8f -> SoomiCrisis.copy(alpha = 0.7f)
        else -> SoomiCrisis
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Barometer bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedLevel.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(16.dp))
                    .background(barColor)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Scale labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Quiet",
                style = MaterialTheme.typography.bodySmall,
                color = SoomiCalm
            )
            Text(
                text = "Loud", 
                style = MaterialTheme.typography.bodySmall,
                color = SoomiRising
            )
            Text(
                text = "Crying",
                style = MaterialTheme.typography.bodySmall,
                color = SoomiCrisis
            )
        }
    }
}

/**
 * Baseline Mode Selector - startet Sound sofort bei Auswahl
 */
@Composable
fun BaselineModeSelector2(
    selectedMode: BaselineMode,
    onModeSelected: (BaselineMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BaselineMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onModeSelected(mode) }
            ) {
                Text(
                    text = when (mode) {
                        BaselineMode.OFF -> "Off"
                        BaselineMode.GENTLE -> "Gentle"
                        BaselineMode.MEDIUM -> "Medium"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp)
                )
            }
        }
    }
}

/**
 * Großer Session Button
 */
@Composable
fun SessionButton(
    isRunning: Boolean,
    hasPermission: Boolean,
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
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.height(64.dp)
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (isRunning) "Stop Session" else "Start Session",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Privacy notice with version number
 */
@Composable
fun PrivacyNoticeWithVersion() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = SoomiCalm.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Läuft lokal. Keine Aufnahmen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "SOOMI v2.1",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

/**
 * Permission request card
 */
@Composable
fun PermissionRequestCard(
    onRequestPermission: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Mikrofon-Zugriff benötigt",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SOOMI braucht Mikrofon-Zugriff um Baby-Geräusche zu erkennen.\nAudio wird lokal verarbeitet und nie aufgezeichnet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequestPermission) {
                Text("Mikrofon erlauben")
            }
        }
    }
}

/**
 * Sound Test Dialog
 */
@Composable
fun SoundTestDialog(
    onDismiss: () -> Unit,
    onPlaySound: (SoundType, Float) -> Unit,
    onStopSound: () -> Unit
) {
    var currentlyPlaying by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Sound Test")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Tippe zum Abspielen. Nochmal tippen zum Stoppen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Baseline Sounds",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                SoundTestItem(
                    name = "Gentle",
                    description = "Leises Hintergrundrauschen",
                    isPlaying = currentlyPlaying == "gentle",
                    onClick = {
                        if (currentlyPlaying == "gentle") {
                            onStopSound()
                            currentlyPlaying = null
                        } else {
                            onPlaySound(SoundType.BROWN_NOISE, 0.3f)
                            currentlyPlaying = "gentle"
                        }
                    }
                )

                SoundTestItem(
                    name = "Medium",
                    description = "Mittleres Hintergrundrauschen",
                    isPlaying = currentlyPlaying == "medium",
                    onClick = {
                        if (currentlyPlaying == "medium") {
                            onStopSound()
                            currentlyPlaying = null
                        } else {
                            onPlaySound(SoundType.BROWN_NOISE, 0.5f)
                            currentlyPlaying = "medium"
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Beruhigende Sounds",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                SoundTestItem(
                    name = "Brown Noise",
                    description = "Tief, rumpelnd - am besten für Babys",
                    isPlaying = currentlyPlaying == "brown",
                    onClick = {
                        if (currentlyPlaying == "brown") {
                            onStopSound()
                            currentlyPlaying = null
                        } else {
                            onPlaySound(SoundType.BROWN_NOISE, 0.6f)
                            currentlyPlaying = "brown"
                        }
                    }
                )

                SoundTestItem(
                    name = "Pink Noise",
                    description = "Ausgewogen, natürlich",
                    isPlaying = currentlyPlaying == "pink",
                    onClick = {
                        if (currentlyPlaying == "pink") {
                            onStopSound()
                            currentlyPlaying = null
                        } else {
                            onPlaySound(SoundType.PINK_NOISE, 0.6f)
                            currentlyPlaying = "pink"
                        }
                    }
                )

                SoundTestItem(
                    name = "Shush Pulse",
                    description = "Rhythmisches Shush-Muster",
                    isPlaying = currentlyPlaying == "shush",
                    onClick = {
                        if (currentlyPlaying == "shush") {
                            onStopSound()
                            currentlyPlaying = null
                        } else {
                            onPlaySound(SoundType.SHUSH_PULSE, 0.6f)
                            currentlyPlaying = "shush"
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen")
            }
        }
    )
}

/**
 * Sound Test Item
 */
@Composable
fun SoundTestItem(
    name: String,
    description: String,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isPlaying) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isPlaying) {
                PlayingIndicator()
            }
        }
    }
}

/**
 * Playing indicator animation
 */
@Composable
fun PlayingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "playing")
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = 8f,
                targetValue = 20f,
                animationSpec = infiniteRepeatable(
                    animation = tween(300, delayMillis = index * 100),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$index"
            )
            
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TonightTopBar(
    onSoundTestClick: () -> Unit,
    onProgressClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "SOOMI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            IconButton(onClick = onSoundTestClick) {
                Icon(Icons.Default.MusicNote, contentDescription = "Sound Test")
            }
            IconButton(onClick = onProgressClick) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Fortschritt")
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

// ============================================================================
// UI State & ViewModel
// ============================================================================

data class TonightUiState(
    val isRunning: Boolean = false,
    val state: SoomiState = SoomiState.IDLE,
    val currentScore: UnrestScore = UnrestScore(0f),
    val currentLevel: InterventionLevel = InterventionLevel.OFF,
    val baselineMode: BaselineMode = BaselineMode.GENTLE,
    val showPermissionDialog: Boolean = false,
    val soundLevel: Float = 0f,
    val isCalmingActive: Boolean = false
)

class TonightViewModel(
    private val settingsRepository: com.soomi.baby.data.repository.SettingsRepository
) {
    private val _uiState = MutableStateFlow(TonightUiState())
    val uiState: StateFlow<TonightUiState> = _uiState

    private var service: SoomiService? = null
    private var bound = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Audio für Baseline Sound
    private var baselineAudioOutput: AudioOutputEngine? = null
    private var audioRecord: AudioRecord? = null
    private var listeningJob: Job? = null

    // Audio für Sound Test
    private var testAudioOutput: AudioOutputEngine? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as SoomiService.LocalBinder
            service = localBinder.getService()
            bound = true
            observeService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    init {
        scope.launch {
            settingsRepository.baselineMode.collect { mode ->
                _uiState.value = _uiState.value.copy(baselineMode = mode)
            }
        }

        baselineAudioOutput = AudioOutputEngine()
        testAudioOutput = AudioOutputEngine()
    }

    fun bindService(context: Context) {
        try {
            Intent(context, SoomiService::class.java).also { intent ->
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unbindService(context: Context) {
        try {
            if (bound) {
                context.unbindService(connection)
                bound = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startListening() {
        if (listeningJob?.isActive == true) return

        listeningJob = scope.launch(Dispatchers.Default) {
            try {
                val sampleRate = 44100
                val bufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                audioRecord?.startRecording()
                val buffer = ShortArray(bufferSize / 2)

                while (isActive) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += buffer[i].toDouble() * buffer[i].toDouble()
                        }
                        val rms = kotlin.math.sqrt(sum / read)
                        val normalized = (rms / 10000.0).coerceIn(0.0, 1.0).toFloat()

                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(soundLevel = normalized)
                        }
                    }
                    delay(50)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
        audioRecord?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        audioRecord = null
    }

    /**
     * Baseline Sound Auswahl - startet Sound SOFORT
     */
    fun setBaselineModeWithSound(mode: BaselineMode) {
        _uiState.value = _uiState.value.copy(baselineMode = mode)
        
        // Sound sofort starten/stoppen
        try {
            when (mode) {
                BaselineMode.OFF -> {
                    baselineAudioOutput?.stop()
                }
                BaselineMode.GENTLE -> {
                    baselineAudioOutput?.apply {
                        stop()
                        start(SoundType.BROWN_NOISE)
                        setVolume(0.3f)
                    }
                }
                BaselineMode.MEDIUM -> {
                    baselineAudioOutput?.apply {
                        stop()
                        start(SoundType.BROWN_NOISE)
                        setVolume(0.5f)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Settings speichern
        scope.launch {
            settingsRepository.setBaselineMode(mode)
        }

        // Service informieren falls aktiv
        service?.setBaselineMode(mode)
    }

    fun playTestSound(soundType: SoundType, volume: Float) {
        try {
            testAudioOutput?.apply {
                stop()
                start(soundType)
                setVolume(volume)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopTestSound() {
        try {
            testAudioOutput?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun observeService() {
        service?.let { svc ->
            scope.launch {
                svc.isRunning.collect { running ->
                    _uiState.value = _uiState.value.copy(isRunning = running)
                }
            }
            scope.launch {
                svc.sessionState.collect { state ->
                    _uiState.value = _uiState.value.copy(state = state)
                }
            }
            scope.launch {
                svc.currentScore.collect { score ->
                    _uiState.value = _uiState.value.copy(currentScore = score)
                }
            }
            scope.launch {
                svc.currentLevel.collect { level ->
                    _uiState.value = _uiState.value.copy(currentLevel = level)
                }
            }
        }
    }

    fun startSession(context: Context) {
        try {
            // Baseline Audio stoppen - Service übernimmt
            baselineAudioOutput?.stop()
            SoomiService.startService(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopSession(context: Context) {
        try {
            if (_uiState.value.isRunning) {
                SoomiService.stopService(context)
            }
            // Baseline Audio auch stoppen
            baselineAudioOutput?.stop()
            _uiState.value = _uiState.value.copy(baselineMode = BaselineMode.OFF)
            scope.launch {
                settingsRepository.setBaselineMode(BaselineMode.OFF)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setBaselineMode(mode: BaselineMode) {
        setBaselineModeWithSound(mode)
    }

    fun dismissPermissionDialog() {
        _uiState.value = _uiState.value.copy(showPermissionDialog = false)
    }
}
