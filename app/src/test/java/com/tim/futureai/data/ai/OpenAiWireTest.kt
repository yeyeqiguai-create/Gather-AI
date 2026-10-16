package com.tim.futureai.data.ai

import com.tim.futureai.core.common.AppError
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.MessageRole
import com.tim.futureai.domain.model.ChatAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader
import java.io.File

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

    @Test
    fun buildRequestBody_streamEnablesServerSentEvents() {
        val body = OpenAiWire.buildRequestBody(emptyList(), "m", stream = true)
        assertTrue(body.contains("\"stream\":true"))
    }

    @Test
    fun buildRequestBody_includesTextAttachmentAndEncodesImageAsDataUrl() {
        val image = File.createTempFile("gather-image", ".png").apply { writeBytes("hello".toByteArray()) }
        try {
            val body = OpenAiWire.buildRequestBody(listOf(ChatMessage(
                sessionId = "s", role = MessageRole.USER, content = "分析这些内容",
                attachments = listOf(
                    ChatAttachment(name = "notes.txt", mimeType = "text/plain", textContent = "文档正文"),
                    ChatAttachment(name = "photo.png", mimeType = "image/png", localPath = image.absolutePath),
                ),
            )), "vision-model")
            assertTrue(body.contains("type"))
            assertTrue(body.contains("文档正文"))
            assertTrue(body.contains("data:image/png;base64,aGVsbG8="))
        } finally { image.delete() }
    }

    @Test
    fun encodeBase64_matchesRfc4648Padding() {
        assertEquals("TWFu", OpenAiWire.encodeBase64("Man".toByteArray()))
        assertEquals("TQ==", OpenAiWire.encodeBase64("M".toByteArray()))
    }

    @Test
    fun extractTokenUsage_onlyReportsCompleteProviderCounts() {
        val usage = OpenAiWire.extractTokenUsage("""{"usage":{"prompt_tokens":12,"completion_tokens":7,"total_tokens":19}}""")
        assertEquals(12, usage?.promptTokens)
        assertEquals(7, usage?.completionTokens)
        assertEquals(null, OpenAiWire.extractTokenUsage("""{"usage":{"total_tokens":19}}"""))
    }

    @Test
    fun streamReply_emitsDeltasAndStopsAtDone() {
        val events = """
            : keep-alive
            data: {"choices":[{"delta":{"content":"Hello"}}]}

            data: {"choices":[{"delta":{"content":" world"}}]}

            data: [DONE]

        """.trimIndent()
        val chunks = mutableListOf<String>()
        val complete = OpenAiWire.streamReply(BufferedReader(StringReader(events))) { chunks += it }
        assertTrue(complete)
        assertEquals(listOf("Hello", " world"), chunks)
    }

    @Test
    fun streamReply_reportsUnexpectedEndWithoutDone() {
        val chunks = mutableListOf<String>()
        val complete = OpenAiWire.streamReply(BufferedReader(StringReader(
            "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n",
        ))) { chunks += it }
        assertTrue(!complete)
        assertEquals(listOf("partial"), chunks)
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
