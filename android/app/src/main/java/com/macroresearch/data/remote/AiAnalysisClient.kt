package com.macroresearch.data.remote

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.macroresearch.data.TranslationSettings
import com.macroresearch.data.model.EconomicEvent
import com.macroresearch.data.model.ExpectedReaction
import com.macroresearch.data.model.MarketReaction
import com.macroresearch.data.model.TransmissionStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the model needs to brief a single released event. */
data class AiAnalysisInput(
    val event: EconomicEvent,
    val macroSignal: String,
    val rawSurprise: String?,
    val expectedReactions: List<ExpectedReaction>,
    val observedReactions: List<MarketReaction>,
    val languageTag: String,
)

/** Parsed model output before it is persisted. */
data class AiAnalysisDraft(
    val chain: List<TransmissionStep>,
    val dataAnalysis: String,
    val marketOutlook: String,
    val risks: String?,
)

/**
 * Generates a post-release briefing on the user's own OpenAI-compatible endpoint:
 * a transmission chain from the data surprise to asset prices, a read of the released
 * numbers and a market outlook. The caller persists the result.
 */
class AiAnalysisClient(private val client: OkHttpClient, private val gson: Gson) {
    suspend fun analyze(
        input: AiAnalysisInput,
        settings: TranslationSettings,
        apiKey: String,
    ): AiAnalysisDraft = withContext(Dispatchers.IO) {
        val responseText = execute(chatEndpoint(settings.baseUrl), apiKey, payload(input, settings.model))
        parseResponse(responseText)
    }

    fun encodeChain(chain: List<TransmissionStep>): String = gson.toJson(chain)

    fun decodeChain(json: String): List<TransmissionStep> = runCatching {
        gson.fromJson(json, Array<TransmissionStep>::class.java)?.toList().orEmpty()
    }.getOrDefault(emptyList())

    private fun chatEndpoint(baseUrl: String): String =
        if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"

    private fun execute(endpoint: String, apiKey: String, payload: Map<String, Any>): String {
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("AI analysis API returned HTTP ${response.code}")
            }
            return body
        }
    }

    private fun payload(input: AiAnalysisInput, model: String): Map<String, Any> {
        // The briefing is read next to cards that render device-local time, so hand the model
        // the local clock as well as the precise instant.
        val zone = ZoneId.systemDefault()
        val releasedLocal = runCatching {
            Instant.parse(input.event.eventTime).atZone(zone).format(LOCAL_TIME_FORMAT)
        }.getOrNull()
        val event = mapOf(
            "name" to input.event.event,
            "country" to input.event.country,
            "currency" to input.event.currency,
            "importance" to input.event.importance,
            "releasedAt" to input.event.eventTime,
            "releasedAtLocal" to releasedLocal,
            "timeZone" to zone.id,
            "status" to input.event.status,
            "unit" to input.event.unit,
            "actual" to input.event.actual,
            "consensus" to input.event.consensus,
            "forecast" to input.event.forecast,
            "previous" to input.event.previous,
        )
        val expected = input.expectedReactions.map {
            mapOf("symbol" to it.symbol, "ruleDirection" to it.direction)
        }
        val observed = input.observedReactions.map {
            mapOf(
                "symbol" to it.symbol,
                "baseline" to it.baselinePrice,
                "unit" to it.reactionUnit,
                "change1m" to it.change1m,
                "change5m" to it.change5m,
                "change15m" to it.change15m,
                "change30m" to it.change30m,
                "change60m" to it.change60m,
            )
        }
        val user = mapOf(
            "event" to event,
            "ruleSignal" to input.macroSignal,
            "rawSurprise" to input.rawSurprise,
            "expectedReactions" to expected,
            "observedReactions" to observed,
        )
        return linkedMapOf(
            "model" to model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt(input.languageTag)),
                mapOf("role" to "user", "content" to gson.toJson(user)),
            ),
            "stream" to false,
            "max_tokens" to 2048,
        )
    }

    private fun systemPrompt(languageTag: String): String = buildString {
        append("You are a macro market analyst writing a post-release briefing for one economic event. ")
        append("Use only the numbers and observations in the payload; never invent data. ")
        append("Explain the causal transmission from the data surprise to asset prices step by step. ")
        append("Reply with JSON only: {")
        append("\"chain\":[{\"from\":\"...\",\"to\":\"...\",\"direction\":\"up|down|flat\",\"rationale\":\"...\"}],")
        append("\"dataAnalysis\":\"...\",\"marketOutlook\":\"...\",\"risks\":\"...\"}. ")
        append("chain is ordered from the surprise to the final asset reaction using short node names ")
        append("(for example \"CPI surprise\", \"real yields\", \"US dollar\", \"gold\"). ")
        append("dataAnalysis: 2-4 sentences comparing actual with consensus, forecast and previous, ")
        append("including revisions or caveats. marketOutlook: 2-4 sentences on how rates, the dollar and ")
        append("risk assets are likely to trade next and what would invalidate the view. ")
        append("risks: 1-2 sentences on the main risk to this chain. State uncertainty explicitly. ")
        append("Quote release times in the reader's local time zone given by timeZone. ")
        append("Write in ").append(outputLanguage(languageTag)).append(". ")
        append("This is descriptive analysis, not investment advice.")
    }

    private fun outputLanguage(languageTag: String): String {
        val tag = languageTag.lowercase(Locale.ROOT)
        return when {
            tag.startsWith("zh-hant") || tag.startsWith("zh-tw") || tag.startsWith("zh-hk") ->
                "Traditional Chinese"
            tag.startsWith("zh") -> "Simplified Chinese"
            else -> "English"
        }
    }

    internal fun parseResponse(responseText: String): AiAnalysisDraft {
        val root = JsonParser.parseString(responseText)
        val content = extractContent(root)
        val payload = content.takeIf(JsonElement::isJsonObject)?.asJsonObject
            ?: error("AI analysis response is not a JSON object")
        val chain = parseChain(payload.firstArray("chain", "transmissionChain", "transmission_chain", "steps", "links"))
        val dataAnalysis = payload
            .firstString("dataAnalysis", "data_analysis", "dataRead", "data_read", "analysis")
            .orEmpty().trim()
        val marketOutlook = payload
            .firstString("marketOutlook", "market_outlook", "marketView", "market_view", "outlook")
            .orEmpty().trim()
        val risks = payload.firstString("risks", "risk", "caveats")?.trim()?.takeIf(String::isNotEmpty)
        check(dataAnalysis.isNotEmpty() || marketOutlook.isNotEmpty()) {
            "AI returned no usable analysis"
        }
        return AiAnalysisDraft(chain, dataAnalysis, marketOutlook, risks)
    }

    private fun extractContent(root: JsonElement): JsonElement {
        if (root.isJsonObject && (root.asJsonObject.has("chain") || root.asJsonObject.has("dataAnalysis"))) {
            return root
        }
        val choice = root.asJsonObject.getAsJsonArray("choices")
            ?.firstOrNull()?.asJsonObject
            ?: error("AI analysis API returned no choices")
        val message = choice.getAsJsonObject("message")
        val value = message?.get("content")
            ?.takeUnless { it.isJsonNull || (it.isJsonPrimitive && it.asStringOrNull().isNullOrBlank()) }
            ?: message?.get("reasoning_content")
            ?: choice.get("text")
            ?: error("AI analysis API returned no content")
        return if (value.isJsonObject || value.isJsonArray) value else parseModelJson(value.asString)
    }

    private fun parseModelJson(raw: String): JsonElement {
        var content = raw.trim()
        content = content.replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```$"), "")
            .trim()
        runCatching { return JsonParser.parseString(content) }
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return JsonParser.parseString(content.substring(start, end + 1))
        }
        error("AI analysis API returned invalid JSON")
    }

    private fun parseChain(rows: List<JsonElement>?): List<TransmissionStep> = rows.orEmpty().mapNotNull { element ->
        if (!element.isJsonObject) return@mapNotNull null
        val row = element.asJsonObject
        val from = row.firstString("from", "fromNode", "from_node", "source", "cause")?.trim().orEmpty()
        val to = row.firstString("to", "toNode", "to_node", "target", "effect")?.trim().orEmpty()
        if (from.isEmpty() || to.isEmpty()) return@mapNotNull null
        TransmissionStep(
            from = from,
            to = to,
            direction = normalizeDirection(row.firstString("direction", "dir", "sign")),
            rationale = row.firstString("rationale", "reason", "why", "explanation").orEmpty().trim(),
        )
    }

    private fun normalizeDirection(raw: String?): String {
        val value = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when {
            value.isEmpty() -> "flat"
            value.startsWith("up") || value in setOf("higher", "rise", "rising", "increase", "bullish", "↑", "+") -> "up"
            value.startsWith("down") || value in setOf("lower", "fall", "falling", "decrease", "bearish", "↓", "-") -> "down"
            else -> "flat"
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val LOCAL_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        private fun JsonElement.asStringOrNull(): String? =
            takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

        private fun JsonObject.firstString(vararg names: String): String? =
            names.firstNotNullOfOrNull { name ->
                runCatching { get(name)?.takeUnless { it.isJsonNull }?.asString }.getOrNull()
            }

        private fun JsonObject.firstArray(vararg names: String): List<JsonElement>? =
            names.firstNotNullOfOrNull { name ->
                runCatching { get(name)?.takeUnless { it.isJsonNull }?.asJsonArray?.toList() }.getOrNull()
            }
    }
}
