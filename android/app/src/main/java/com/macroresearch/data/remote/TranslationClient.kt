package com.macroresearch.data.remote

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.macroresearch.data.TranslationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class TranslationClient(
    private val client: OkHttpClient,
    private val gson: Gson,
) {
    suspend fun models(baseUrl: String, apiKey: String): List<String> =
        withContext(Dispatchers.IO) {
            val endpoint = when {
                baseUrl.endsWith("/models") -> baseUrl
                baseUrl.endsWith("/chat/completions") ->
                    baseUrl.removeSuffix("/chat/completions") + "/models"
                else -> "$baseUrl/models"
            }
            var lastError: Exception? = null
            repeat(MODEL_LIST_ATTEMPTS) { attempt ->
                try {
                    val request = Request.Builder()
                        .url(endpoint)
                        .header("Authorization", "Bearer $apiKey")
                        .header("Accept", "application/json")
                        .get()
                        .build()
                    val call = client.newCall(request)
                    call.timeout().timeout(MODEL_LIST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    call.execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            throw IOException("Models API returned HTTP ${response.code}")
                        }
                        return@withContext parseModelsResponse(body)
                    }
                } catch (error: Exception) {
                    lastError = error
                }
                if (attempt + 1 < MODEL_LIST_ATTEMPTS) {
                    Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
                }
            }
            throw lastError ?: IOException("Unable to fetch models")
        }

    suspend fun translate(
        eventNames: List<String>,
        settings: TranslationSettings,
        apiKey: String,
    ): Map<String, Pair<String, String>> = withContext(Dispatchers.IO) {
        if (eventNames.isEmpty()) return@withContext emptyMap()
        val endpoint = if (settings.baseUrl.endsWith("/chat/completions")) {
            settings.baseUrl
        } else {
            "${settings.baseUrl}/chat/completions"
        }

        val translated = linkedMapOf<String, Pair<String, String>>()
        var pending = eventNames
        var lastError: Exception? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val responseText = execute(
                    endpoint = endpoint,
                    apiKey = apiKey,
                    payload = payload(pending, settings.model, useResponseFormat = attempt == 0),
                )
                translated += parseChatResponse(responseText, pending)
                pending = pending.filterNot(translated::containsKey)
                if (pending.isEmpty()) return@withContext translated
                lastError = IOException("Translation API omitted ${pending.size} event names")
            } catch (error: Exception) {
                lastError = error
            }
            if (attempt + 1 < MAX_ATTEMPTS) Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
        }
        if (translated.isNotEmpty()) return@withContext translated
        throw lastError ?: IOException("Translation request failed")
    }

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
                throw IOException("Translation API returned HTTP ${response.code}")
            }
            return body
        }
    }

    private fun payload(
        eventNames: List<String>,
        model: String,
        useResponseFormat: Boolean,
    ): Map<String, Any> {
        val input = eventNames.mapIndexed { index, name -> mapOf("id" to index, "name" to name) }
        val values = linkedMapOf<String, Any>(
            "model" to model,
            "messages" to listOf(
                mapOf(
                    "role" to "system",
                    "content" to "Translate each macroeconomic event name into Simplified Chinese and Traditional Chinese. Preserve numbers, periods and abbreviations. Reply with JSON only: {\"translations\":[{\"id\":0,\"zhCn\":\"...\",\"zhTw\":\"...\"}]}. Include every input id exactly once.",
                ),
                mapOf("role" to "user", "content" to gson.toJson(mapOf("events" to input))),
            ),
            "stream" to false,
            "max_tokens" to 2048,
        )
        if (useResponseFormat) values["response_format"] = mapOf("type" to "json_object")
        return values
    }

    internal fun parseModelsResponse(responseText: String): List<String> {
        val root = JsonParser.parseString(responseText)
        val rows = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> root.asJsonObject.getAsJsonArray("data")
                ?: root.asJsonObject.getAsJsonArray("models")
                ?: error("Models API returned no model list")
            else -> error("Models API returned an invalid result")
        }
        val models = rows.mapNotNull { element ->
            when {
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString
                element.isJsonObject -> element.asJsonObject.firstString("id", "name", "model")
                else -> null
            }?.trim()?.takeIf(String::isNotEmpty)
        }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        check(models.isNotEmpty()) { "Models API returned an empty list" }
        return models
    }

    internal fun parseChatResponse(
        responseText: String,
        eventNames: List<String>,
    ): Map<String, Pair<String, String>> {
        val root = JsonParser.parseString(responseText)
        val content = if (root.isJsonObject && root.asJsonObject.has("translations")) {
            root
        } else {
            val choice = root.asJsonObject.getAsJsonArray("choices")
                ?.firstOrNull()?.asJsonObject
                ?: error("Translation API returned no choices")
            val message = choice.getAsJsonObject("message")
            val value = message?.get("content")
                ?.takeUnless {
                    it.isJsonNull || (it.isJsonPrimitive && it.asStringOrNull().isNullOrBlank())
                }
                ?: message?.get("reasoning_content")
                ?: choice.get("text")
                ?: error("Translation API returned no content")
            if (value.isJsonObject || value.isJsonArray) value else parseModelJson(value.asString)
        }
        return parseTranslations(content, eventNames)
    }

    private fun parseModelJson(raw: String): JsonElement {
        var content = raw.trim()
        content = content.replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```$"), "")
            .trim()
        runCatching { return JsonParser.parseString(content) }
        val objectStart = content.indexOf('{')
        val objectEnd = content.lastIndexOf('}')
        if (objectStart >= 0 && objectEnd > objectStart) {
            return JsonParser.parseString(content.substring(objectStart, objectEnd + 1))
        }
        val arrayStart = content.indexOf('[')
        val arrayEnd = content.lastIndexOf(']')
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return JsonParser.parseString(content.substring(arrayStart, arrayEnd + 1))
        }
        error("Translation API returned invalid JSON")
    }

    private fun parseTranslations(
        content: JsonElement,
        eventNames: List<String>,
    ): Map<String, Pair<String, String>> {
        val rows: JsonArray = when {
            content.isJsonArray -> content.asJsonArray
            content.isJsonObject -> content.asJsonObject.getAsJsonArray("translations")
                ?: content.asJsonObject.getAsJsonArray("items")
                ?: error("Translation API returned no translations")
            else -> error("Translation API returned an invalid result")
        }
        val translated = linkedMapOf<String, Pair<String, String>>()
        rows.forEachIndexed { index, element ->
            if (!element.isJsonObject) return@forEachIndexed
            val row = element.asJsonObject
            val id = row.intOrNull("id") ?: index
            if (id !in eventNames.indices) return@forEachIndexed
            val zhCn = row.firstString("zhCn", "zh_cn", "zhHans", "simplified", "simplifiedChinese")
            val zhTw = row.firstString("zhTw", "zh_tw", "zhHant", "traditional", "traditionalChinese")
            if (!zhCn.isNullOrBlank() && !zhTw.isNullOrBlank()) {
                translated[eventNames[id]] = zhCn.trim() to zhTw.trim()
            }
        }
        check(translated.isNotEmpty()) { "Translation API returned no usable translations" }
        return translated
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val MODEL_LIST_ATTEMPTS = 2
        private const val MODEL_LIST_TIMEOUT_SECONDS = 25L
        private const val RETRY_DELAY_MS = 1_500L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun JsonElement.asStringOrNull(): String? =
            takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

        private fun JsonObject.intOrNull(name: String): Int? =
            runCatching { get(name)?.takeUnless { it.isJsonNull }?.asInt }.getOrNull()

        private fun JsonObject.firstString(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
            runCatching { get(name)?.takeUnless { it.isJsonNull }?.asString }.getOrNull()
        }
    }
}
