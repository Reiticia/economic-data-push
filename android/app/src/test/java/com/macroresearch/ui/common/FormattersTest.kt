package com.macroresearch.ui.common

import com.macroresearch.data.model.EconomicEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class FormattersTest {
    @Test
    fun formatsPercentageAndSurpriseWithoutFloatingPoint() {
        val event = event(actual = "3.2", consensus = "2.9", unit = "%")
        assertEquals("3.2%", event.value(event.actual))
        assertEquals("0.3", event.surprise()?.stripTrailingZeros()?.toPlainString())
    }

    @Test
    fun countdownHandlesUpcomingAndReleasedEvents() {
        assertEquals("01:01:01", countdown("2026-09-07T01:01:01Z", Instant.parse("2026-09-07T00:00:00Z")))
        assertEquals("已公布", countdown("2026-09-06T23:59:59Z", Instant.parse("2026-09-07T00:00:00Z")))
    }

    @Test
    fun missingConsensusHasNoSurprise() {
        assertNull(event(actual = "3.2", consensus = null, unit = "%").surprise())
    }

    private fun event(actual: String?, consensus: String?, unit: String?) = EconomicEvent(
        id = 1,
        provider = "test",
        providerId = "test-1",
        releaseGroupId = null,
        country = "United States",
        currency = "USD",
        category = "inflation",
        event = "CPI YoY",
        eventTime = "2026-09-07T01:01:01Z",
        importance = 3,
        actual = actual,
        previous = "2.8",
        consensus = consensus,
        forecast = "3.0",
        unit = unit,
        status = "released",
    )
}

