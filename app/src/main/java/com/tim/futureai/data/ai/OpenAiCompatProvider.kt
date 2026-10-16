package com.tim.futureai.data.ai

import com.tim.futureai.core.common.AppError
import com.tim.futureai.core.common.AppResult
import com.tim.futureai.core.common.NetworkFailureKind
import com.tim.futureai.core.common.toAppError
import com.tim.futureai.domain.ai.AiChatConfig
import com.tim.futureai.domain.ai.AiRequestPhase
import com.tim.futureai.domain.ai.AiTokenUsage
import com.tim.futureai.domain.ai.AiProvider
import com.tim.futureai.domain.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicReference
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

/**
 * OpenAI Chat Completions 兼容 Provider（data 层唯一 HTTP 出口）。
 *
 * 使用 JDK HttpURLConnection，零新增第三方依赖。
 * UI 不直接使用本类，只依赖 domain 的 [AiProvider] 接口。
 */
class OpenAiCompatProvider(
    private val configProvider: () -> AiChatConfig?,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 60_000,
) : AiProvider {

    override suspend fun complete(messages: List<ChatMessage>): AppResult<String> = complete(messages) {}

    override suspend fun complete(messages: List<ChatMessage>, onPhase: (AiRequestPhase) -> Unit): AppResult<String> =
        perform(messages, streaming = false, onPhase = onPhase, onDelta = {}, onUsage = {})

    override suspend fun stream(
        messages: List<ChatMessage>,
        onPhase: (AiRequestPhase) -> Unit,
        onDelta: (String) -> Unit,
    ): AppResult<String> = perform(messages, streaming = true, onPhase = onPhase, onDelta = onDelta, onUsage = {})

    override suspend fun streamWithUsage(
        messages: List<ChatMessage>,
        onPhase: (AiRequestPhase) -> Unit,
        onDelta: (String) -> Unit,
        onUsage: (AiTokenUsage) -> Unit,
    ): AppResult<String> = perform(messages, streaming = true, onPhase = onPhase, onDelta = onDelta, onUsage = onUsage)

    /** Checks credentials and provider reachability without spending completion tokens. */
    suspend fun testConnection(): AppResult<Unit> {
        val config = configProvider()
            ?: return AppResult.failure(AppError.Configuration("AI 服务未配置：请检查 Base URL、模型名和 API Key"))
        if (!config.isConfigured() || !isValidBaseUrl(config.baseUrl)) {
            return AppResult.failure(AppError.Configuration("AI 配置无效，请检查 Base URL、模型名和 API Key"))
        }
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("${config.baseUrl.trimEnd('/')}/models").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                    setRequestProperty("Accept", "application/json")
                }
                val code = connection.responseCode
                if (code in 200..299) AppResult.Success(Unit)
                else {
                    val body = connection.errorStream?.bufferedReader()?.use { it.readText().take(1000) }.orEmpty()
                    val message = sanitize(OpenAiWire.extractErrorMessage(sanitize(body, config.apiKey)), config.apiKey)
                    AppResult.failure(mapHttpError(code, message))
                }
            } catch (e: SocketTimeoutException) {
                AppResult.failure(AppError.Network("Connection test timed out", e, NetworkFailureKind.TIMEOUT))
            } catch (e: IOException) {
                AppResult.failure(AppError.Network("Could not reach the configured provider", e, NetworkFailureKind.CONNECTION))
            } catch (e: Exception) {
                AppResult.failure(AppError.Unknown("Connection test failed", e))
            } finally {
                connection?.disconnect()
            }
        }
    }

    private suspend fun perform(
        messages: List<ChatMessage>,
        streaming: Boolean,
        onPhase: (AiRequestPhase) -> Unit,
        onDelta: (String) -> Unit,
        onUsage: (AiTokenUsage) -> Unit,
    ): AppResult<String> {
        val config = configProvider()
            ?: return AppResult.failure(AppError.Configuration("AI 服务未配置：请检查 Base URL、模型名和 API Key"))
        if (!config.isConfigured()) {
            return AppResult.failure(AppError.Configuration("AI 服务未配置：请检查 Base URL、模型名和 API Key"))
        }
        if (!isValidBaseUrl(config.baseUrl)) return AppResult.failure(AppError.Configuration("Base URL 格式无效：请输入 http 或 https 接口地址"))
        return suspendCancellableCoroutine { continuation ->
            val connection = AtomicReference<HttpURLConnection?>(null)
            continuation.invokeOnCancellation { connection.get()?.disconnect() }
            Dispatchers.IO.dispatch(continuation.context, Runnable {
                if (!continuation.isActive) return@Runnable
                onPhase(AiRequestPhase.REQUESTING)
                val result = execute(config, messages, connection, { continuation.isActive }, streaming, onDelta, onUsage) {
                    onPhase(AiRequestPhase.RECEIVING_REPLY)
                }
                try {
                    continuation.resume(result)
                } catch (e: IllegalStateException) {
                    if (continuation.isActive) throw e
                }
            })
        }
    }

    private fun execute(
        config: AiChatConfig,
        messages: List<ChatMessage>,
        connection: AtomicReference<HttpURLConnection?>,
        isActive: () -> Boolean,
        streaming: Boolean,
        onDelta: (String) -> Unit,
        onUsage: (AiTokenUsage) -> Unit,
        onResponseReceived: () -> Unit,
    ): AppResult<String> {
        val conn = try {
            val url = URL("${config.baseUrl.trimEnd('/')}/chat/completions")
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (streaming) setRequestProperty("Accept", "text/event-stream")
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return AppResult.failure(sanitizeError(e.toAppError(), config.apiKey))
        }
        connection.set(conn)
        if (!isActive()) {
            conn.disconnect()
            return AppResult.failure(AppError.Cancelled)
        }

        try {
            val body = OpenAiWire.buildRequestBody(messages, config.model, stream = streaming)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            onResponseReceived()
            if (code in 200..299) {
                if (streaming) {
                    val fullReply = StringBuilder()
                    val completed = conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        OpenAiWire.streamReply(reader, onUsage = onUsage) { delta ->
                            fullReply.append(delta)
                            onDelta(delta)
                        }
                    }
                    if (!completed) {
                        return AppResult.failure(AppError.Network(
                            "AI stream ended before completion", null, NetworkFailureKind.CONNECTION,
                        ))
                    }
                    if (fullReply.isBlank()) return AppResult.failure(AppError.Parse("Empty assistant response"))
                    return AppResult.success(fullReply.toString())
                }
                val responseBody = conn.inputStream.bufferedReader().readText()
                return try {
                    OpenAiWire.extractTokenUsage(responseBody)?.let(onUsage)
                    AppResult.success(OpenAiWire.extractReplyContent(responseBody))
                } catch (e: OpenAiWire.ResponseParseException) {
                    AppResult.failure(AppError.Parse(e.message, e))
                }
            }
            // 非 2xx：读错误流并映射
            val errorBody = conn.errorStream?.bufferedReader()?.readText().orEmpty()
            val safeBody = sanitize(errorBody, config.apiKey)
            val message = sanitize(safeBody.let(OpenAiWire::extractErrorMessage), config.apiKey)
            return AppResult.failure(mapHttpError(code, message))
        } catch (e: CancellationException) {
            throw e
        } catch (e: OpenAiWire.ResponseParseException) {
            return AppResult.failure(AppError.Parse(e.message, e))
        } catch (e: SocketTimeoutException) {
            return AppResult.failure(AppError.Network("Request timed out", e, NetworkFailureKind.TIMEOUT))
        } catch (e: IOException) {
            return AppResult.failure(AppError.Network(sanitize(e.message.orEmpty(), config.apiKey), e, NetworkFailureKind.CONNECTION))
        } catch (e: TimeoutException) {
            return AppResult.failure(AppError.Network("Request timed out", e, NetworkFailureKind.TIMEOUT))
        } catch (e: Exception) {
            return AppResult.failure(AppError.Unknown(sanitize(e.message ?: "Unexpected error", config.apiKey), e))
        } finally {
            conn.disconnect()
            connection.compareAndSet(conn, null)
        }
    }

    private fun sanitizeError(error: AppError, apiKey: String): AppError = when (error) {
        is AppError.Network -> error.copy(message = sanitize(error.message.orEmpty(), apiKey))
        is AppError.Configuration -> error.copy(message = sanitize(error.message, apiKey))
        is AppError.ModelUnavailable -> error.copy(message = sanitize(error.message, apiKey))
        is AppError.Http -> error.copy(message = sanitize(error.message, apiKey))
        is AppError.Auth -> error.copy(message = sanitize(error.message.orEmpty(), apiKey))
        is AppError.RateLimited -> error.copy(message = sanitize(error.message.orEmpty(), apiKey))
        is AppError.Server -> error.copy(message = sanitize(error.message.orEmpty(), apiKey))
        is AppError.Parse -> error.copy(message = sanitize(error.message.orEmpty(), apiKey))
        is AppError.Unknown -> error.copy(message = sanitize(error.message.orEmpty(), apiKey))
        AppError.Cancelled -> error
    }

    private fun sanitize(message: String, apiKey: String): String {
        val noAuthorization = message.replace(
            Regex("(?i)authorization\\s*:\\s*bearer\\s+[^\\s,;]+"),
            "[redacted credentials]",
        ).replace(Regex("(?i)bearer\\s+[A-Za-z0-9._~+/-]+=*"), "Bearer [redacted]")
        return if (apiKey.isBlank()) noAuthorization else noAuthorization.replace(apiKey, "[redacted]")
    }

    private fun isValidBaseUrl(baseUrl: String): Boolean = runCatching {
        val uri = URI(baseUrl.trim())
        uri.isAbsolute && uri.scheme.lowercase() in setOf("http", "https") &&
            !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null
    }.getOrDefault(false)

    companion object {
        /** HTTP 状态码 → [AppError] 的统一映射（独立函数，便于测试）。 */
        fun mapHttpError(code: Int, message: String): AppError {
            val modelIssue = message.contains("model", ignoreCase = true) &&
                listOf("not found", "does not exist", "doesn't exist", "unavailable", "unsupported").any { message.contains(it, ignoreCase = true) }
            return when {
                code == 401 || code == 403 -> AppError.Auth(message, httpCode = code)
                code == 429 -> AppError.RateLimited(message, httpCode = code)
                code in 500..599 -> AppError.Server(message, httpCode = code)
                modelIssue -> AppError.ModelUnavailable(message, code)
                else -> AppError.Http("HTTP $code: $message", code)
            }
        }
    }
}
