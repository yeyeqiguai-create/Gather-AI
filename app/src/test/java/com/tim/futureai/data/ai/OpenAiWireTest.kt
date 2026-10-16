package com.tim.futureai.data.ai

import com.tim.futureai.core.common.AppError
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiWireTest {

    // ---- 请求体构造 ----

    @Test
    fun buildRequestBody_containsModelAndRoles() {
        val body = OpenAiWire.buildRequestBody(
            listOf(
                ChatMessage(sessionId = "s", role = MessageRole.SYSTEM, content = "You are helpful."),
                ChatMessage(sessionId = "s", role = MessageRole.USER, content = "Hi \"there\"\nnext"),
            ),
            model = "gpt-test",
        )
        assertTrue(body.contains("\"model\":\"gpt-test\""))
        assertTrue(body.contains("\"role\":\"system\""))
        assertTrue(body.contains("\"role\":\"user\""))
        // 特殊字符已转义
        assertTrue(body.contains("\\\"there\\\""))
        assertTrue(body.contains("\\n"))
    }

    @Test
    fun buildRequestBody_roleLowercaseMapping() {
        val body = OpenAiWire.buildRequestBody(
            listOf(ChatMessage(sessionId = "s", role = MessageRole.ASSISTANT, content = "ok")),
            model = "m",
        )
        assertTrue(body.contains("\"role\":\"assistant\""))
    }

    // ---- 响应解析 ----

    @Test
    fun extractReplyContent_happyPath() {
        val body = """
            {"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}
        """.trimIndent()
        assertEquals("Hello!", OpenAiWire.extractReplyContent(body))
    }

    @Test
    fun extractReplyContent_contentWithEscapes() {
        val body = """{"choices":[{"message":{"content":"line1\nline2 \"q\""}}]}"""
        assertEquals("line1\nline2 \"q\"", OpenAiWire.extractReplyContent(body))
    }

    @Test(expected = OpenAiWire.ResponseParseException::class)
    fun extractReplyContent_rejectsBlankAssistantText() {
        OpenAiWire.extractReplyContent("""{"choices":[{"message":{"content":"  "}}]}""")
    }

    @Test(expected = OpenAiWire.ResponseParseException::class)
    fun extractReplyContent_missingChoicesThrows() {
        OpenAiWire.extractReplyContent("""{"id":"x"}""")
    }

    @Test(expected = OpenAiWire.ResponseParseException::class)
    fun extractReplyContent_emptyChoicesThrows() {
        OpenAiWire.extractReplyContent("""{"choices":[]}""")
    }

    @Test(expected = OpenAiWire.ResponseParseException::class)
    fun extractReplyContent_malformedJsonThrows() {
        OpenAiWire.extractReplyContent("not json")
    }

    @Test(expected = OpenAiWire.ResponseParseException::class)
    fun extractReplyContent_missingContentThrows() {
        OpenAiWire.extractReplyContent("""{"choices":[{"message":{"role":"assistant"}}]}""")
    }

    // ---- 错误消息提取 ----

    @Test
    fun extractErrorMessage_readsOpenAiStyleError() {
        val body = """{"error":{"message":"Incorrect API key","type":"invalid_request_error"}}"""
        assertEquals("Incorrect API key", OpenAiWire.extractErrorMessage(body))
    }

    @Test
    fun extractErrorMessage_fallsBackToRawText() {
        assertEquals("Bad Gateway", OpenAiWire.extractErrorMessage("Bad Gateway"))
    }

    // ---- HTTP 状态码映射 ----

    @Test
    fun mapHttpError_401_mapsToAuth() {
        val e = OpenAiCompatProvider.mapHttpError(401, "bad key") as AppError.Auth
        assertEquals("bad key", e.message)
    }

    @Test
    fun mapHttpError_403_mapsToAuth() {
        assertTrue(OpenAiCompatProvider.mapHttpError(403, "x") is AppError.Auth)
    }

    @Test
    fun mapHttpError_429_mapsToRateLimited() {
        assertTrue(OpenAiCompatProvider.mapHttpError(429, "slow down") is AppError.RateLimited)
    }

    @Test
    fun mapHttpError_5xx_mapsToServerWithCode() {
        val e = OpenAiCompatProvider.mapHttpError(503, "overloaded") as AppError.Server
        assertEquals(503, e.httpCode)
    }

    @Test
    fun mapHttpError_otherCode_mapsToServer() {
        val e = OpenAiCompatProvider.mapHttpError(400, "bad request") as AppError.Http
        assertEquals(400, e.httpCode)
        assertTrue(e.message!!.contains("400"))
    }
}
