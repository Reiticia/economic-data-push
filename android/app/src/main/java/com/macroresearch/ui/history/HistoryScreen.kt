package com.macroresearch.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var selected by remember { mutableStateOf<String?>(null) }
    val events = state.value.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("历史研究", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("从历史中发现规律", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "全部", "inflation" to "CPI / PCE", "employment" to "就业").forEach { (category, label) ->
                    FilterChip(
                        selected = selected == category,
                        onClick = { selected = category; vm.refresh(category) },
                        label = { Text(label) },
                    )
                }
            }
        }
        item { SummaryCards(events) }
        state.error?.let { item { Text("加载失败：$it", color = MaterialTheme.colorScheme.error) } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("历史记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("最近 ${events.size} 次", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(events, key = { it.id }) { event -> EventCard(event, { onEvent(event.id) }) }
    }
}

@Composable
private fun SummaryCards(events: List<EconomicEvent>) {
    val above = events.count { (it.surprise()?.signum() ?: 0) > 0 }
    val below = events.count { (it.surprise()?.signum() ?: 0) < 0 }
    val equal = events.count { it.actual != null && it.consensus != null && it.surprise()?.signum() == 0 }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard("Actual >\nConsensus", above, AssetUp, Modifier.weight(1f))
        SummaryCard("Actual <\nConsensus", below, AssetDown, Modifier.weight(1f))
        SummaryCard("Equal", equal, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(label: String, count: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text("${count}次", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

