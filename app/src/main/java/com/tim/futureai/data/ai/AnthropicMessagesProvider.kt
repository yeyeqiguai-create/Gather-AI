package com.tim.futureai.data.ai

import com.tim.futureai.core.common.AppError
import com.tim.futureai.core.common.AppResult
import com.tim.futureai.core.common.NetworkFailureKind
import com.tim.futureai.core.common.RetryAfter
import com.tim.futureai.domain.ai.AiChatConfig
import com.tim.futureai.domain.ai.AiProvider
import com.tim.futureai.domain.ai.AiRequestPhase
import com.tim.futureai.domain.ai.AiTokenUsage
import com.tim.futureai.domain.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume

/** Native Anthropic Messages API adapter. Base URL should end at `/v1`. */
class AnthropicMessagesProvider(
    private val configProvider: () -> AiChatConfig?,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 60_000,
) : AiProvider {
    override suspend fun complete(messages: List<ChatMessage>): AppResult<String> = complete(messages) {}

    override suspend fun complete(messages: List<ChatMessage>, onPhase: (AiRequestPhase) -> Unit): AppResult<String> =
        perform(messages, false, onPhase, {}, {})

    override suspend fun stream(messages: List<ChatMessage>, onPhase: (AiRequestPhase) -> Unit, onDelta: (String) -> Unit): AppResult<String> =
        perform(messages, true, onPhase, onDelta, {})

    override suspend fun streamWithUsage(
        messages: List<ChatMessage>, onPhase: (AiRequestPhase) -> Unit,
        onDelta: (String) -> Unit, onUsage: (AiTokenUsage) -> Unit,
    ): AppResult<String> = perform(messages, true, onPhase, onDelta, onUsage)

    override suspend fun testConnection(): AppResult<Unit> {
        val config = configProvider() ?: return AppResult.failure(AppError.Configuration("AI 服务未配置"))
        if (!config.isConfigured() || !validBaseUrl(config.baseUrl)) return AppResult.failure(AppError.Configuration("Anthropic Base URL、模型或 API Key 无效"))
        return withConnection(config, path = "models", method = "GET") { connection ->
            val code = connection.responseCode
            if (code in 200..299) AppResult.Success(Unit)
            else AppResult.failure(httpError(code, readError(connection, config.apiKey, config.customHeaders.values), connection.getHeaderField("Retry-After")))
        }
    }

    override suspend fun listModels(): AppResult<List<String>> {
        val config = configProvider() ?: return AppResult.failure(AppError.Configuration("AI 服务未配置"))
        if (!config.isConfigured() || !validBaseUrl(config.baseUrl)) return AppResult.failure(AppError.Configuration("Anthropic Base URL、模型或 API Key 无效"))
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val ids = linkedSetOf<String>()
            var afterId: String? = null
            var hasMore = true
            var pageCount = 0
            var failure: AppError? = null
            while (hasMore && pageCount < 3 && failure == null) {
                var connection: HttpURLConnection? = null
                try {
                    val cursor = afterId?.let { "&after_id=${java.net.URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
                    connection = open(config, "models?limit=100$cursor", "GET")
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        failure = httpError(code, readError(connection, config.apiKey, config.customHeaders.values), connection.getHeaderField("Retry-After"))
                    } else {
                        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val root = com.tim.futureai.core.common.JsonMini.parse(body).asObject()?.entries
                            ?: throw IllegalStateException("Expected model list object")
                        val data = root["data"]?.asArray()?.items ?: throw IllegalStateException("Missing model data array")
                        data.forEach { item -> item.asObject()?.entries?.get("id")?.asString()?.trim()?.takeIf(String::isNotBlank)?.let(ids::add) }
                        hasMore = (root["has_more"] as? com.tim.futureai.core.common.JsonValue.JBool)?.value == true
                        afterId = root["last_id"]?.asString()?.takeIf(String::isNotBlank)
                        if (hasMore && afterId == null) failure = AppError.Parse("Model list pagination cursor is missing")
                    }
                } catch (e: SocketTimeoutException) {
                    failure = AppError.Network("Model list request timed out", e, NetworkFailureKind.TIMEOUT)
                } catch (e: IOException) {
                    failure = AppError.Network(sanitize(e.message.orEmpty(), config.apiKey, config.customHeaders.values), e, NetworkFailureKind.CONNECTION)
                } catch (e: Exception) {
                    failure = AppError.Parse(sanitize(e.message ?: "Could not read model list", config.apiKey, config.customHeaders.values), e)
                } finally { connection?.disconnect() }
                pageCount++
            }
            when {
                failure != null -> AppResult.failure(failure!!)
                ids.isEmpty() -> AppResult.failure(AppError.Parse("Anthropic returned no model IDs"))
                else -> AppResult.Success(ids.toList())
            }
        }
    }

    private suspend fun perform(
        messages: List<ChatMessage>, streaming: Boolean, onPhase: (AiRequestPhase) -> Unit,
        onDelta: (String) -> Unit, onUsage: (AiTokenUsage) -> Unit,
    ): AppResult<String> {
        val config = configProvider() ?: return AppResult.failure(AppError.Configuration("AI 服务未配置：请检查 Base URL、模型名和 API Key"))
        if (!config.isConfigured() || !validBaseUrl(config.baseUrl)) return AppResult.failure(AppError.Configuration("Anthropic 配置无效，请检查 Base URL、模型名和 API Key"))
        return suspendCancellableCoroutine { continuation ->
            val activeConnection = AtomicReference<HttpURLConnection?>(null)
            continuation.invokeOnCancellation { activeConnection.get()?.disconnect() }
            Dispatchers.IO.dispatch(continuation.context, Runnable {
                if (!continuation.isActive) return@Runnable
                onPhase(AiRequestPhase.REQUESTING)
                val result = try {
                    val connection = open(config, "messages", "POST").apply {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        if (streaming) setRequestProperty("Accept", "text/event-stream")
                    }
                    activeConnection.set(connection)
                    if (!continuation.isActive) { connection.disconnect(); return@Runnable }
                    connection.outputStream.use {
                        it.write(AnthropicWire.buildRequestBody(
                            messages, config.model, streaming,
                            temperature = config.temperature, maxOutputTokens = config.maxOutputTokens,
                            systemInstruction = config.systemInstruction,
                        ).toByteArray(Charsets.UTF_8))
                    }
                    val code = connection.responseCode
                    onPhase(AiRequestPhase.RECEIVING_REPLY)
                    if (code !in 200..299) {
                        AppResult.failure(httpError(code, readError(connection, config.apiKey, config.customHeaders.values), connection.getHeaderField("Retry-After")))
                    } else if (streaming) {
                        val full = StringBuilder()
                        val ended = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                            AnthropicWire.streamReply(reader, onUsage) { delta -> full.append(delta); onDelta(delta) }
                        }
                        when {
                            !ended -> AppResult.failure(AppError.Network("Anthropic stream ended before completion", null, NetworkFailureKind.CONNECTION))
                            full.isBlank() -> AppResult.failure(AppError.Parse("Empty assistant response"))
                            else -> AppResult.success(full.toString())
                        }
                    } else {
                        val (reply, usage) = AnthropicWire.extractReply(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                        usage?.let(onUsage)
                        AppResult.success(reply)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SocketTimeoutException) {
                    AppResult.failure(AppError.Network("Request timed out", e, NetworkFailureKind.TIMEOUT))
                } catch (e: IOException) {
                    AppResult.failure(AppError.Network(sanitize(e.message.orEmpty(), config.apiKey, config.customHeaders.values), e, NetworkFailureKind.CONNECTION))
                } catch (e: TimeoutException) {
                    AppResult.failure(AppError.Network("Request timed out", e, NetworkFailureKind.TIMEOUT))
                } catch (e: Exception) {
                    AppResult.failure(AppError.Parse(sanitize(e.message ?: "Invalid Anthropic response", config.apiKey, config.customHeaders.values), e))
                } finally {
                    activeConnection.getAndSet(null)?.disconnect()
                }
                if (continuation.isActive) continuation.resume(result)
            })
        }
    }

    private suspend fun withConnection(config: AiChatConfig, path: String, method: String, block: (HttpURLConnection) -> AppResult<Unit>): AppResult<Unit> =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = open(config, path, method)
                block(connection)
            } catch (e: SocketTimeoutException) {
                AppResult.failure(AppError.Network("Connection test timed out", e, NetworkFailureKind.TIMEOUT))
            } catch (e: IOException) {
                AppResult.failure(AppError.Network("Could not reach Anthropic", e, NetworkFailureKind.CONNECTION))
            } catch (e: Exception) {
                AppResult.failure(AppError.Unknown("Connection test failed", e))
            } finally { connection?.disconnect() }
        }

    private fun open(config: AiChatConfig, path: String, method: String): HttpURLConnection =
        (URL("${config.baseUrl.trimEnd('/')}/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("x-api-key", config.apiKey)
            setRequestProperty("anthropic-version", API_VERSION)
            setRequestProperty("Accept", "application/json")
            config.customHeaders.forEach { (name, value) -> setRequestProperty(name, value) }
        }

    private fun readError(connection: HttpURLConnection, apiKey: String, customSecrets: Collection<String>): String =
        sanitize(connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(1500) }.orEmpty(), apiKey, customSecrets)
            .let { body -> runCatching {
                val root = com.tim.futureai.core.common.JsonMini.parse(body).asObject()?.entries
                root?.get("error")?.asObject()?.entries?.get("message")?.asString() ?: body.take(300)
            }.getOrDefault(body.take(300)) }

    private fun sanitize(message: String, key: String, customSecrets: Collection<String> = emptyList()): String = message
        .replace(Regex("(?i)x-api-key\\s*:\\s*[^\\s,;]+"), "[redacted credentials]")
        .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._~+/-]+=*"), "Bearer [redacted]")
        .let { text -> customSecrets.filter(String::isNotBlank).fold(if (key.isBlank()) text else text.replace(key, "[redacted]")) { safe, secret -> safe.replace(secret, "[redacted]") } }

    private fun validBaseUrl(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        uri.isAbsolute && uri.scheme.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null
    }.getOrDefault(false)

    private fun httpError(code: Int, message: String, retryAfter: String?): AppError {
        val delay = RetryAfter.parse(retryAfter)
        return when {
            code == 401 || code == 403 -> AppError.Auth(message, httpCode = code)
            code == 429 -> AppError.RateLimited(message, retryAfterSeconds = delay, httpCode = code)
            code in 500..599 || code == 529 -> AppError.Server(message, httpCode = code)
            message.contains("model", ignoreCase = true) -> AppError.ModelUnavailable(message, code)
            else -> AppError.Http("HTTP $code: $message", code)
        }
    }

    companion object { private const val API_VERSION = "2023-06-01" }
}
