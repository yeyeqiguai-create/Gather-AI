package com.tim.futureai.data.ai

import com.tim.futureai.core.common.AppError
import com.tim.futureai.core.common.AppResult
import com.tim.futureai.core.common.NetworkFailureKind
import com.tim.futureai.domain.ai.AiChatConfig
import com.tim.futureai.domain.ai.AiRequestPhase
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.MessageRole
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CopyOnWriteArrayList

class OpenAiCompatProviderTest {
    private val apiKey = "test-key-do-not-log"
    private val messages = listOf(ChatMessage(sessionId = "session", role = MessageRole.USER, content = "hello"))

    @Test fun complete_postsRequestAndReturnsAssistantReply() = mockResponse(200, """{"choices":[{"message":{"content":"hello from mock"}}]}""") { server ->
        val provider = provider(server)
        val result = runBlocking { provider.complete(messages) }
        assertEquals("hello from mock", (result as AppResult.Success).data)
        assertTrue(server.request.get().contains("Authorization: Bearer $apiKey"))
        assertTrue(server.request.get().contains("\"model\":\"mock-model\""))
        assertTrue(server.request.get().contains("\"content\":\"hello\""))
    }

    @Test fun complete_reportsRequestAndReplyPhases() = mockResponse(200, """{"choices":[{"message":{"content":"reply"}}]}""") { server ->
        val phases = CopyOnWriteArrayList<AiRequestPhase>()
        val result = runBlocking { provider(server).complete(messages) { phases += it } }
        assertTrue(result is AppResult.Success)
        assertEquals(listOf(AiRequestPhase.REQUESTING, AiRequestPhase.RECEIVING_REPLY), phases.toList())
    }

    @Test fun stream_postsSseRequestAndEmitsDeltasInOrder() = mockResponse(
        200,
        "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n" +
            "data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n\n" +
            "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":8,\"completion_tokens\":2}}\n\n" +
            "data: [DONE]\n\n",
        contentType = "text/event-stream",
    ) { server ->
        val chunks = CopyOnWriteArrayList<String>()
        val tokenUsage = AtomicReference<com.tim.futureai.domain.ai.AiTokenUsage?>(null)
        val result = runBlocking {
            provider(server).streamWithUsage(messages, onDelta = chunks::add, onUsage = tokenUsage::set)
        }
        assertEquals("hello world", (result as AppResult.Success).data)
        assertEquals(listOf("hello", " world"), chunks.toList())
        assertEquals(8, tokenUsage.get()?.promptTokens)
        assertEquals(2, tokenUsage.get()?.completionTokens)
        assertTrue(server.request.get().contains("Accept: text/event-stream"))
        assertTrue(server.request.get().contains("\"stream\":true"))
    }

    @Test fun stream_preservesPartialOutputWhenServerEndsEarly() = mockResponse(
        200,
        "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n",
        contentType = "text/event-stream",
    ) { server ->
        val chunks = mutableListOf<String>()
        val result = runBlocking { provider(server).stream(messages, onDelta = chunks::add) }
        assertEquals(listOf("partial"), chunks)
        assertEquals(NetworkFailureKind.CONNECTION, ((result as AppResult.Failure).error as AppError.Network).kind)
    }

    @Test fun testConnectionChecksModelsEndpointWithoutGeneratingCompletion() = mockResponse(200, "{}") { server ->
        val result = runBlocking { provider(server).testConnection() }
        assertTrue(result is AppResult.Success)
        assertTrue(server.request.get().startsWith("GET /models HTTP/1.1"))
        assertTrue(server.request.get().contains("Authorization: Bearer $apiKey"))
    }

    @Test fun complete_maps401WithoutExposingApiKey() = assertHttpError(401, """{"error":{"message":"invalid key $apiKey"}}""") { error ->
        assertTrue(error is AppError.Auth)
        assertEquals(401, (error as AppError.Auth).httpCode)
        assertFalse(error.message.orEmpty().contains(apiKey))
    }

    @Test fun complete_maps429() = assertHttpError(429, """{"error":{"message":"slow down"}}""") {
        assertTrue(it is AppError.RateLimited)
        assertEquals(429, (it as AppError.RateLimited).httpCode)
    }

    @Test fun complete_maps5xxAndRedactsEchoedKey() = assertHttpError(503, "upstream rejected $apiKey") { error ->
        assertTrue(error is AppError.Server)
        assertEquals(503, (error as AppError.Server).httpCode)
        assertFalse(error.message.orEmpty().contains(apiKey))
    }

    @Test fun complete_mapsMalformedSuccessJsonToParseError() = mockResponse(200, "not-json") { server ->
        val result = runBlocking { provider(server).complete(messages) }
        assertTrue((result as AppResult.Failure).error is AppError.Parse)
    }

    @Test fun complete_mapsConnectionFailureToNetworkError() {
        val socket = ServerSocket(0)
        val port = socket.localPort
        socket.close()
        val result = runBlocking { provider("http://127.0.0.1:$port").complete(messages) }
        val error = (result as AppResult.Failure).error as AppError.Network
        assertEquals(NetworkFailureKind.CONNECTION, error.kind)
        assertFalse(error.message.orEmpty().contains(apiKey))
    }

    @Test fun complete_mapsReadTimeoutToNetworkError() {
        val server = MockServer { socket, _ ->
            readRequest(socket)
            Thread.sleep(500)
        }
        try {
            val result = runBlocking {
                OpenAiCompatProvider(
                    configProvider = { AiChatConfig(server.url, "mock-model", apiKey) },
                    connectTimeoutMs = 1_000,
                    readTimeoutMs = 100,
                ).complete(messages)
            }
            val error = (result as AppResult.Failure).error as AppError.Network
            assertEquals(NetworkFailureKind.TIMEOUT, error.kind)
        } finally {
            server.close()
        }
    }

    @Test fun complete_mapsInvalidBaseUrlToConfigurationErrorWithoutEchoingConfig() {
        val result = runBlocking {
            OpenAiCompatProvider({ AiChatConfig("not a url $apiKey", "mock-model", apiKey) }).complete(messages)
        }
        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.Configuration)
        assertFalse(error.message.orEmpty().contains(apiKey))
    }

    @Test fun complete_mapsModelNotFoundWithHttpDiagnostic() = assertHttpError(404, """{"error":{"message":"The model was not found"}}""") { error ->
        assertTrue(error is AppError.ModelUnavailable)
        assertEquals(404, (error as AppError.ModelUnavailable).httpCode)
    }

    @Test fun complete_mapsOther4xxWithHttpDiagnosticAndRedactsCredentials() = assertHttpError(400, "invalid request Authorization: Bearer $apiKey") { error ->
        assertTrue(error is AppError.Http)
        assertEquals(400, (error as AppError.Http).httpCode)
        assertFalse(error.message.orEmpty().contains(apiKey))
        assertFalse(error.message.orEmpty().contains("Authorization"))
    }

    @Test fun completeRedactsKeyBeforeDiagnosticTruncation() {
        val body = "x".repeat(195) + apiKey
        assertHttpError(400, body) { error -> assertFalse(error.message.orEmpty().contains(apiKey)) }
    }

    @Test fun cancellingInFlightRequestDisconnectsMockConnection() = runBlocking {
        val accepted = CountDownLatch(1)
        val server = MockServer { socket, _ ->
            accepted.countDown()
            // Wait for client disconnect; cancellation must close the blocking read.
            socket.soTimeout = 2_000
            runCatching { socket.getInputStream().read() }
        }
        try {
            val request = async(Dispatchers.Default) { provider(server.url).complete(messages) }
            assertTrue(accepted.await(2, TimeUnit.SECONDS))
            request.cancelAndJoin()
            assertTrue("server should observe client disconnect", server.finished.await(2, TimeUnit.SECONDS))
        } finally {
            server.close()
        }
    }

    private fun assertHttpError(code: Int, body: String, assertion: (AppError) -> Unit) = mockResponse(code, body) { server ->
        val result = runBlocking { provider(server).complete(messages) }
        assertion((result as AppResult.Failure).error)
    }

    private fun provider(url: String) = OpenAiCompatProvider(
        configProvider = { AiChatConfig(url, "mock-model", apiKey) },
        connectTimeoutMs = 1_000,
        readTimeoutMs = 1_000,
    )

    private fun provider(server: MockServer) = provider(server.url)

    private fun mockResponse(code: Int, body: String, contentType: String = "application/json", block: (MockServer) -> Unit) {
        val server = MockServer { socket, request ->
            request.set(readRequest(socket))
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            val reason = if (code == 200) "OK" else "Mock Error"
            socket.getOutputStream().apply {
                write("HTTP/1.1 $code $reason\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
                write(bytes)
                flush()
            }
        }
        try { block(server) } finally { server.close() }
    }

    private fun readRequest(socket: Socket): String {
        val input = BufferedInputStream(socket.getInputStream())
        val header = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) break
            header.append(c.toChar())
            val tail = header.takeLast(4).toString()
            if (tail == "\r\n\r\n") break
        }
        val headers = header.toString()
        val contentLength = Regex("(?im)^Content-Length:\\s*(\\d+)").find(headers)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val body = ByteArray(contentLength)
        var offset = 0
        while (offset < body.size) {
            val count = input.read(body, offset, body.size - offset)
            if (count < 0) break
            offset += count
        }
        return headers + String(body, 0, offset, StandardCharsets.UTF_8)
    }

    private class MockServer(private val handler: (Socket, AtomicReference<String>) -> Unit) : AutoCloseable {
        private val listener = ServerSocket(0)
        val url = "http://127.0.0.1:${listener.localPort}"
        val request = AtomicReference("")
        val finished = CountDownLatch(1)
        private val worker = Thread {
            try { listener.accept().use { handler(it, request) } } catch (_: Exception) { } finally { finished.countDown() }
        }.apply { isDaemon = true; start() }
        override fun close() {
            runCatching { listener.close() }
            worker.join(2_000)
        }
    }
}
