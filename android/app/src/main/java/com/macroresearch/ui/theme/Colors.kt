package com.macroresearch.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Ink & Jade: quiet neutral surfaces, jade for interaction, champagne for emphasis.
// Define container/on-container pairs as well as accents so Material components
// never fall back to the stock purple palette.
internal val DarkColors = darkColorScheme(
    primary = Color(0xFF8DD5C5),
    onPrimary = Color(0xFF073C32),
    primaryContainer = Color(0xFF173F36),
    onPrimaryContainer = Color(0xFFC1EFE1),
    inversePrimary = Color(0xFF226B5B),
    secondary = Color(0xFFB0C9C2),
    onSecondary = Color(0xFF1C3630),
    secondaryContainer = Color(0xFF2B433C),
    onSecondaryContainer = Color(0xFFD2E8DF),
    tertiary = Color(0xFFE4BE80),
    onTertiary = Color(0xFF432E0C),
    tertiaryContainer = Color(0xFF473820),
    onTertiaryContainer = Color(0xFFF6DDB4),
    background = Color(0xFF0D1514),
    onBackground = Color(0xFFE4ECE7),
    surface = Color(0xFF111C19),
    onSurface = Color(0xFFE4ECE7),
    surfaceVariant = Color(0xFF243630),
    onSurfaceVariant = Color(0xFFAABDB4),
    surfaceTint = Color(0xFF8DD5C5),
    surfaceDim = Color(0xFF0D1514),
    surfaceBright = Color(0xFF35433D),
    surfaceContainerLowest = Color(0xFF09110F),
    surfaceContainerLow = Color(0xFF15221D),
    surfaceContainer = Color(0xFF1A2923),
    surfaceContainerHigh = Color(0xFF21322A),
    surfaceContainerHighest = Color(0xFF293B32),
    inverseSurface = Color(0xFFE4ECE7),
    inverseOnSurface = Color(0xFF25362E),
    outline = Color(0xFF768B80),
    outlineVariant = Color(0xFF354A3F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF59302D),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
)

internal val LightColors = lightColorScheme(
    primary = Color(0xFF226B5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9ECE2),
    onPrimaryContainer = Color(0xFF164C3E),
    inversePrimary = Color(0xFF8DD5C5),
    secondary = Color(0xFF4F685D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0EADF),
    onSecondaryContainer = Color(0xFF304B3B),
    tertiary = Color(0xFF855C20),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3E5CE),
    onTertiaryContainer = Color(0xFF61420F),
    background = Color(0xFFF5F4EE),
    onBackground = Color(0xFF1C3027),
    surface = Color(0xFFFCFBF6),
    onSurface = Color(0xFF1C3027),
    surfaceVariant = Color(0xFFE6EAE0),
    onSurfaceVariant = Color(0xFF56665A),
    surfaceTint = Color(0xFF226B5B),
    surfaceDim = Color(0xFFDDDFD5),
    surfaceBright = Color(0xFFFCFBF6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F1E9),
    surfaceContainer = Color(0xFFECEFE5),
    surfaceContainerHigh = Color(0xFFE6EADF),
    surfaceContainerHighest = Color(0xFFE0E5D9),
    inverseSurface = Color(0xFF25362E),
    inverseOnSurface = Color(0xFFF0F3E9),
    outline = Color(0xFF788479),
    outlineVariant = Color(0xFFCED7CB),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0xFF000000),
)

// Macro direction is not asset direction: violet/blue signals remain distinct
// from green/red returns. Light mode uses darker inks for readable small labels.
@Immutable
internal data class ResearchColors(
    val hawkish: Color,
    val dovish: Color,
    val upcoming: Color,
    val assetUp: Color,
    val assetDown: Color,
)

internal val DarkResearchColors = ResearchColors(
    hawkish = Color(0xFFC5AFE8),
    dovish = Color(0xFF8ABFE0),
    upcoming = Color(0xFFE4BE80),
    assetUp = Color(0xFF78C7A0),
    assetDown = Color(0xFFEF929A),
)

internal val LightResearchColors = ResearchColors(
    hawkish = Color(0xFF7856A8),
    dovish = Color(0xFF236A91),
    upcoming = Color(0xFF855C20),
    assetUp = Color(0xFF18734F),
    assetDown = Color(0xFFB13D4D),
)
