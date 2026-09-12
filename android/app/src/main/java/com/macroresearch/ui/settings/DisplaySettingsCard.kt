package com.macroresearch.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.macroresearch.R
import com.macroresearch.data.DisplayMode
import com.macroresearch.ui.theme.LocalDisplaySettings
import com.macroresearch.ui.theme.ResearchLayout

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplaySettingsCard() {
    val settings = LocalDisplaySettings.current
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(ResearchLayout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(ResearchLayout.smallGap),
        ) {
            Text(stringResource(R.string.display_size), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.display_size_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ResearchLayout.smallGap)) {
                DisplayMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.mode == mode,
                        onClick = { settings.setMode(mode) },
                        label = {
                            Text(stringResource(when (mode) {
                                DisplayMode.AUTO -> R.string.display_auto
                                DisplayMode.COMPACT -> R.string.display_compact
                                DisplayMode.SYSTEM -> R.string.display_system
                            }))
                        },
                    )
                }
            }
        }
    }
}
