package com.macroresearch.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.macroresearch.ui.CalendarViewModel
import com.macroresearch.ui.common.EventCard
import com.macroresearch.ui.theme.Upcoming
import com.macroresearch.ui.viewModelFactory
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(repository: MacroRepository, padding: PaddingValues, onEvent: (Long) -> Unit) {
    val vm: CalendarViewModel = viewModel(factory = viewModelFactory { CalendarViewModel(repository) })
    val state by vm.state.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("日历", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${state.date.year}年${state.date.monthValue}月", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showFilters = true }) { Icon(Icons.Outlined.FilterAlt, "筛选", tint = MaterialTheme.colorScheme.primary) }
            }
        }
        item { DateSelector(state.date, vm::selectDate) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = 3 in state.importance, onClick = { showFilters = true }, label = { Text("🔴 High") }) }
                item { FilterChip(selected = 2 in state.importance, onClick = { showFilters = true }, label = { Text("🟠 Medium") }) }
                item { FilterChip(selected = "United States" in state.countries, onClick = { showFilters = true }, label = { Text("🇺🇸 US") }) }
                item { FilterChip(selected = "Euro Area" in state.countries, onClick = { showFilters = true }, label = { Text("🇪🇺 EU") }) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${state.date.monthValue}月${state.date.dayOfMonth}日 · ${state.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("${state.filtered.size} 个事件", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.error?.let { item { Text("加载失败：$it", color = MaterialTheme.colorScheme.error) } }
        if (!state.loading && state.filtered.isEmpty()) item {
            Card { Text("当前筛选下没有事件", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(state.filtered, key = { it.id }) { event -> EventCard(event, { onEvent(event.id) }) }
    }

    if (showFilters) {
        CalendarFilterSheet(
            initialImportance = state.importance,
            initialCountries = state.countries,
            onDismiss = { showFilters = false },
            onApply = { importance, countries ->
                vm.applyFilters(importance, countries)
                showFilters = false
            },
        )
    }
}

@Composable
private fun DateSelector(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val dates = remember(selected) {
        val monday = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
        (0L..6L).map(monday::plusDays)
    }
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        items(dates) { date ->
            FilterChip(
                selected = date == selected,
                onClick = { onSelect(date) },
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA))
                        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarFilterSheet(
    initialImportance: Set<Int>,
    initialCountries: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Set<Int>, Set<String>) -> Unit,
) {
    var importance by remember { mutableStateOf(initialImportance) }
    var countries by remember { mutableStateOf(initialCountries) }
    val countryOptions = listOf("United States" to "🇺🇸 US", "Euro Area" to "🇪🇺 EU", "China" to "🇨🇳 CN", "Japan" to "🇯🇵 JP", "United Kingdom" to "🇬🇧 UK")
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("筛选", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            Text("重要性", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3 to "High", 2 to "Medium", 1 to "Low").forEach { (level, label) ->
                    FilterChip(
                        selected = level in importance,
                        onClick = { importance = if (level in importance) importance - level else importance + level },
                        label = { Text(label) },
                    )
                }
            }
            Text("国家 / 地区", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(countryOptions) { (country, label) ->
                    FilterChip(
                        selected = country in countries,
                        onClick = { countries = if (country in countries) countries - country else countries + country },
                        label = { Text(label) },
                    )
                }
            }
            Text("宏观信号使用紫/青色，资产涨跌使用红/绿色。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onApply(importance, countries) }, modifier = Modifier.fillMaxWidth(), enabled = importance.isNotEmpty()) { Text("应用") }
        }
    }
}
