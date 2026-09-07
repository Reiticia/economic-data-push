package com.macroresearch.ui.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MarketScreen(padding: PaddingValues) {
    val groups = listOf(
        "风险资产" to listOf("NASDAQ", "S&P 500"),
        "贵金属" to listOf("Gold", "Silver"),
        "美元与外汇" to listOf("DXY", "EUR/USD"),
        "美债" to listOf("US 2Y", "US 10Y"),
        "Crypto" to listOf("BTC", "ETH"),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("市场", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("宏观相关资产", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        groups.forEach { (title, symbols) ->
            item {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    symbols.forEach { symbol ->
                        Card {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(symbol, fontWeight = FontWeight.Bold)
                                Text("等待实时市场 API", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        item {
            Text("事件窗口内的实时价格和涨跌已在事件详情与分析页面展示。独立市场页将在后端提供当前报价接口后自动接入。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

