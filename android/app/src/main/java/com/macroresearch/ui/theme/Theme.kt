package com.macroresearch.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val Hawkish = Color(0xFFB69CFF)
val Dovish = Color(0xFF62D8D1)
val Upcoming = Color(0xFFFFB86B)
val AssetUp = Color(0xFF5DD39E)
val AssetDown = Color(0xFFFF6B7A)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5794FF),
    secondary = Dovish,
    tertiary = Hawkish,
    background = Color(0xFF07111C),
    surface = Color(0xFF0C1825),
    surfaceVariant = Color(0xFF132131),
    outline = Color(0xFF26384C),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF365E9D),
    secondary = Color(0xFF006B67),
    tertiary = Color(0xFF654FA3),
)

@Composable
fun MacroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(LocalContext.current)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(LocalContext.current)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
