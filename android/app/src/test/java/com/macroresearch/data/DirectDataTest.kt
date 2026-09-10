package com.macroresearch.data

import com.macroresearch.data.model.EconomicEvent
import com.google.gson.Gson
import com.macroresearch.data.remote.TradingEconomicsClient
import com.macroresearch.data.remote.TranslationClient
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DirectDataTest {
    @Test
    fun calendarHtmlIsParsedIntoStableClientSideEvents() {
        val html = """
            <table><tbody>
              <tr data-id="us-cpi-202609" data-event="Inflation Rate YoY"
                  data-country="United States" data-category="Inflation"
                  data-date="2026-09-10T12:30:00Z" data-importance="3">
                <td class="calendar-actual">3.2%</td>
                <td class="calendar-previous">2.8%</td>
                <td class="calendar-consensus">2.9%</td>
                <td class="calendar-forecast">3.0%</td>
              </tr>
              <tr data-id="mobile-us-cpi-202609" data-event="Inflation Rate YoY"
                  data-country="United States" data-category="Inflation"
                  data-date="2026-09-10T12:30:00Z" data-importance="3">
                <td class="calendar-actual">3.2%</td>
                <td class="calendar-previous">2.8%</td>
                <td class="calendar-consensus">2.9%</td>
              </tr>
            </tbody></table>
        """.trimIndent()
        val client = TradingEconomicsClient(OkHttpClient())
        val first = client.parse(html, Instant.parse("2026-09-10T13:00:00Z")).single()
        val second = client.parse(html, Instant.parse("2026-09-10T13:00:00Z")).single()

        assertEquals(first.id, second.id)
        assertEquals("3.2", first.actual)
        assertEquals("2.9", first.consensus)
        assertEquals("%", first.unit)
        assertEquals("released", first.status)
        assertEquals(3, first.importance)
    }

    @Test
    fun localAnalysisAppliesIndicatorDirectionWithoutServer() {
        val event = event("Unemployment Rate", "4.3", "4.1")
        val report = LocalAnalysisEngine().analyze(event, emptyList())

        assertTrue(report.macroSignal.contains("dovish"))
        assertEquals("0.2", report.rawSurprise)
        assertEquals("up", report.expectedReactions.first { it.symbol == "nasdaq100" }.direction)
    }

    @Test
    fun openAiCompatibleModelListsAreParsedAndSorted() {
        val client = TranslationClient(OkHttpClient(), Gson())
        val models = client.parseModelsResponse(
            """{"object":"list","data":[{"id":"z-model"},{"id":"a-model"},{"id":"a-model"}]}""",
        )

        assertEquals(listOf("a-model", "z-model"), models)
        assertEquals(
            listOf("model-one", "model-two"),
            client.parseModelsResponse("""{"models":["model-two",{"name":"model-one"}]}"""),
        )
    }

    @Test
    fun compatibleTranslationResponsesAcceptFencesAndSnakeCaseKeys() {
        val client = TranslationClient(OkHttpClient(), Gson())
        val response = """
            {"choices":[{"message":{"content":"```JSON\n{\"translations\":[{\"id\":0,\"zh_cn\":\"现房销售\",\"zh_tw\":\"成屋銷售\"}]}\n```"}}]}
        """.trimIndent()

        val translated = client.parseChatResponse(response, listOf("Existing Home Sales"))

        assertEquals("现房销售", translated.getValue("Existing Home Sales").first)
        assertEquals("成屋銷售", translated.getValue("Existing Home Sales").second)
    }

    @Test
    fun translationEndpointMustBeHttpsAndContainsNoCredentials() {
        assertEquals(
            "https://api.example.com/v1",
            TranslationPreferences.normalizeBaseUrl(" https://api.example.com/v1/ "),
        )
        assertTrue(runCatching { TranslationPreferences.normalizeBaseUrl("http://api.example.com") }.isFailure)
        assertTrue(runCatching { TranslationPreferences.normalizeBaseUrl("https://key@api.example.com/v1") }.isFailure)
    }

    private fun event(name: String, actual: String, consensus: String) = EconomicEvent(
        id = 1,
        provider = "test",
        providerId = "test",
        releaseGroupId = null,
        country = "United States",
        currency = "USD",
        category = "employment",
        event = name,
        eventTime = "2026-09-10T12:30:00Z",
        importance = 3,
        actual = actual,
        previous = null,
        consensus = consensus,
        forecast = null,
        unit = "%",
        status = "released",
    )
}
