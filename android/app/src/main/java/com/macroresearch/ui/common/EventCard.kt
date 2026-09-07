package com.macroresearch.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.macroresearch.data.model.EconomicEvent
import com.macroresearch.ui.theme.AssetDown
import com.macroresearch.ui.theme.AssetUp

@Composable
fun EventCard(event: EconomicEvent, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val surprise = event.surprise()
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${event.localTime()}  ${flag(event.country)}  ${event.country}", style = MaterialTheme.typography.labelLarge)
                Text(statusLabel(event.status), color = statusColor(event.status), style = MaterialTheme.typography.labelMedium)
            }
            Text(event.event, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (event.actual == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("前值 ${event.value(event.previous)}", style = MaterialTheme.typography.bodyMedium)
                    Text("预期 ${event.value(event.consensus)}", style = MaterialTheme.typography.bodyMedium)
                    ImportanceDots(event.importance)
                }
            } else {
                Text("实际 ${event.value(event.actual)}  ·  预期 ${event.value(event.consensus)}", style = MaterialTheme.typography.bodyLarge)
                surprise?.let {
                    Text(
                        text = if (it.signum() > 0) "↑ 高于预期 ${signed(it, if (event.unit == "%") "%" else "")}" else "↓ 低于预期 ${signed(it, if (event.unit == "%") "%" else "")}",
                        color = if (it.signum() > 0) AssetUp else AssetDown,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
fun ImportanceDots(importance: Int) {
    Text("●".repeat(importance.coerceIn(0, 3)), color = importanceColor(importance))
}

@Composable
private fun statusColor(status: String) = when (status) {
    "watching" -> com.macroresearch.ui.theme.Upcoming
    "released", "collecting_market_data", "analyzing" -> MaterialTheme.colorScheme.tertiary
    "completed" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun importanceColor(importance: Int) = when (importance) {
    3 -> MaterialTheme.colorScheme.tertiary
    2 -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

fun statusLabel(status: String): String = when (status) {
    "scheduled" -> "待公布"
    "watching" -> "监听中"
    "released" -> "已公布"
    "collecting_market_data" -> "采集中"
    "analyzing" -> "分析中"
    "completed" -> "已完成"
    "timeout" -> "超时"
    else -> status
}
