package com.macroresearch.ui.market

import androidx.compose.ui.res.stringResource
import com.macroresearch.R
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
import com.macroresearch.ui.common.assetLabel

@Composable
fun MarketScreen(padding: PaddingValues) {
    val groups = listOf(
        R.string.risk_assets to listOf("NASDAQ", "S&P 500"),
        R.string.precious_metals to listOf("Gold", "Silver"),
        R.string.dollar_fx to listOf("DXY", "EUR/USD"),
        R.string.treasuries to listOf("US 2Y", "US 10Y"),
        R.string.crypto to listOf("BTC", "ETH"),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.nav_market), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.market_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        groups.forEach { (title, symbols) ->
            item {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    symbols.forEach { symbol ->
                        Card {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(assetLabel(symbol), fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.waiting_market_api), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.market_api_note), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

