package com.macroresearch.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macroresearch.R
import com.macroresearch.BuildConfig
import com.macroresearch.data.CountryPreferences
import com.macroresearch.data.MacroRepository
import com.macroresearch.data.MarketPreferences
import com.macroresearch.ui.common.assetLabel
import com.macroresearch.ui.common.countryLabel

@Composable
fun SettingsScreen(repository: MacroRepository, padding: PaddingValues) {
    val selectedCountries by repository.selectedCountries.collectAsStateWithLifecycle()
    val countries = CountryPreferences.SUPPORTED_COUNTRIES.associateWith { it in selectedCountries }
    val selectedMarkets by repository.selectedMarkets.collectAsStateWithLifecycle()
    val markets = MarketPreferences.SUPPORTED_MARKETS.associateWith { it in selectedMarkets }
    var releaseNotifications by rememberSaveable { mutableStateOf(true) }
    var reactionNotifications by rememberSaveable { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.settings_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { LanguageSettings() }
            item {
                SettingsGroup(stringResource(R.string.countries_regions), countries, { countryLabel(it) }) { key, checked ->
                    repository.setCountryEnabled(key, checked)
                }
            }
            item {
                MarketSettingsGroup(markets) { key, checked ->
                    repository.setMarketEnabled(key, checked)
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.notifications), fontWeight = FontWeight.Bold)
                        ToggleRow(stringResource(R.string.release_notifications), releaseNotifications) { releaseNotifications = it }
                        ToggleRow(stringResource(R.string.reaction_notifications), reactionNotifications) { reactionNotifications = it }
                        Text(stringResource(R.string.notification_permission_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (BuildConfig.DEBUG) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.server), fontWeight = FontWeight.Bold)
                            Text(BuildConfig.API_BASE_URL, color = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.server_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, values: Map<String, Boolean>, labelFor: @Composable (String) -> String, onChange: (String, Boolean) -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            values.forEach { (label, checked) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked, { onChange(label, it) })
                    Text(labelFor(label))
                }
            }
        }
    }
}

@Composable
private fun MarketSettingsGroup(values: Map<String, Boolean>, onChange: (String, Boolean) -> Unit) {
    val selectedCount = values.count { it.value }
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.market_tracking), fontWeight = FontWeight.Bold)
            values.forEach { (market, checked) ->
                val enabled = if (checked) {
                    selectedCount > MarketPreferences.MIN_SELECTIONS
                } else {
                    selectedCount < MarketPreferences.MAX_SELECTIONS
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { onChange(market, it) },
                        enabled = enabled,
                    )
                    Text(assetLabel(market))
                }
            }
            Text(
                stringResource(R.string.market_selection_note, selectedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked, onCheckedChange = onChange)
    }
}
