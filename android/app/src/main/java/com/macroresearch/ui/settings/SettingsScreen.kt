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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.macroresearch.BuildConfig

@Composable
fun SettingsScreen(padding: PaddingValues) {
    val countries = remember { mutableStateMapOf("United States" to true, "Euro Area" to true, "China" to true, "Japan" to true, "United Kingdom" to false) }
    val markets = remember { mutableStateMapOf("Gold" to true, "DXY" to true, "US 2Y" to true, "US 10Y" to true, "NASDAQ" to true, "Bitcoin" to true) }
    var releaseNotifications by remember { mutableStateOf(true) }
    var reactionNotifications by remember { mutableStateOf(true) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("个人研究偏好", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SettingsGroup("国家 / 地区", countries) }
        item { SettingsGroup("市场观察", markets) }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("通知", fontWeight = FontWeight.Bold)
                    ToggleRow("数据公布通知", releaseNotifications) { releaseNotifications = it }
                    ToggleRow("5m / 15m 市场反应", reactionNotifications) { reactionNotifications = it }
                    Text("系统通知权限可在 Android 应用设置中调整。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("服务器", fontWeight = FontWeight.Bold)
                    Text(BuildConfig.API_BASE_URL, color = MaterialTheme.colorScheme.primary)
                    Text("可通过 app/build.gradle.kts 的 BuildConfig 修改。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, values: MutableMap<String, Boolean>) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            values.toMap().forEach { (label, checked) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked, { values[label] = it })
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Switch(checked, onCheckedChange = onChange)
    }
}
