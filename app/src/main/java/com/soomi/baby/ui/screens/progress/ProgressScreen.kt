package com.soomi.baby.ui.screens.progress

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
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Progress Screen
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
    
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Progress") },
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
                        text = "Recent Nights",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                // Night list
                items(uiState.nights) { night ->
                    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
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
}

@Composable
private fun TrendCard(
    trend: ProgressTrend,
    recentNights: Int,
    totalEvents: Int
) {
    val (icon, label, color) = when (trend) {
        ProgressTrend.IMPROVING -> Triple(Icons.Default.TrendingDown, "Improving", SoomiCalm)
        ProgressTrend.STABLE -> Triple(Icons.Default.TrendingFlat, "Stable", SoomiRising)
        ProgressTrend.WORSE -> Triple(Icons.Default.TrendingUp, "More restless", SoomiCrisis)
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
                    text = "Last 7 nights: $label",
                    style = MaterialTheme.typography.titleMedium,
                    color = color
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$recentNights nights tracked • $totalEvents total events",
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
            text = "No nights tracked yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Start your first session tonight\nto begin tracking progress",
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
