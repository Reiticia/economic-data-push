package com.macroresearch.data.remote

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.Strictness
import java.io.StringReader

/**
 * Recovers the JSON a model was asked to produce.
 *
 * OpenAI-compatible endpoints may wrap the answer in markdown fences, prepend reasoning prose,
 * embed the example schema from the prompt, or cut the answer off at the token limit. Slicing
 * "first brace to last brace" then yields malformed JSON, so candidates are scanned with
 * string/escape awareness and every candidate is parsed leniently before use.
 */
internal object ModelJson {
    /** First JSON object or array that parses. */
    fun value(raw: String): JsonElement? = values(raw).firstOrNull()

    /**
     * Every JSON object/array found in [raw], in order of appearance. Primitives are ignored:
     * a lenient reader happily parses the first word of prose as a bare string, which would
     * otherwise shadow the JSON that follows it.
     */
    fun values(raw: String): List<JsonElement> {
        val text = stripFences(raw)
        val parsed = mutableListOf<JsonElement>()
        // The payload may already be a single JSON document.
        parse(text)?.let { parsed += it }
        slices(text).forEach { slice ->
            if (slice != text) parse(slice)?.let { parsed += it }
        }
        return parsed.distinctBy { it.toString() }
    }

    /**
     * Text payloads carried by an OpenAI chat envelope. Falls back to the value itself so a
     * caller can treat an envelope and a bare payload the same way.
     */
    fun contents(root: JsonElement): List<String> = when {
        root.isJsonObject && root.asJsonObject.has("choices") -> {
            val choice = root.asJsonObject.getAsJsonArray("choices")?.firstOrNull()?.takeIf { it.isJsonObject }
                ?.asJsonObject
            val message = choice?.getAsJsonObject("message")
            listOfNotNull(
                message?.get("content"),
                message?.get("reasoning_content"),
                choice?.get("text"),
            ).mapNotNull { element ->
                when {
                    element.isJsonNull -> null
                    element.isJsonPrimitive && element.asJsonPrimitive.isString ->
                        element.asString.takeIf(String::isNotBlank)
                    element.isJsonObject || element.isJsonArray -> element.toString()
                    else -> null
                }
            }
        }
        root.isJsonPrimitive && (root as JsonPrimitive).isString -> listOf(root.asString)
        else -> listOf(root.toString())
    }

    /** Every JSON object that appears inside [text], including the text itself when it is one. */
    fun objects(text: String): List<JsonObject> = values(text).flatMap { element ->
        when {
            element.isJsonObject -> listOf(element.asJsonObject)
            element.isJsonArray -> element.asJsonArray.filter { it.isJsonObject }.map { it.asJsonObject }
            else -> emptyList()
        }
    }

    private fun stripFences(raw: String): String = raw.trim()
        .replace(Regex("^```[a-zA-Z]*\\s*"), "")
        .replace(Regex("\\s*```\\s*$"), "")
        .trim()

    /** Balanced `{...}` / `[...]` spans. Truncated tails are dropped instead of mis-sliced. */
    private fun slices(text: String): List<String> {
        val found = mutableListOf<String>()
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '{' || char == '[') {
                val end = matchingEnd(text, index)
                if (end > index) {
                    found += text.substring(index, end + 1)
                    index = end + 1
                    continue
                }
            }
            index++
        }
        return found
    }

    private fun matchingEnd(text: String, start: Int): Int {
        val open = text[start]
        val close = if (open == '{') '}' else ']'
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }

    private fun parse(json: String): JsonElement? = runCatching {
        val reader = JsonReader(StringReader(json))
        // Lenient mode accepts the shapes models actually emit, instead of failing with
        // "Use JsonReader.setStrictness(Strictness.LENIENT)".
        reader.setStrictness(Strictness.LENIENT)
        JsonParser.parseReader(reader)
    }.getOrNull()?.takeIf { it.isJsonObject || it.isJsonArray }
}
