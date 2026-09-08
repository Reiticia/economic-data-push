package com.macroresearch.ui.analysis

import androidx.compose.ui.res.stringResource
import com.macroresearch.R
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
import androidx.compose.ui.graphics.compositeOver
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
import com.macroresearch.ui.common.assetLabel
import com.macroresearch.ui.common.macroSignalLabel
import com.macroresearch.ui.common.statusLabel
import com.macroresearch.ui.common.localizedChange as formatChange
import com.macroresearch.ui.common.localizedValue as value
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
        topBar = { TopAppBar(title = { Text(state.event?.event ?: stringResource(R.string.analysis), fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } }) },
    ) { padding ->
        when {
            state.loading -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            state.event == null -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) { Text(stringResource(R.string.event_load_failed)); Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            state.report == null -> AnalysisPending(state.event!!, Modifier.padding(padding), state.error, vm::refresh)
            else -> AnalysisContent(state.event!!, state.report!!, state.market, Modifier.padding(padding))
        }
    }
}

@Composable
private fun AnalysisPending(event: EconomicEvent, modifier: Modifier, error: String?, retry: () -> Unit) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        val historical = event.status == "historical"
        if (!historical) CircularProgressIndicator()
        Text(stringResource(if (historical) R.string.historical_pending else R.string.observing_market), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.current_status, statusLabel(event.status)), color = MaterialTheme.colorScheme.primary)
        Text(stringResource(if (historical) R.string.historical_pending_body else R.string.analysis_pending_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = retry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun AnalysisContent(event: EconomicEvent, report: AnalysisReport, market: MarketResponse?, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ResultCard(event, report) }
        item { SignalCard(report) }
        item { ExpectedCard(report) }
        item { ObservedTable(report) }
        if (report.historical != null) {
            item { HistoricalCoverageCard(report.historical) }
        } else {
            item { ReactionTimeline(event, market) }
        }
        item {
            Text(report.summary, Modifier.padding(bottom = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ResultCard(event: EconomicEvent, report: AnalysisReport) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.data_results), fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric(stringResource(R.string.actual), event.value(event.actual), true)
                Metric(stringResource(R.string.consensus), event.value(event.consensus))
                Metric(stringResource(R.string.previous), event.value(event.previous))
                Metric(stringResource(R.string.surprise), report.rawSurprise?.let { (if (it.startsWith("-")) "" else "+") + it + if (event.unit == "%") "%" else "" } ?: "--", true)
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
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = .08f).compositeOver(MaterialTheme.colorScheme.surface),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, color.copy(alpha = .45f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(macroSignalLabel(report.macroSignal), style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.signal_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExpectedCard(report: AnalysisReport) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.expected_reactions), fontWeight = FontWeight.Bold)
            report.expectedReactions.forEach { reaction ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(assetLabel(reaction.symbol), Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
                    Text(if (reaction.direction == "up") "↑" else if (reaction.direction == "down") "↓" else "→", color = if (reaction.direction == "up") AssetUp else if (reaction.direction == "down") AssetDown else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp), fontWeight = FontWeight.Bold)
                    Text(reaction.rationale, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
            Text(stringResource(R.string.observed_reactions), fontWeight = FontWeight.Bold)
            Row(Modifier.width(760.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.asset), Modifier.width(100.dp), style = MaterialTheme.typography.labelSmall)
                listOf(1, 5, 15, 30, 60).forEach { Text(stringResource(R.string.minutes_short, it), Modifier.width(100.dp), style = MaterialTheme.typography.labelSmall) }
                Text(stringResource(R.string.conformity), Modifier.width(100.dp), style = MaterialTheme.typography.labelSmall)
            }
            HorizontalDivider()
            report.observedReactions.forEach { reaction ->
                Row(Modifier.width(760.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    val values = listOf(assetLabel(reaction.symbol), formatChange(reaction.change1m, reaction.reactionUnit), formatChange(reaction.change5m, reaction.reactionUnit), formatChange(reaction.change15m, reaction.reactionUnit), formatChange(reaction.change30m, reaction.reactionUnit), formatChange(reaction.change60m, reaction.reactionUnit))
                    values.forEachIndexed { index, value -> Text(value, Modifier.width(100.dp), color = if (index > 0 && value.startsWith("+")) AssetUp else if (index > 0 && value.startsWith("-")) AssetDown else MaterialTheme.colorScheme.onSurface) }
                    val conforms = comparisons[reaction.symbol]?.conforms
                    Text(if (conforms == null) "--" else stringResource(if (conforms) R.string.conforms else R.string.diverges), Modifier.width(100.dp), color = if (conforms == true) AssetUp else if (conforms == false) AssetDown else MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text(stringResource(R.string.reaction_timeline), fontWeight = FontWeight.Bold)
                Text(stringResource(if (minute < 0) R.string.before_release else R.string.after_release, abs(minute.toInt())), color = MaterialTheme.colorScheme.primary)
            }
            Slider(value = minute, onValueChange = { minute = it }, valueRange = -5f..60f, steps = 64)
            listOf("gold", "dxy", "us2y", "nasdaq100", "bitcoin").forEach { symbol ->
                val change = timelineChange(event.eventTime, samples.filter { it.symbol == symbol }, minute.toInt(), symbol in setOf("us2y", "us10y"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(assetLabel(symbol))
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
