package com.tim.futureai.data.ai

import com.tim.futureai.core.common.AppError
import com.tim.futureai.core.common.AppResult
import com.tim.futureai.domain.ai.ProviderProtocol
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.MessageRole
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderRegistryTest {
    @Test fun openAiCompatibleProtocolRoutesToItsRegisteredAdapter() {
        val registry = AiProviderRegistry()
        assertTrue(ProviderProtocol.OPENAI_COMPATIBLE in registry.supportedProtocols())
        assertTrue(registry.create(ProviderProtocol.OPENAI_COMPATIBLE) { null } is OpenAiCompatProvider)
    }

    @Test fun unsupportedNativeProtocolFailsClosedWithoutSending() = runBlocking {
        val registry = AiProviderRegistry()
        val result = registry.create(ProviderProtocol.ANTHROPIC_MESSAGES) { error("config must not be read") }
            .complete(listOf(ChatMessage(sessionId = "s", role = MessageRole.USER, content = "hello")))
        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Configuration)
        assertEquals("尚未实现 ANTHROPIC_MESSAGES 协议；请求未发送", result.error.message)
    }

    @Test fun mockHttpRequestsRouteToTheConfiguredProviderAndModel() = runBlocking {
        val first = MockEndpoint()
        val second = MockEndpoint()
        try {
            val registry = AiProviderRegistry()
            val message = listOf(ChatMessage(sessionId = "s", role = MessageRole.USER, content = "hello"))
            val firstResult = registry.create(ProviderProtocol.OPENAI_COMPATIBLE) {
                com.tim.futureai.domain.ai.AiChatConfig(first.url, "first-model", "test-secret")
            }.complete(message)
            val secondResult = registry.create(ProviderProtocol.OPENAI_COMPATIBLE) {
                com.tim.futureai.domain.ai.AiChatConfig(second.url, "second-model", "test-secret")
            }.complete(message)
            assertTrue(firstResult is AppResult.Success)
            assertTrue(secondResult is AppResult.Success)
            assertTrue(first.awaitRequest())
            assertTrue(second.awaitRequest())
            assertTrue(first.requestBody().contains("\"model\":\"first-model\""))
            assertTrue(second.requestBody().contains("\"model\":\"second-model\""))
            assertTrue(!first.requestBody().contains("second-model"))
            assertTrue(!second.requestBody().contains("first-model"))
        } finally {
            first.close()
            second.close()
        }
    }

    private class MockEndpoint : AutoCloseable {
        private val server = ServerSocket(0)
        private val request = AtomicReference("")
        private val received = CountDownLatch(1)
        val url = "http://127.0.0.1:${server.localPort}/v1"

        init {
            Thread {
                runCatching {
                    server.accept().use { socket ->
                        val input = BufferedInputStream(socket.getInputStream())
                        val headerBytes = ByteArrayOutputStream()
                        var ending = ""
                        while (ending != "\r\n\r\n") {
                            val next = input.read()
                            if (next < 0) break
                            headerBytes.write(next)
                            ending = (ending + next.toChar()).takeLast(4)
                        }
                        val headers = headerBytes.toString(StandardCharsets.US_ASCII.name())
                        val length = Regex("(?im)^Content-Length:\\s*(\\d+)").find(headers)?.groupValues?.get(1)?.toInt() ?: 0
                        val body = ByteArray(length)
                        var offset = 0
                        while (offset < length) {
                            val count = input.read(body, offset, length - offset)
                            if (count < 0) break
                            offset += count
                        }
                        request.set(body.toString(StandardCharsets.UTF_8))
                        val response = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"
                        val bytes = response.toByteArray(StandardCharsets.UTF_8)
                        socket.getOutputStream().apply {
                            val responseHeaders = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                            write(responseHeaders.toByteArray(StandardCharsets.US_ASCII))
                            write(bytes)
                            flush()
                        }
                    }
                }
                received.countDown()
            }.apply { isDaemon = true; start() }
        }

        fun awaitRequest() = received.await(3, TimeUnit.SECONDS)
        fun requestBody() = request.get()
        override fun close() { runCatching { server.close() } }
    }
}
