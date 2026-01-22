package com.soomi.baby.ui.screens.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soomi.baby.data.repository.SessionRepository
import com.soomi.baby.domain.model.NightSummary
import com.soomi.baby.domain.model.ProgressTrend
import com.soomi.baby.ui.components.NightSummaryCard
import com.soomi.baby.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Progress Screen v2.7
 * 
 * Änderungen v2.7:
 * - Fix: Kalender-Icon Crash behoben (war: NullPointerException bei DatePicker)
 * - Deutsche Übersetzung
 * - Unruhetagebuch mit Kalender-Ansicht
 * 
 * Shows:
 * - Overall trend (improving/stable/worse)
 * - Calendar-style list of nights
 * - Each night: events, soothing time, user feedback
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel,
    onNavigateBack: () -> Unit,
    onNightClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // v2.7: Calendar dialog state - FIX für Crash
    var showCalendarDialog by remember { mutableStateOf(false) }
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unruhetagebuch") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Zurück")
                    }
                },
                actions = {
                    // v2.7: Kalender-Icon mit sicherem Dialog
                    IconButton(
                        onClick = { 
                            // FIX: Sichere Initialisierung des Dialogs
                            showCalendarDialog = true 
                        }
                    ) {
                        Icon(Icons.Default.CalendarMonth, "Kalender öffnen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.nights.isEmpty()) {
            EmptyProgressState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Trend card
                item {
                    TrendCard(
                        trend = uiState.overallTrend,
                        recentNights = uiState.nights.size,
                        totalEvents = uiState.nights.sumOf { it.unrestEventCount }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Letzte Nächte",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                // Night list
                items(uiState.nights) { night ->
                    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        .withLocale(Locale.GERMAN)
                    NightSummaryCard(
                        date = night.date.format(dateFormatter),
                        unrestEvents = night.unrestEventCount,
                        soothingMinutes = night.totalSoothingMinutes,
                        helpedRating = night.feedback?.helpedRating?.name,
                        onClick = { night.session?.let { onNightClick(it.id) } }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
    
    // v2.7: Kalender-Dialog mit sicherer Implementierung
    if (showCalendarDialog) {
        CalendarMonthDialog(
            selectedMonth = selectedMonth,
            nights = uiState.nights,
            onMonthChange = { selectedMonth = it },
            onDismiss = { showCalendarDialog = false },
            onDayClick = { date ->
                // Finde die Nacht für dieses Datum
                val night = uiState.nights.find { it.date == date }
                night?.session?.let { onNightClick(it.id) }
                showCalendarDialog = false
            }
        )
    }
}

/**
 * v2.7: Sicherer Kalender-Dialog (ersetzt DatePicker der gecrasht hat)
 */
@Composable
private fun CalendarMonthDialog(
    selectedMonth: YearMonth,
    nights: List<NightSummary>,
    onMonthChange: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
    onDayClick: (LocalDate) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onMonthChange(selectedMonth.minusMonths(1)) }) {
                    Icon(Icons.Default.ChevronLeft, "Vorheriger Monat")
                }
                Text(
                    text = "${selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)} ${selectedMonth.year}",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { onMonthChange(selectedMonth.plusMonths(1)) }) {
                    Icon(Icons.Default.ChevronRight, "Nächster Monat")
                }
            }
        },
        text = {
            Column {
                // Wochentage Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach { day ->
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Kalender-Tage
                val firstDayOfMonth = selectedMonth.atDay(1)
                val lastDayOfMonth = selectedMonth.atEndOfMonth()
                val startDayOfWeek = firstDayOfMonth.dayOfWeek.value // 1 = Montag
                
                // Erstelle die Tage-Liste mit Platzhaltern
                val days = mutableListOf<LocalDate?>()
                
                // Leere Felder vor dem ersten Tag
                repeat(startDayOfWeek - 1) {
                    days.add(null)
                }
                
                // Tage des Monats
                var currentDay = firstDayOfMonth
                while (!currentDay.isAfter(lastDayOfMonth)) {
                    days.add(currentDay)
                    currentDay = currentDay.plusDays(1)
                }
                
                // In 7er-Gruppen (Wochen) aufteilen
                days.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        week.forEach { day ->
                            if (day != null) {
                                val hasNight = nights.any { it.date == day }
                                val isToday = day == LocalDate.now()
                                
                                CalendarDayCell(
                                    day = day.dayOfMonth,
                                    hasNight = hasNight,
                                    isToday = isToday,
                                    onClick = { onDayClick(day) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        // Auffüllen falls die Woche weniger als 7 Tage hat
                        repeat(7 - week.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen")
            }
        }
    )
}

@Composable
private fun CalendarDayCell(
    day: Int,
    hasNight: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        hasNight -> SoomiCalm.copy(alpha = 0.3f)
        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surface
    }
    
    val textColor = when {
        hasNight -> SoomiCalm
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    Surface(
        color = backgroundColor,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .padding(2.dp)
            .clickable(enabled = hasNight, onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
            if (hasNight) {
                // Kleiner Punkt unter der Zahl
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(4.dp)
                        .padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun TrendCard(
    trend: ProgressTrend,
    recentNights: Int,
    totalEvents: Int
) {
    val (icon, label, color) = when (trend) {
        ProgressTrend.IMPROVING -> Triple(Icons.Default.TrendingDown, "Verbesserung", SoomiCalm)
        ProgressTrend.STABLE -> Triple(Icons.Default.TrendingFlat, "Stabil", SoomiRising)
        ProgressTrend.WORSE -> Triple(Icons.Default.TrendingUp, "Unruhiger", SoomiCrisis)
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Letzte 7 Nächte: $label",
                    style = MaterialTheme.typography.titleMedium,
                    color = color
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$recentNights Nächte erfasst • $totalEvents Ereignisse gesamt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyProgressState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Brightness3,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Noch keine Nächte erfasst",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Starte heute Abend deine erste Sitzung\num den Fortschritt zu verfolgen",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 48.dp)
        )
    }
}

/**
 * UI State for Progress screen
 */
data class ProgressUiState(
    val isLoading: Boolean = true,
    val nights: List<NightSummary> = emptyList(),
    val overallTrend: ProgressTrend = ProgressTrend.STABLE
)

/**
 * ViewModel for Progress screen
 */
class ProgressViewModel(
    private val sessionRepository: SessionRepository
) {
    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState
    
    private val scope = CoroutineScope(Dispatchers.Main)
    
    fun loadData() {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val nights = sessionRepository.getNightSummaries(30)
                val trend = sessionRepository.calculateProgressTrend()
                
                _uiState.value = ProgressUiState(
                    isLoading = false,
                    nights = nights,
                    overallTrend = trend
                )
            } catch (e: Exception) {
                _uiState.value = ProgressUiState(isLoading = false)
            }
        }
    }
}
