package com.macroresearch.ui.history

import androidx.compose.ui.res.stringResource
import com.macroresearch.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.macroresearch.data.MacroRepository
import com.macroresearch.data.model.EconomicEvent
import com.macroresearch.ui.HistoryViewModel
import com.macroresearch.ui.common.EventCard
import com.macroresearch.ui.common.surprise
import com.macroresearch.ui.theme.AssetDown
import com.macroresearch.ui.theme.AssetUp
import com.macroresearch.ui.viewModelFactory

@Composable
fun HistoryScreen(repository: MacroRepository, padding: PaddingValues, onEvent: (Long) -> Unit) {
    val vm: HistoryViewModel = viewModel(factory = viewModelFactory { HistoryViewModel(repository) })
    val state by vm.state.collectAsStateWithLifecycle()
    val selected by vm.category.collectAsStateWithLifecycle()
    val hasMore by vm.hasMore.collectAsStateWithLifecycle()
    val events = state.value.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.history_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to stringResource(R.string.all), "inflation" to stringResource(R.string.category_inflation), "employment" to stringResource(R.string.category_employment)).forEach { (category, label) ->
                    FilterChip(
                        selected = selected == category,
                        onClick = { vm.refresh(category) },
                        label = { Text(label) },
                    )
                }
            }
        }
        item { TextButton(onClick = { vm.refresh() }, enabled = !state.loading) { Text(stringResource(R.string.history_refresh)) } }
        item { SummaryCards(events) }
        state.error?.let { item { Text(stringResource(R.string.load_failed, it), color = MaterialTheme.colorScheme.error) } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.history_records), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.recent_count, events.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(events, key = { it.id }) { event -> EventCard(event, { onEvent(event.id) }) }
        if (state.loading) item { CircularProgressIndicator() }
        if (hasMore && !state.loading) item {
            Button(onClick = vm::loadMore, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.error == null) R.string.history_load_more else R.string.retry))
            }
        }
    }
}

@Composable
private fun SummaryCards(events: List<EconomicEvent>) {
    val above = events.count { (it.surprise()?.signum() ?: 0) > 0 }
    val below = events.count { (it.surprise()?.signum() ?: 0) < 0 }
    val equal = events.count { it.actual != null && it.consensus != null && it.surprise()?.signum() == 0 }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard(stringResource(R.string.above_consensus), above, AssetUp, Modifier.weight(1f))
        SummaryCard(stringResource(R.string.below_consensus), below, AssetDown, Modifier.weight(1f))
        SummaryCard(stringResource(R.string.equal_consensus), equal, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(label: String, count: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(stringResource(R.string.occurrence_count, count), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

