package com.macroresearch.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.macroresearch.data.MacroRepository
import com.macroresearch.data.model.EconomicEvent
import com.macroresearch.ui.HomeViewModel
import com.macroresearch.ui.common.EventCard
import com.macroresearch.ui.common.ImportanceDots
import com.macroresearch.ui.common.countdown
import com.macroresearch.ui.common.flag
import com.macroresearch.ui.common.localDate
import com.macroresearch.ui.common.localTime
import com.macroresearch.ui.common.value
import com.macroresearch.ui.theme.Upcoming
import com.macroresearch.ui.viewModelFactory
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HomeScreen(repository: MacroRepository, padding: PaddingValues, onEvent: (Long) -> Unit) {
    val vm: HomeViewModel = viewModel(factory = viewModelFactory { HomeViewModel(repository) })
    val events by vm.events.collectAsStateWithLifecycle()
    val refresh by vm.refresh.collectAsStateWithLifecycle()
    val market by vm.market.collectAsStateWithLifecycle()
    val now = Instant.now()
    val next = events.firstOrNull { it.importance == 3 && runCatching { Instant.parse(it.eventTime) > now }.getOrDefault(false) }
        ?: events.firstOrNull { runCatching { Instant.parse(it.eventTime) > now }.getOrDefault(false) }
    val today = events.filter {
        runCatching { Instant.parse(it.eventTime).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now() }
            .getOrDefault(false)
    }
    LaunchedEffect(next?.id) { next?.let { vm.loadMarket(it.id) } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        buildAnnotatedString {
                            append("Mac")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("ro") }
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(next?.localDate() ?: "宏观事件研究", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.Notifications, "通知", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (next != null) item { NextEventCard(next, { onEvent(next.id) }) }
        if (refresh.loading && events.isEmpty()) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        }
        refresh.error?.let { message -> item { Text("离线缓存 · $message", color = Upcoming) } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("今日事件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${today.size} 个事件", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(today, key = { it.id }) { event -> EventCard(event, { onEvent(event.id) }) }
        item {
            Text("市场概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val latest = market?.snapshots.orEmpty().groupBy { it.symbol }
                .mapValues { (_, values) -> values.maxByOrNull { it.timestamp } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("nasdaq100" to "NASDAQ", "dxy" to "DXY", "gold" to "Gold").forEach { (symbol, label) ->
                    MarketMiniCard(label, latest[symbol]?.price, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NextEventCard(event: EconomicEvent, onClick: () -> Unit) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(event.id) {
        while (true) { now = Instant.now(); delay(1_000) }
    }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2119)),
        border = BorderStroke(1.dp, Color(0xFFD97B26)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("▣  下一重要事件", color = Upcoming, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${flag(event.country)}  ${event.event}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(countdown(event.eventTime, now), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(event.localTime())
                Spacer(Modifier.width(8.dp)); Text("· 高重要性", color = Upcoming); Spacer(Modifier.width(8.dp)); ImportanceDots(event.importance)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ValueColumn("Previous", event.value(event.previous))
                ValueColumn("Consensus", event.value(event.consensus))
                ValueColumn("Forecast", event.value(event.forecast))
            }
        }
    }
}

@Composable
private fun ValueColumn(label: String, value: String) = Column {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun MarketMiniCard(label: String, price: Double?, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(price?.let { "%,.2f".format(it) } ?: "--", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (price == null) "等待行情" else "实时快照", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
