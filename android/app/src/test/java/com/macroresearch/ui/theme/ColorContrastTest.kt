package com.macroresearch.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ColorContrastTest {
    @Test
    fun materialTextPairsMeetWcagAA() {
        listOf(LightColors, DarkColors).forEach { colors ->
            val pairs = listOf(
                colors.onPrimary to colors.primary,
                colors.onPrimaryContainer to colors.primaryContainer,
                colors.onSecondary to colors.secondary,
                colors.onSecondaryContainer to colors.secondaryContainer,
                colors.onTertiary to colors.tertiary,
                colors.onTertiaryContainer to colors.tertiaryContainer,
                colors.onError to colors.error,
                colors.onErrorContainer to colors.errorContainer,
                colors.onBackground to colors.background,
                colors.inverseOnSurface to colors.inverseSurface,
            )
            pairs.forEach { (text, background) -> assertReadable(text, background) }
            listOf(
                colors.background, colors.surface, colors.surfaceVariant,
                colors.surfaceContainerLowest, colors.surfaceContainerLow,
                colors.surfaceContainer, colors.surfaceContainerHigh,
                colors.surfaceContainerHighest,
            ).forEach { background ->
                assertReadable(colors.onSurface, background)
                assertReadable(colors.onSurfaceVariant, background)
                assertReadable(colors.primary, background)
            }
        }
    }

    @Test
    fun semanticLabelsAreReadableInBothThemes() {
        listOf(LightColors to LightResearchColors, DarkColors to DarkResearchColors)
            .forEach { (colors, research) ->
                listOf(research.hawkish, research.dovish, research.upcoming, research.assetUp, research.assetDown)
                    .forEach { text ->
                        listOf(colors.background, colors.surface, colors.surfaceContainerLow)
                            .forEach { background -> assertReadable(text, background) }
                    }
                // Gold importance dots also appear on selected calendar filters.
                assertReadable(research.upcoming, colors.secondaryContainer)
            }
    }

    @Test
    fun signalCardsKeepContrastOnTintedSurfaces() {
        listOf(LightColors to LightResearchColors, DarkColors to DarkResearchColors)
            .forEach { (colors, research) ->
                listOf(research.hawkish, research.dovish, colors.onSurfaceVariant).forEach { signal ->
                    val background = signal.copy(alpha = .08f).compositeOver(colors.surface)
                    assertReadable(signal, background)
                    assertReadable(colors.onSurfaceVariant, background)
                }
            }
    }

    @Test
    fun featuredEventLabelsStayReadable() {
        listOf(LightColors to LightResearchColors, DarkColors to DarkResearchColors)
            .forEach { (colors, research) ->
                listOf(colors.primary, colors.onPrimaryContainer, colors.onSurfaceVariant, research.upcoming)
                    .forEach { text -> assertReadable(text, colors.primaryContainer) }
            }
    }

    private fun assertReadable(text: Color, background: Color) {
        val light = luminance(text)
        val dark = luminance(background)
        val ratio = (max(light, dark) + .05) / (min(light, dark) + .05)
        assertTrue("$text on $background: contrast $ratio must be at least 4.5:1", ratio >= 4.5)
    }

    private fun luminance(color: Color): Double {
        fun linear(channel: Float): Double = channel.toDouble().let {
            if (it <= .04045) it / 12.92 else ((it + .055) / 1.055).pow(2.4)
        }
        return .2126 * linear(color.red) + .7152 * linear(color.green) + .0722 * linear(color.blue)
    }
}
