package com.macroresearch.data.remote

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
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
        val endpoint = chatEndpoint(settings.baseUrl)
        val request = { jsonMode: Boolean ->
            execute(endpoint, apiKey, payload(input, settings.model, jsonMode))
        }
        val responseText = try {
            request(true)
        } catch (rejected: ApiException) {
            // Not every OpenAI-compatible gateway accepts response_format.
            if (rejected.code in UNSUPPORTED_JSON_MODE_CODES) request(false) else throw rejected
        }
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
                throw ApiException(response.code, "AI analysis API returned HTTP ${response.code}")
            }
            return body
        }
    }

    private fun payload(input: AiAnalysisInput, model: String, jsonMode: Boolean): Map<String, Any> {
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
        return linkedMapOf<String, Any>(
            "model" to model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt(input.languageTag)),
                mapOf("role" to "user", "content" to gson.toJson(user)),
            ),
            "stream" to false,
            // Reasoning-capable models spend tokens on their thinking before the answer, so a
            // small budget truncates the JSON mid-object.
            "max_tokens" to MAX_TOKENS,
        ).apply {
            if (jsonMode) put("response_format", mapOf("type" to "json_object"))
        }
    }

    private fun systemPrompt(languageTag: String): String = buildString {
        append("You are a macro market analyst writing a post-release briefing for one economic event. ")
        append("Use only the numbers and observations in the payload; never invent data. ")
        append("Explain the causal transmission from the data surprise to asset prices step by step. ")
        append("Reply with JSON only, no reasoning, no plan, no markdown fence, no text before or after: {")
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
        val payload = briefingCandidates(responseText)
            .mapNotNull(::usableBriefing)
            .lastOrNull()
            ?: run {
                responseObserver?.invoke(responseText)
                error("AI did not return a usable analysis JSON; the reply may have been truncated or missing the JSON object")
            }
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

    /**
     * Every object in the response that could be the briefing: the payload itself, or the
     * contents of an OpenAI envelope. Reasoning prose often embeds the example schema from the
     * prompt, so candidates are validated instead of taking the first braces found.
     */
    private fun briefingCandidates(responseText: String): List<JsonObject> =
        ModelJson.values(responseText).flatMap { root ->
            val fromContents = ModelJson.contents(root).flatMap(ModelJson::objects)
            val direct = when {
                root.isJsonObject -> listOf(root.asJsonObject)
                root.isJsonArray -> root.asJsonArray.filter { it.isJsonObject }.map { it.asJsonObject }
                else -> emptyList()
            }
            direct + fromContents
        }.distinctBy { it.toString() }

    /** Rejects echo of the prompt schema, which carries placeholder values instead of a result. */
    private fun usableBriefing(payload: JsonObject): JsonObject? {
        val text = listOf("dataAnalysis", "data_analysis", "dataRead", "data_read", "analysis",
            "marketOutlook", "market_outlook", "outlook")
            .firstNotNullOfOrNull { payload.firstString(it) }
            ?.trim()
            .orEmpty()
        if (text.isBlank()) return null
        // Rejects the schema echo, whose fields hold placeholders instead of a result.
        if (PLACEHOLDER_TOKENS.any { it in text }) return null
        return payload
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

    private class ApiException(val code: Int, message: String) : IOException(message)

    companion object {
        /** Debug-only hook; the app logs raw model output when a reply cannot be parsed. */
        internal var responseObserver: ((String) -> Unit)? = null

        private const val MAX_TOKENS = 4096
        private val UNSUPPORTED_JSON_MODE_CODES = setOf(400, 404, 422)
        /** Fragments of the schema echo; a real briefing never contains them. */
        private val PLACEHOLDER_TOKENS = listOf("up|down|flat", "...", "short node names", "2-4 sentences")

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
