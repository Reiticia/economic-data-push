package com.macroresearch.data.model

data class EconomicEvent(
    val id: Long,
    val provider: String,
    val providerId: String,
    val releaseGroupId: Long?,
    val country: String,
    val currency: String?,
    val category: String,
    val event: String,
    val eventZhCn: String? = null,
    val eventZhTw: String? = null,
    val eventTime: String,
    val importance: Int,
    val actual: String?,
    val previous: String?,
    val consensus: String?,
    val forecast: String?,
    val unit: String?,
    val status: String,
)

data class EventObservation(
    val id: Long,
    val eventId: Long,
    val observedAt: String,
    val actual: String?,
    val previous: String?,
    val consensus: String?,
    val forecast: String?,
)

data class EventDetailResponse(
    val event: EconomicEvent,
    val observations: List<EventObservation>,
)

data class ExpectedReaction(
    val symbol: String,
    val direction: String,
    val rationale: String,
)

data class MarketReaction(
    val eventId: Long,
    val symbol: String,
    val baselinePrice: Double,
    val reactionUnit: String,
    val change1m: Double?,
    val change5m: Double?,
    val change15m: Double?,
    val change30m: Double?,
    val change60m: Double?,
)

data class ReactionComparison(
    val symbol: String,
    val expected: String,
    val observedChange: Double?,
    val conforms: Boolean?,
)

data class AnalysisReport(
    val id: Long,
    val eventId: Long,
    val rawSurprise: String?,
    val macroSignal: String,
    val expectedReactions: List<ExpectedReaction>,
    val observedReactions: List<MarketReaction>,
    val comparisons: List<ReactionComparison>,
    val summary: String,
    val createdAt: String,
    val updatedAt: String,
    val historical: HistoricalEvidence? = null,
)

data class HistoricalEvidence(
    val fetchedAt: String,
    val revisedDataPossible: Boolean,
    val coverage: List<HistoricalCoverage>,
)

data class HistoricalCoverage(
    val symbol: String,
    val source: String,
    val intervalSeconds: Long?,
    val status: String,
    val reason: String?,
    val availableHorizons: List<Int>,
    val baselineTime: String?,
    val sampleTimes: Map<String, String>,
)

data class MarketSnapshot(
    val id: Long,
    val eventId: Long,
    val symbol: String,
    val timestamp: String,
    val price: Double,
    val open: Double?,
    val high: Double?,
    val low: Double?,
    val close: Double?,
    val volume: Double?,
)

data class MarketResponse(
    val snapshots: List<MarketSnapshot>,
    val reactions: List<MarketReaction>,
)

data class SocketEvent(
    val type: String,
    val eventId: Long,
    val event: String? = null,
    val eventZhCn: String? = null,
    val eventZhTw: String? = null,
    val actual: String? = null,
    val consensus: String? = null,
)

