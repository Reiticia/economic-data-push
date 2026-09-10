package com.macroresearch.data

import com.macroresearch.data.model.AnalysisReport
import com.macroresearch.data.model.EconomicEvent
import com.macroresearch.data.model.ExpectedReaction
import com.macroresearch.data.model.MarketReaction
import com.macroresearch.data.model.ReactionComparison
import com.macroresearch.data.remote.TradingEconomicsClient
import java.math.BigDecimal
import java.time.Instant

class LocalAnalysisEngine {
    fun analyze(event: EconomicEvent, observed: List<MarketReaction>): AnalysisReport {
        val actual = event.actual?.toBigDecimalOrNull()
        val consensus = event.consensus?.toBigDecimalOrNull()
        val surprise = if (actual != null && consensus != null) actual - consensus else null
        val polarity = indicatorPolarity(event)
        val macroDirection = when {
            surprise == null || surprise.signum() == 0 || polarity == 0 -> 0
            else -> surprise.signum() * polarity
        }
        val strong = surprise?.abs()?.let { it >= BigDecimal("0.2") } == true
        val signal = when {
            macroDirection > 0 && strong -> "strong_hawkish"
            macroDirection > 0 -> "hawkish"
            macroDirection < 0 && strong -> "strong_dovish"
            macroDirection < 0 -> "dovish"
            else -> "neutral"
        }
        val expected = expectedReactions(macroDirection)
        val expectedBySymbol = expected.associate { it.symbol to it.direction }
        val comparisons = observed.map { reaction ->
            val expectedDirection = expectedBySymbol[reaction.symbol] ?: "flat"
            val change = reaction.change5m ?: reaction.change1m
            ReactionComparison(
                symbol = reaction.symbol,
                expected = expectedDirection,
                observedChange = change,
                conforms = change?.let {
                    when (expectedDirection) {
                        "up" -> it > 0
                        "down" -> it < 0
                        else -> null
                    }
                },
            )
        }
        val now = Instant.now().toString()
        return AnalysisReport(
            id = TradingEconomicsClient.stableId("analysis|${event.id}"),
            eventId = event.id,
            rawSurprise = surprise?.stripTrailingZeros()?.toPlainString(),
            macroSignal = signal,
            expectedReactions = expected,
            observedReactions = observed,
            comparisons = comparisons,
            summary = "Calculated on this device from the published actual and consensus values. Market returns use public one-minute candles when available and are descriptive, not investment advice.",
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun indicatorPolarity(event: EconomicEvent): Int {
        val name = event.event.lowercase()
        return when {
            "unemployment" in name || "jobless claims" in name -> -1
            "crude oil stocks" in name || "crude oil inventories" in name -> 0
            POLARITY_POSITIVE.any { it in name } -> 1
            else -> 0
        }
    }

    private fun expectedReactions(direction: Int): List<ExpectedReaction> {
        val riskDirection = when {
            direction > 0 -> "down"
            direction < 0 -> "up"
            else -> "flat"
        }
        val defensiveDirection = when {
            direction > 0 -> "up"
            direction < 0 -> "down"
            else -> "flat"
        }
        val rationale = when {
            direction > 0 -> "A tighter policy path is the rule-based baseline."
            direction < 0 -> "An easier policy path is the rule-based baseline."
            else -> "The release does not produce a directional rule signal."
        }
        return listOf(
            ExpectedReaction("dxy", defensiveDirection, rationale),
            ExpectedReaction("us2y", defensiveDirection, rationale),
            ExpectedReaction("us10y", defensiveDirection, rationale),
            ExpectedReaction("gold", riskDirection, rationale),
            ExpectedReaction("nasdaq100", riskDirection, rationale),
            ExpectedReaction("bitcoin", riskDirection, rationale),
        )
    }

    companion object {
        private val POLARITY_POSITIVE = listOf(
            "inflation", "cpi", "average hourly earnings", "non farm payroll", "nonfarm payroll",
            "pce price", "gdp growth", "ism manufacturing", "ism services", "interest rate decision",
            "federal funds rate",
        )
    }
}
