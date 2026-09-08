package com.macroresearch.ui.event

import androidx.compose.ui.res.stringResource
import com.macroresearch.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.macroresearch.data.MacroRepository
import com.macroresearch.data.model.EconomicEvent
import com.macroresearch.data.model.MarketResponse
import com.macroresearch.ui.EventDetailViewModel
import com.macroresearch.ui.common.assetLabel
import com.macroresearch.ui.common.categoryLabel
import com.macroresearch.ui.common.countryLabel
import com.macroresearch.ui.common.importanceLabel
import com.macroresearch.ui.common.localizedCountdown as countdown
import com.macroresearch.ui.common.flag
import com.macroresearch.ui.common.localizedChange as formatChange
import com.macroresearch.ui.common.localTime
import com.macroresearch.ui.common.statusLabel
import com.macroresearch.ui.common.localizedValue as value
import com.macroresearch.ui.theme.AssetDown
import com.macroresearch.ui.theme.AssetUp
import com.macroresearch.ui.theme.Upcoming
import com.macroresearch.ui.viewModelFactory
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    id: Long,
    repository: MacroRepository,
    onBack: () -> Unit,
    onAnalysis: () -> Unit,
    onHistory: () -> Unit,
) {
    val vm: EventDetailViewModel = viewModel(key = "event-$id", factory = viewModelFactory { EventDetailViewModel(id, repository) })
    val state by vm.state.collectAsStateWithLifecycle()
    val event = state.detail?.event
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(event?.event ?: stringResource(R.string.event_detail), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = vm::toggleFollowed) {
                        Icon(if (state.followed) Icons.Filled.Star else Icons.Outlined.StarBorder, stringResource(R.string.follow), tint = if (state.followed) Upcoming else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && event == null -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            event == null -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(stringResource(R.string.event_load_failed), style = MaterialTheme.typography.titleLarge)
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
            else -> EventContent(event, state.market, Modifier.padding(padding), onAnalysis, onHistory)
        }
    }
}

@Composable
private fun EventContent(
    event: EconomicEvent,
    market: MarketResponse?,
    modifier: Modifier,
    onAnalysis: () -> Unit,
    onHistory: () -> Unit,
) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("${flag(event.country)}  ${countryLabel(event.country)}", style = MaterialTheme.typography.titleMedium)
                    Text(categoryLabel(event.category), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${importanceLabel(event.importance)}  ${"●".repeat(event.importance.coerceIn(0, 3))}", color = Upcoming, style = MaterialTheme.typography.labelMedium)
            }
        }
        item { CountdownCard(event) }
        item { ReleaseDataCard(event) }
        item { EventIntroduction(event) }
        item { MarketTrackingCard(event, market) }
        item {
            Button(
                onClick = onAnalysis,
                modifier = Modifier.fillMaxWidth(),
                enabled = event.status in setOf("released", "collecting_market_data", "analyzing", "completed", "historical"),
            ) { Text(stringResource(if (event.status in setOf("completed", "historical")) R.string.view_analysis else R.string.view_analysis_progress)) }
        }
        item {
            Card(onClick = onHistory, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.view_history), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("›")
                }
            }
        }
        item { Column(Modifier.padding(bottom = 24.dp)) {} }
    }
}

@Composable
private fun CountdownCard(event: EconomicEvent) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(event.id) { while (true) { now = Instant.now(); delay(1_000) } }
    Card(colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(stringResource(if (event.actual == null) R.string.release_countdown else R.string.data_status), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (event.actual == null) countdown(event.eventTime, now) else statusLabel(event.status),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (event.status == "watching") Upcoming else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(event.localTime(), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.local_time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReleaseDataCard(event: EconomicEvent) {
    Card {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            DataValue(stringResource(R.string.actual), event.value(event.actual), event.actual != null)
            DataValue(stringResource(R.string.consensus), event.value(event.consensus))
            DataValue(stringResource(R.string.forecast), event.value(event.forecast))
            DataValue(stringResource(R.string.previous), event.value(event.previous))
        }
    }
}

@Composable
private fun DataValue(label: String, value: String, highlight: Boolean = false) = Column {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleMedium, color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
}

@Composable
private fun EventIntroduction(event: EconomicEvent) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.event_intro), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.event_intro_body, countryLabel(event.country), categoryLabel(event.category)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MarketTrackingCard(event: EconomicEvent, market: MarketResponse?) {
    val symbols = listOf("gold", "dxy", "us2y", "us10y", "nasdaq100", "bitcoin")
    val latest = market?.snapshots.orEmpty().groupBy { it.symbol }.mapValues { it.value.maxByOrNull { row -> row.timestamp } }
    val reactions = market?.reactions.orEmpty().associateBy { it.symbol }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.market_tracking), fontWeight = FontWeight.Bold)
                Text(statusLabel(event.status), color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
            symbols.forEach { symbol ->
                val reaction = reactions[symbol]
                val change = reaction?.change5m ?: reaction?.change1m
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(assetLabel(symbol))
                    Text(latest[symbol]?.price?.let { "%,.2f".format(it) } ?: "--")
                    Text(
                        formatChange(change, reaction?.reactionUnit ?: "percent"),
                        color = when { change == null -> MaterialTheme.colorScheme.onSurfaceVariant; change >= 0 -> AssetUp; else -> AssetDown },
                    )
                }
            }
            if (event.status == "historical") {
                Text(stringResource(R.string.historical_method), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                AnalysisProgress(event.eventTime, event.status)
            }
        }
    }
}

@Composable
private fun AnalysisProgress(eventTime: String, status: String) {
    val elapsed = runCatching { Duration.between(Instant.parse(eventTime), Instant.now()).toMinutes() }.getOrDefault(-1)
    Text(stringResource(R.string.analysis_progress), fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(1L, 5L, 15L, 30L, 60L).forEach { horizon ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.minutes_short, horizon), style = MaterialTheme.typography.labelSmall)
                Text(
                    if (elapsed >= horizon || status == "completed") "✓" else "--",
                    color = if (elapsed >= horizon || status == "completed") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
