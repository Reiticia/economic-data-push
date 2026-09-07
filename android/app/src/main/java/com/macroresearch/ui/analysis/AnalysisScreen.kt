package com.macroresearch.ui.analysis

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.macroresearch.data.MacroRepository
import com.macroresearch.data.model.AnalysisReport
import com.macroresearch.data.model.EconomicEvent
import com.macroresearch.data.model.MarketResponse
import com.macroresearch.data.model.MarketSnapshot
import com.macroresearch.ui.AnalysisViewModel
import com.macroresearch.ui.common.formatChange
import com.macroresearch.ui.common.value
import com.macroresearch.ui.theme.AssetDown
import com.macroresearch.ui.theme.AssetUp
import com.macroresearch.ui.theme.Dovish
import com.macroresearch.ui.theme.Hawkish
import com.macroresearch.ui.viewModelFactory
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(id: Long, repository: MacroRepository, onBack: () -> Unit) {
    val vm: AnalysisViewModel = viewModel(key = "analysis-$id", factory = viewModelFactory { AnalysisViewModel(id, repository) })
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text(state.event?.event ?: "分析", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }) },
    ) { padding ->
        when {
            state.loading -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            state.event == null -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) { Text("事件加载失败"); Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            state.report == null -> AnalysisPending(state.event!!, Modifier.padding(padding), state.error, vm::refresh)
            else -> AnalysisContent(state.event!!, state.report!!, state.market, Modifier.padding(padding))
        }
    }
}

@Composable
private fun AnalysisPending(event: EconomicEvent, modifier: Modifier, error: String?, retry: () -> Unit) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        CircularProgressIndicator()
        Text("正在观察市场反应…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("当前状态：${event.status}", color = MaterialTheme.colorScheme.primary)
        Text("分析会随 1m / 5m / 15m / 30m / 60m 数据逐步完成。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = retry) { Text("重新检查") }
    }
}

@Composable
private fun AnalysisContent(event: EconomicEvent, report: AnalysisReport, market: MarketResponse?, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ResultCard(event, report) }
        item { SignalCard(report) }
        item { ExpectedCard(report) }
        item { ObservedTable(report) }
        item { ReactionTimeline(event, market) }
        item {
            Text(report.summary, Modifier.padding(bottom = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ResultCard(event: EconomicEvent, report: AnalysisReport) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("数据结果", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Actual", event.value(event.actual), true)
                Metric("Consensus", event.value(event.consensus))
                Metric("Previous", event.value(event.previous))
                Metric("Surprise", report.rawSurprise?.let { (if (it.startsWith("-")) "" else "+") + it + if (event.unit == "%") "%" else "" } ?: "--", true)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, highlight: Boolean = false) = Column {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, fontWeight = FontWeight.Bold, color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun SignalCard(report: AnalysisReport) {
    val hawkish = report.macroSignal.contains("hawkish")
    val color = if (hawkish) Hawkish else if (report.macroSignal.contains("dovish")) Dovish else MaterialTheme.colorScheme.onSurfaceVariant
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .15f)), border = BorderStroke(1.dp, color)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (hawkish) "🦅  ${report.macroSignal.label()}" else "${report.macroSignal.label()}", style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
            Text("规则信号描述宏观倾向，不代表资产必然按该方向运行。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExpectedCard(report: AnalysisReport) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("理论市场影响", fontWeight = FontWeight.Bold)
            report.expectedReactions.forEach { reaction ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(reaction.symbol.label())
                    Text(if (reaction.direction == "up") "↑" else if (reaction.direction == "down") "↓" else "→", color = if (reaction.direction == "up") AssetUp else if (reaction.direction == "down") AssetDown else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text(reaction.rationale, Modifier.width(210.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ObservedTable(report: AnalysisReport) {
    val comparisons = report.comparisons.associateBy { it.symbol }
    Card {
        Column(Modifier.padding(16.dp).horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("实际行情", fontWeight = FontWeight.Bold)
            Row(Modifier.width(620.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("Asset", "1m", "5m", "15m", "30m", "60m", "符合度").forEach { Text(it, Modifier.width(76.dp), style = MaterialTheme.typography.labelSmall) }
            }
            HorizontalDivider()
            report.observedReactions.forEach { reaction ->
                Row(Modifier.width(620.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    val values = listOf(reaction.symbol.label(), formatChange(reaction.change1m, reaction.reactionUnit), formatChange(reaction.change5m, reaction.reactionUnit), formatChange(reaction.change15m, reaction.reactionUnit), formatChange(reaction.change30m, reaction.reactionUnit), formatChange(reaction.change60m, reaction.reactionUnit))
                    values.forEachIndexed { index, value -> Text(value, Modifier.width(76.dp), color = if (index > 0 && value.startsWith("+")) AssetUp else if (index > 0 && value.startsWith("-")) AssetDown else MaterialTheme.colorScheme.onSurface) }
                    val conforms = comparisons[reaction.symbol]?.conforms
                    Text(if (conforms == true) "符合" else if (conforms == false) "背离" else "--", Modifier.width(76.dp), color = if (conforms == true) AssetUp else if (conforms == false) AssetDown else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ReactionTimeline(event: EconomicEvent, market: MarketResponse?) {
    var minute by remember { mutableFloatStateOf(5f) }
    val samples = market?.snapshots.orEmpty()
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("事件反应时间线", fontWeight = FontWeight.Bold)
                Text("T${if (minute >= 0) "+" else ""}${minute.toInt()}m", color = MaterialTheme.colorScheme.primary)
            }
            Slider(value = minute, onValueChange = { minute = it }, valueRange = -5f..60f, steps = 64)
            listOf("gold", "dxy", "us2y", "nasdaq100", "bitcoin").forEach { symbol ->
                val change = timelineChange(event.eventTime, samples.filter { it.symbol == symbol }, minute.toInt(), symbol in setOf("us2y", "us10y"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(symbol.label())
                    Text(formatChange(change, if (symbol.startsWith("us") && symbol.endsWith("y")) "basis_points" else "percent"), color = when { change == null -> MaterialTheme.colorScheme.onSurfaceVariant; change >= 0 -> AssetUp; else -> AssetDown })
                }
            }
        }
    }
}

private fun timelineChange(eventTime: String, values: List<MarketSnapshot>, minute: Int, yield: Boolean): Double? {
    val event = runCatching { Instant.parse(eventTime) }.getOrNull() ?: return null
    val baselineTarget = event.minusSeconds(60)
    val target = event.plusSeconds(minute * 60L)
    val baseline = values.minByOrNull { abs(Duration.between(baselineTarget, Instant.parse(it.timestamp)).seconds) } ?: return null
    val sample = values.minByOrNull { abs(Duration.between(target, Instant.parse(it.timestamp)).seconds) } ?: return null
    if (abs(Duration.between(target, Instant.parse(sample.timestamp)).seconds) > 180 || baseline.price == 0.0) return null
    return if (yield) (sample.price - baseline.price) * 100 else (sample.price - baseline.price) / baseline.price * 100
}

private fun String.label(): String = split('_').joinToString(" ") { part -> part.replaceFirstChar(Char::uppercase) }
