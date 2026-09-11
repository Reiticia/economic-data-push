package com.macroresearch.data

import com.macroresearch.data.local.EventDao
import com.macroresearch.data.local.asEntity
import com.macroresearch.data.local.asExternalModel
import com.macroresearch.data.model.EconomicEvent
import com.macroresearch.data.remote.EconomicCalendarClient
import java.time.Instant
import java.time.ZoneId

internal data class EventReleaseRefreshResult(val event: EconomicEvent, val warning: String?)

/** A user-triggered retry, independent of history TTLs, translation and market requests. */
internal class EventReleaseRefresher(
    private val calendar: EconomicCalendarClient,
    private val dao: EventDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun refresh(id: Long): EventReleaseRefreshResult {
        val original = dao.event(id) ?: error("Event is not available in the local cache")
        val date = Instant.parse(original.eventTime).atZone(zone).toLocalDate()
        val result = calendar.fetch(date, date, EconomicCalendarClient.codesFor(listOf(original.country)))
        // Existing IDs, published observations and corrected names are preserved by the merge.
        // Do not clear anything when the source returns no rows or no actual value.
        dao.mergeCalendar(result.events.map(EconomicEvent::asEntity))
        val updated = dao.event(id) ?: error("Event is not available in the local cache")
        return EventReleaseRefreshResult(updated.asExternalModel(), result.warning)
    }
}
