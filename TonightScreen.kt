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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soomi.baby.audio.AudioOutputEngine
import com.soomi.baby.domain.model.*
import com.soomi.baby.service.SoomiService
import com.soomi.baby.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * SOOMI 2.7 - Tonight Screen
 * 
 * v2.7 Änderungen:
 * - Unruhewert-Meter ersetzt Noise Level (0-100, Ruhig/Aktiv/Unruhig Zonen)
 * - Status-Feld zeigt nur Interventionsstatus und Cooldown-Zeit
 * - Nur ein Icon (Library) statt zwei
 * - Sound-Fadeout während Cooldown
 */
@Composable
fun TonightScreen(
    viewModel: TonightViewModel,
    onNavigateToProgress: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSoundLibrary: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    LaunchedEffect(Unit) {
        hasPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.startListening()
        }
    }

    DisposableEffect(Unit) {
        viewModel.bindService(context)
        onDispose {
            viewModel.unbindService(context)
            viewModel.stopListening()
        }
    }

    Scaffold(
        topBar = {
            TonightTopBar(
                onSoundLibraryClick = onNavigateToSoundLibrary,
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

            // === v2.7: UNRUHEWERT METER (ersetzt Noise Level) ===
            UnruhewertMeter(
                value = uiState.currentScore.value,
                isSessionActive = uiState.isRunning,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // === v2.7: STATUS FIELD (nur Intervention + Cooldown) ===
            InterventionStatusDisplay(
                state = uiState.state,
                cooldownRemaining = uiState.cooldownRemaining,
                isSessionActive = uiState.isRunning,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // === BASELINE SOUND SELECTOR ===
            Text(
                text = "Hintergrundklang",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            BaselineModeSelector(
                selectedMode = uiState.baselineMode,
                onModeSelected = { mode ->
                    viewModel.setBaselineModeWithSound(mode)
                },
                enabled = !uiState.isRunning,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // === SELECTED SOUND PROFILE ===
            SelectedProfileDisplay(
                profile = uiState.selectedProfile,
                onClick = onNavigateToSoundLibrary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            // === PERMISSION REQUEST IF NEEDED ===
            if (!hasPermission) {
                PermissionRequestCard(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

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

            PrivacyNoticeWithVersion()

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * v2.7: Unruhewert-Meter (ersetzt Noise Level Barometer)
 * 
 * Zeigt den Z-Wert von 0-100 mit Farbzonen:
 * - Ruhig (grün): 0-35
 * - Aktiv (gelb): 35-70
 * - Unruhig (rot): 70-100
 */
@Composable
fun UnruhewertMeter(
    value: Float,
    isSessionActive: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedValue by animateFloatAsState(
        targetValue = if (isSessionActive) value else 0f,
        animationSpec = tween(300),
        label = "unruhewert"
    )
    
    // Bestimme Zone und Farbe
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
        
        // Großer Wert in der Mitte
        Text(
            text = if (isSessionActive) animatedValue.toInt().toString() else "—",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Light,
            color = animatedColor
        )
        
        // Zone-Label
        Text(
            text = if (isSessionActive) zoneLabel else "Bereit",
            style = MaterialTheme.typography.titleMedium,
            color = animatedColor.copy(alpha = 0.8f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
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
                // Ruhig (0-35) = 35%
                Box(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                        .background(SoomiCalm.copy(alpha = 0.2f))
                )
                // Aktiv (35-70) = 35%
                Box(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                        .background(SoomiRising.copy(alpha = 0.2f))
                )
                // Unruhig (70-100) = 30%
                Box(
                    modifier = Modifier
                        .weight(0.30f)
                        .fillMaxHeight()
                        .background(SoomiCrisis.copy(alpha = 0.2f))
                )
            }
            
            // Aktueller Wert-Indikator
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
        
        // Zone-Labels unter dem Balken
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Ruhig",
                style = MaterialTheme.typography.labelSmall,
                color = SoomiCalm
            )
            Text(
                text = "Aktiv",
                style = MaterialTheme.typography.labelSmall,
                color = SoomiRising
            )
            Text(
                text = "Unruhig",
                style = MaterialTheme.typography.labelSmall,
                color = SoomiCrisis
            )
        }
    }
}

/**
 * v2.7: Status-Anzeige nur für Intervention und Cooldown
 * Zeigt "Bereit" wenn keine Intervention läuft
 */
@Composable
fun InterventionStatusDisplay(
    state: SoomiState,
    cooldownRemaining: Int,
    isSessionActive: Boolean,
    modifier: Modifier = Modifier
) {
    // Bestimme Status basierend auf State
    val (statusText, statusColor, showCountdown) = when {
        !isSessionActive -> Triple("Bereit", SoomiOnBackgroundMuted, false)
        state == SoomiState.SOOTHING -> Triple("Beruhigung aktiv", SoomiSoothing, false)
        state == SoomiState.COOLDOWN && cooldownRemaining > 0 -> {
            val minutes = cooldownRemaining / 60
            val seconds = cooldownRemaining % 60
            Triple(
                "Abklingzeit: ${String.format("%02d:%02d", minutes, seconds)}",
                SoomiCalm,
                true
            )
        }
        state == SoomiState.LISTENING || state == SoomiState.BASELINE -> Triple("Bereit", SoomiOnBackgroundMuted, false)
        else -> Triple("Bereit", SoomiOnBackgroundMuted, false)
    }

    val animatedColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = tween(300),
        label = "statusColor"
    )

    // Pulsieren bei aktiver Beruhigung
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == SoomiState.SOOTHING) 0.6f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        color = animatedColor.copy(alpha = 0.15f * alpha),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                state == SoomiState.SOOTHING -> {
                    Icon(
                        imageVector = Icons.Default.Waves,
                        contentDescription = null,
                        tint = animatedColor.copy(alpha = alpha),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                state == SoomiState.COOLDOWN && cooldownRemaining > 0 -> {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        tint = animatedColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = animatedColor.copy(alpha = alpha)
            )
        }
    }
}

/**
 * Baseline mode selector
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
                    .clickable(enabled = enabled) { onModeSelected(mode) }
            ) {
                Text(
                    text = when (mode) {
                        BaselineMode.OFF -> "Aus"
                        BaselineMode.GENTLE -> "Sanft"
                        BaselineMode.MEDIUM -> "Mittel"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (enabled) 1f else 0.5f
                        )
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp)
                )
            }
        }
    }
}

/**
 * Selected sound profile display
 */
@Composable
fun SelectedProfileDisplay(
    profile: SoundProfile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Klangprofil",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Klangbibliothek öffnen",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Main session button
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
    
    val buttonText = if (isRunning) "Beruhigung beenden" else "Beruhigung starten"

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .height(64.dp)
            .semantics { contentDescription = buttonText }
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = buttonText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Privacy notice with version
 */
@Composable
fun PrivacyNoticeWithVersion() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            text = "soomi Baby v2.7",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

/**
 * Permission request card
 */
@Composable
fun PermissionRequestCard(onRequestPermission: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                text = "soomi Baby braucht Mikrofon-Zugriff um Baby-Geräusche zu erkennen.\nAudio wird lokal verarbeitet und nie aufgezeichnet.",
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
 * v2.7: Top Bar mit nur einem Icon (Library) statt zwei
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TonightTopBar(
    onSoundLibraryClick: () -> Unit,
    onProgressClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NightsStay,
                    contentDescription = null,
                    tint = SoomiPrimary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "soomi Baby",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        actions = {
            // v2.7: Nur Library-Icon (erstes Musik-Icon), zweites entfernt
            IconButton(onClick = onSoundLibraryClick) {
                Icon(Icons.Default.LibraryMusic, contentDescription = "Klangbibliothek")
            }
            IconButton(onClick = onProgressClick) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Unruhetagebuch")
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
// ViewModel
// ============================================================================

data class TonightUiState(
    val isRunning: Boolean = false,
    val state: SoomiState = SoomiState.STOPPED,
    val currentScore: UnrestScore = UnrestScore(0f),
    val currentLevel: InterventionLevel = InterventionLevel.OFF,
    val baselineMode: BaselineMode = BaselineMode.GENTLE,
    val soundLevel: Float = 0f,
    val cooldownRemaining: Int = 0,
    val selectedProfile: SoundProfile = SoundProfile.DEFAULT
)

class TonightViewModel(
    private val settingsRepository: com.soomi.baby.data.repository.SettingsRepository
) {
    private val _uiState = MutableStateFlow(TonightUiState())
    val uiState: StateFlow<TonightUiState> = _uiState

    private var service: SoomiService? = null
    private var bound = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var baselineAudioOutput: AudioOutputEngine? = null
    private var audioRecord: AudioRecord? = null
    private var listeningJob: Job? = null

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
        
        scope.launch {
            settingsRepository.soundProfile.collect { profile ->
                _uiState.value = _uiState.value.copy(selectedProfile = profile)
            }
        }

        baselineAudioOutput = AudioOutputEngine()
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

    fun setBaselineModeWithSound(mode: BaselineMode) {
        _uiState.value = _uiState.value.copy(baselineMode = mode)
        
        try {
            when (mode) {
                BaselineMode.OFF -> baselineAudioOutput?.stop()
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

        scope.launch {
            settingsRepository.setBaselineMode(mode)
        }

        service?.setBaselineMode(mode)
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
            scope.launch {
                svc.cooldownRemaining.collect { remaining ->
                    _uiState.value = _uiState.value.copy(cooldownRemaining = remaining)
                }
            }
        }
    }

    fun startSession(context: Context) {
        try {
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
}
