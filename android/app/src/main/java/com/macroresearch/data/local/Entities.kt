package com.macroresearch.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.macroresearch.data.model.EconomicEvent

@Entity(tableName = "cached_event")
data class CachedEventEntity(
    @PrimaryKey val id: Long,
    val provider: String,
    val providerId: String,
    val releaseGroupId: Long?,
    val country: String,
    val currency: String?,
    val category: String,
    val event: String,
    val eventZhCn: String?,
    val eventZhTw: String?,
    val eventTime: String,
    val importance: Int,
    val actual: String?,
    val previous: String?,
    val consensus: String?,
    val forecast: String?,
    val unit: String?,
    val status: String,
)

@Entity(tableName = "followed_event")
data class FollowedEventEntity(
    @PrimaryKey val eventId: Long,
    val followedAt: Long = System.currentTimeMillis(),
)

/** Cached AI analysis; [chainJson] holds the serialized transmission chain. */
@Entity(tableName = "ai_analysis")
data class AiAnalysisEntity(
    @PrimaryKey val eventId: Long,
    val revision: Int,
    val chainJson: String,
    val dataAnalysis: String,
    val marketOutlook: String,
    val risks: String?,
    val model: String,
    val generatedAt: String,
)

fun EconomicEvent.asEntity() = CachedEventEntity(
    id, provider, providerId, releaseGroupId, country, currency, category, event, eventZhCn, eventZhTw,
    eventTime, importance, actual, previous, consensus, forecast, unit, status,
)

fun CachedEventEntity.asExternalModel() = EconomicEvent(
    id, provider, providerId, releaseGroupId, country, currency, category, event, eventZhCn, eventZhTw,
    eventTime, importance, actual, previous, consensus, forecast, unit, status,
)
