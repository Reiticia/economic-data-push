package com.macroresearch.ui.calendar

import androidx.compose.ui.res.stringResource
import com.macroresearch.R
import com.macroresearch.ui.common.appLocale
import com.macroresearch.ui.common.dateLabel
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
import com.macroresearch.data.CountryPreferences
import com.macroresearch.data.MacroRepository
import com.macroresearch.ui.CalendarViewModel
import com.macroresearch.ui.common.EventCard
import com.macroresearch.ui.common.countryLabel
import com.macroresearch.ui.common.flag
import com.macroresearch.ui.common.importanceLabel
import com.macroresearch.ui.theme.Upcoming
import com.macroresearch.ui.viewModelFactory
import java.time.LocalDate
import java.time.format.TextStyle

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
                    Text(stringResource(R.string.nav_calendar), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(dateLabel(state.date, monthOnly = true), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showFilters = true }) { Icon(Icons.Outlined.FilterAlt, stringResource(R.string.filter), tint = MaterialTheme.colorScheme.primary) }
            }
        }
        item { DateSelector(state.date, vm::selectDate) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = 3 in state.importance, onClick = { showFilters = true }, leadingIcon = { Text("●", color = Upcoming) }, label = { Text(importanceLabel(3)) }) }
                item { FilterChip(selected = 2 in state.importance, onClick = { showFilters = true }, leadingIcon = { Text("●", color = MaterialTheme.colorScheme.primary) }, label = { Text(importanceLabel(2)) }) }
                item { FilterChip(selected = "United States" in state.countries, onClick = { showFilters = true }, label = { Text("🇺🇸 ${countryLabel("United States")}") }) }
                item { FilterChip(selected = "Euro Area" in state.countries, onClick = { showFilters = true }, label = { Text("🇪🇺 ${countryLabel("Euro Area")}") }) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dateLabel(state.date), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.event_count, state.filtered.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.error?.let { item { Text(stringResource(R.string.load_failed, it), color = MaterialTheme.colorScheme.error) } }
        if (!state.loading && state.filtered.isEmpty()) item {
            Card { Text(stringResource(R.string.no_filtered_events), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                        Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, appLocale()))
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
    val countryOptions = CountryPreferences.SUPPORTED_COUNTRIES
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.filter), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            Text(stringResource(R.string.importance), fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(3, 2, 1)) { level ->
                    FilterChip(
                        selected = level in importance,
                        onClick = { importance = if (level in importance) importance - level else importance + level },
                        label = { Text(importanceLabel(level)) },
                    )
                }
            }
            Text(stringResource(R.string.countries_regions), fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(countryOptions) { country ->
                    FilterChip(
                        selected = country in countries,
                        onClick = { countries = if (country in countries) countries - country else countries + country },
                        label = { Text("${flag(country)} ${countryLabel(country)}") },
                    )
                }
            }
            Text(stringResource(R.string.color_legend), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onApply(importance, countries) }, modifier = Modifier.fillMaxWidth(), enabled = importance.isNotEmpty()) { Text(stringResource(R.string.apply)) }
        }
    }
}
