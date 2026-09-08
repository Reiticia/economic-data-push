package com.macroresearch.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalEvidenceTest {
    private val base = """
        "id":1,"eventId":2,"rawSurprise":"0.1","macroSignal":"hawkish",
        "expectedReactions":[],"observedReactions":[],"comparisons":[],"summary":"test",
        "createdAt":"2026-06-08T00:00:00Z","updatedAt":"2026-06-08T00:00:00Z"
    """.trimIndent()

    @Test
    fun liveReportsRemainCompatible() {
        assertNull(Gson().fromJson("{$base}", AnalysisReport::class.java).historical)
    }

    @Test
    fun missingAndPartialHistoryAreNotReadAsFullCoverage() {
        val json = """{$base,"historical":{
            "fetchedAt":"2026-09-08T00:00:00Z","revisedDataPossible":true,
            "coverage":[
                {"symbol":"gold","source":"yahoo","intervalSeconds":null,"status":"unavailable",
                 "reason":"retention_limit","availableHorizons":[],"baselineTime":null,"sampleTimes":{}},
                {"symbol":"bitcoin","source":"binance","intervalSeconds":60,"status":"partial",
                 "reason":"missing_samples","availableHorizons":[5],"baselineTime":"2026-06-08T12:29:00Z",
                 "sampleTimes":{"5":"2026-06-08T12:35:00Z"}}
            ]}}
        """
        val evidence = Gson().fromJson(json, AnalysisReport::class.java).historical!!
        assertTrue(evidence.revisedDataPossible)
        assertEquals("retention_limit", evidence.coverage[0].reason)
        assertTrue(evidence.coverage[0].availableHorizons.isEmpty())
        assertEquals(listOf(5), evidence.coverage[1].availableHorizons)
        assertEquals("2026-06-08T12:35:00Z", evidence.coverage[1].sampleTimes["5"])
    }
}
