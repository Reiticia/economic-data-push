package com.macroresearch.data

import com.macroresearch.data.model.AnalysisReport
import com.macroresearch.data.model.EconomicEvent
import com.macroresearch.data.model.ExpectedReaction
import com.macroresearch.data.model.MarketReaction
import com.macroresearch.data.model.ReactionComparison
import com.macroresearch.data.remote.stableEventId
import java.math.BigDecimal
import java.time.Instant

class LocalAnalysisEngine {
    fun analyze(event: EconomicEvent, observed: List<MarketReaction>): AnalysisReport {
        val actual = event.actual?.toBigDecimalOrNull()
        // Some sources publish only one market expectation ("forecast"); fall back to it so
        // a missing consensus column cannot collapse every signal into "neutral".
        val consensus = (event.consensus ?: event.forecast)?.toBigDecimalOrNull()
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
            id = stableEventId("analysis|${event.id}"),
            eventId = event.id,
            rawSurprise = surprise?.stripTrailingZeros()?.toPlainString(),
            macroSignal = signal,
            expectedReactions = expected,
            observedReactions = observed,
            comparisons = comparisons,
            summary = "rule_engine_summary",
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
            direction > 0 -> "tighter_policy_baseline"
            direction < 0 -> "easier_policy_baseline"
            else -> "no_directional_signal"
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
