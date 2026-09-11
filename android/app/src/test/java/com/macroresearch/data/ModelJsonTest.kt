package com.macroresearch.data

import com.google.gson.Gson
import com.macroresearch.data.remote.AiAnalysisClient
import com.macroresearch.data.remote.ModelJson
import com.macroresearch.data.remote.TranslationClient
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the reported "AI analysis always fails" bug: the gateway returned the
 * model's reasoning text (containing the prompt's example schema) truncated at the token limit,
 * and the old parser sliced the first `{` to the last `}` into malformed JSON.
 */
class ModelJsonTest {
    private val briefing = """
        {"chain":[{"from":"核心CPI超预期","to":"美债收益率","direction":"up","rationale":"收益率上行"}],
         "dataAnalysis":"核心CPI月率0.3%高于预期0.2%，读数偏鹰。","marketOutlook":"美元与短端收益率短期偏强。",
         "risks":"若后续数据走弱，该判断失效。"}
    """.trimIndent()

    @Test
    fun parsingAcceptsPlainFencedProseAndMultilinePayloads() {
        assertTrue(ModelJson.value(briefing)!!.isJsonObject)
        assertTrue(ModelJson.value("```json\n$briefing\n```")!!.isJsonObject)
        assertTrue(ModelJson.value("Here is the briefing:\n$briefing\nHope this helps.")!!.isJsonObject)
        // Models emit literal newlines inside JSON strings; the reader must stay lenient.
        val multiline = "{\"dataAnalysis\":\"first line\nsecond line\",\"marketOutlook\":\"ok for now\"}"
        assertEquals("first line\nsecond line", ModelJson.value(multiline)!!.asJsonObject.get("dataAnalysis").asString)
        // Truncated tails are rejected instead of being sliced into broken JSON.
        assertNull(ModelJson.value("{\"chain\":[{\"from\":\"a\""))
        assertNull(ModelJson.value("no json here at all"))
    }

    @Test
    fun envelopeAndBarePayloadAreHandledTheSameWay() {
        // Build the envelope through Gson so the nested payload is escaped like a real reply.
        val fenced = listOf("```json", briefing, "```").joinToString("\n")
        val envelope = Gson().toJson(
            mapOf("choices" to listOf(mapOf("message" to mapOf("content" to fenced)))),
        )
        val client = AiAnalysisClient(OkHttpClient(), Gson())
        assertEquals(client.parseResponse(envelope).dataAnalysis, client.parseResponse(briefing).dataAnalysis)
    }

    @Test
    fun reasoningEchoIsRejectedInFavourOfTheRealAnswer() {
        // The model plans out loud, quotes the schema from the prompt, then answers.
        val response = """
            I need to write the JSON only. Structure:
            {"chain":[{"from":"...","to":"...","direction":"up|down|flat","rationale":"..."}],"dataAnalysis":"...","marketOutlook":"...","risks":"..."}
            Final answer:
            $briefing
        """.trimIndent()
        val draft = AiAnalysisClient(OkHttpClient(), Gson()).parseResponse(response)
        assertEquals("核心CPI月率0.3%高于预期0.2%，读数偏鹰。", draft.dataAnalysis)
        assertEquals(1, draft.chain.size)
    }

    @Test
    fun reasoningOnlyRepliesFailWithAnActionableMessage() {
        // The exact shape captured from the device: reasoning prose plus the schema example,
        // cut off mid-sentence by the token limit.
        val truncated = """
            The chain should be ordered from surprise to final asset reaction.
            {"chain":[{"from":"...","to":"...","direction":"up|down|flat","rationale":"..."}],"dataAnalysis":"...","marketOutlook":"...","risks":"..."}
            Let me design the chain:
            1. {"from":"核心CPI超预期(0.3% vs 0.2%)","to":"美联储降息预期","direction":"down","rationale":"市场下修降
        """.trimIndent()
        val failure = runCatching { AiAnalysisClient(OkHttpClient(), Gson()).parseResponse(truncated) }
        assertTrue(failure.isFailure)
        val message = failure.exceptionOrNull()!!.message.orEmpty()
        assertTrue(message, message.contains("usable analysis JSON"))
        assertTrue(message, !message.contains("MalformedJsonException"))
    }

    @Test
    fun translationContentSurvivesProseAndLists() {
        val client = TranslationClient(OkHttpClient(), Gson())
        // The API envelope carries the model text; that text may wrap the JSON in prose.
        val arrayInProse = """{"choices":[{"message":{"content":"Sure: [{\"id\":0,\"zhCn\":\"现房销售\",\"zhTw\":\"成屋銷售\"}] done"}}]}"""
        assertEquals(
            "现房销售",
            client.parseChatResponse(arrayInProse, listOf("Existing Home Sales")).getValue("Existing Home Sales").first,
        )
        val objectInProse = """{"choices":[{"message":{"content":"Here you go: {\"translations\":[{\"id\":0,\"zhCn\":\"非农就业\",\"zhTw\":\"非農就業\"}]}"}}]}"""
        assertEquals(
            "非农就业",
            client.parseChatResponse(objectInProse, listOf("Nonfarm Payrolls")).getValue("Nonfarm Payrolls").first,
        )
    }
}
