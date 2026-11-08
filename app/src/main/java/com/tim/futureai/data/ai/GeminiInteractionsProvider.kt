package com.tim.futureai.data.ai

import com.tim.futureai.core.common.AppError
import com.tim.futureai.core.common.AppResult
import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.core.common.NetworkFailureKind
import com.tim.futureai.domain.ai.AiChatConfig
import com.tim.futureai.domain.ai.AiProvider
import com.tim.futureai.domain.ai.AiRequestPhase
import com.tim.futureai.domain.ai.AiTokenUsage
import com.tim.futureai.domain.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/** Google Gemini Interactions REST adapter. Base URL should end at `/v1beta`. */
class GeminiInteractionsProvider(
    private val configProvider: () -> AiChatConfig?,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 90_000,
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
        val result = listModels()
        return when (result) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> AppResult.Failure(result.error)
        }
    }

    override suspend fun listModels(): AppResult<List<String>> {
        val config = usableConfig() ?: return AppResult.failure(AppError.Configuration("Gemini Base URL 与 API Key 必须填写"))
        return withContext(Dispatchers.IO) {
            val ids = linkedSetOf<String>()
            var pageToken: String? = null
            var page = 0
            var failure: AppError? = null
            do {
                var connection: HttpURLConnection? = null
                try {
                    val query = buildList {
                        add("pageSize=100")
                        pageToken?.let { add("pageToken=${URLEncoder.encode(it, "UTF-8")}") }
                    }.joinToString("&")
                    connection = open(config, "models?$query", "GET")
                    val code = connection.responseCode
                    if (code !in 200..299) failure = httpError(code, errorMessage(connection, config.apiKey, config.customHeaders.values))
                    else {
                        val root = JsonMini.parse(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                            .asObject()?.entries ?: error("Expected model catalog object")
                        val models = root["models"]?.asArray()?.items ?: error("Model catalog has no models array")
                        models.forEach { value ->
                            val model = value.asObject()?.entries ?: return@forEach
                            val actions = model["supportedActions"]?.asArray()?.items
                                ?: model["supportedGenerationMethods"]?.asArray()?.items
                            val supportsTextGeneration = actions == null || actions.any { it.asString() in setOf("generateContent", "interactions") }
                            if (supportsTextGeneration) {
                                val name = (model["baseModelId"]?.asString() ?: model["name"]?.asString())
                                    ?.removePrefix("models/")?.trim()?.takeIf(String::isNotBlank)
                                name?.let(ids::add)
                            }
                        }
                        pageToken = root["nextPageToken"]?.asString()?.takeIf(String::isNotBlank)
                    }
                } catch (e: SocketTimeoutException) {
                    failure = AppError.Network("Gemini model list request timed out", e, NetworkFailureKind.TIMEOUT)
                } catch (e: IOException) {
                    failure = AppError.Network(sanitize(e.message.orEmpty(), config.apiKey, config.customHeaders.values), e, NetworkFailureKind.CONNECTION)
                } catch (e: Exception) {
                    failure = AppError.Parse(sanitize(e.message ?: "Could not read Gemini model list", config.apiKey, config.customHeaders.values), e)
                } finally { connection?.disconnect() }
                page++
            } while (pageToken != null && page < 3 && failure == null)
            when {
                failure != null -> AppResult.failure(failure!!)
                ids.isEmpty() -> AppResult.failure(AppError.Parse("Gemini returned no text-generation models"))
                else -> AppResult.Success(ids.toList())
            }
        }
    }

    private suspend fun perform(
        messages: List<ChatMessage>, streaming: Boolean, onPhase: (AiRequestPhase) -> Unit,
        onDelta: (String) -> Unit, onUsage: (AiTokenUsage) -> Unit,
    ): AppResult<String> {
        val config = usableConfig() ?: return AppResult.failure(AppError.Configuration("Gemini Base URL、模型或 API Key 无效"))
        return suspendCancellableCoroutine { continuation ->
            val active = AtomicReference<HttpURLConnection?>(null)
            continuation.invokeOnCancellation { active.get()?.disconnect() }
            Dispatchers.IO.dispatch(continuation.context, Runnable {
                if (!continuation.isActive) return@Runnable
                onPhase(AiRequestPhase.REQUESTING)
                val result = try {
                    val endpoint = if (streaming) "interactions?alt=sse" else "interactions"
                    val connection = open(config, endpoint, "POST").apply {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        if (streaming) setRequestProperty("Accept", "text/event-stream")
                    }
                    active.set(connection)
                    if (!continuation.isActive) { connection.disconnect(); return@Runnable }
                    val body = GeminiInteractionsWire.buildRequestBody(
                        messages, config.model, streaming,
                        temperature = config.temperature, maxOutputTokens = config.maxOutputTokens,
                        systemInstruction = config.systemInstruction,
                    )
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    onPhase(AiRequestPhase.RECEIVING_REPLY)
                    if (code !in 200..299) AppResult.failure(httpError(code, errorMessage(connection, config.apiKey, config.customHeaders.values)))
                    else if (streaming) {
                        val full = StringBuilder()
                        val ended = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                            GeminiInteractionsWire.stream(reader, onUsage) { delta -> full.append(delta); onDelta(delta) }
                        }
                        when {
                            !ended -> AppResult.failure(AppError.Network("Gemini stream ended before completion", null, NetworkFailureKind.CONNECTION))
                            full.isBlank() -> AppResult.failure(AppError.Parse("Empty assistant response"))
                            else -> AppResult.Success(full.toString())
                        }
                    } else {
                        val (reply, usage) = GeminiInteractionsWire.extractReply(
                            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() },
                        )
                        usage?.let(onUsage)
                        AppResult.Success(reply)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SocketTimeoutException) {
                    AppResult.failure(AppError.Network("Gemini request timed out", e, NetworkFailureKind.TIMEOUT))
                } catch (e: IOException) {
                    AppResult.failure(AppError.Network(sanitize(e.message.orEmpty(), config.apiKey, config.customHeaders.values), e, NetworkFailureKind.CONNECTION))
                } catch (e: TimeoutException) {
                    AppResult.failure(AppError.Network("Gemini request timed out", e, NetworkFailureKind.TIMEOUT))
                } catch (e: Exception) {
                    AppResult.failure(AppError.Parse(sanitize(e.message ?: "Invalid Gemini response", config.apiKey, config.customHeaders.values), e))
                } finally { active.getAndSet(null)?.disconnect() }
                if (continuation.isActive) continuation.resume(result)
            })
        }
    }

    private fun usableConfig(): AiChatConfig? = configProvider()?.takeIf {
        it.baseUrl.isNotBlank() && it.model.isNotBlank() && it.apiKey.isNotBlank() && validBaseUrl(it.baseUrl)
    }

    private fun open(config: AiChatConfig, path: String, method: String): HttpURLConnection =
        (URL("${config.baseUrl.trimEnd('/')}/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("x-goog-api-key", config.apiKey)
            setRequestProperty("Accept", "application/json")
            config.customHeaders.forEach { (name, value) -> setRequestProperty(name, value) }
        }

    private fun errorMessage(connection: HttpURLConnection, key: String, customSecrets: Collection<String>): String =
        sanitize(connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(1500) }.orEmpty(), key, customSecrets).let { body ->
            runCatching {
                JsonMini.parse(body).asObject()?.entries?.get("error")?.asObject()?.entries?.get("message")?.asString() ?: body.take(300)
            }.getOrDefault(body.take(300))
        }

    private fun sanitize(message: String, key: String, customSecrets: Collection<String> = emptyList()): String = message
        .replace(Regex("(?i)x-goog-api-key\\s*:\\s*[^\\s,;]+"), "[redacted credentials]")
        .replace(Regex("(?i)([?&]key=)[^&\\s]+"), "$1[redacted]")
        .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._~+/-]+=*"), "Bearer [redacted]")
        .let { text -> customSecrets.filter(String::isNotBlank).fold(if (key.isBlank()) text else text.replace(key, "[redacted]")) { safe, secret -> safe.replace(secret, "[redacted]") } }

    private fun validBaseUrl(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        uri.isAbsolute && uri.scheme.lowercase() in setOf("http", "https") &&
            !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null
    }.getOrDefault(false)

    private fun httpError(code: Int, message: String): AppError = when {
        code == 401 || code == 403 -> AppError.Auth(message, httpCode = code)
        code == 429 -> AppError.RateLimited(message, httpCode = code)
        code in 500..599 -> AppError.Server(message, httpCode = code)
        message.contains("model", ignoreCase = true) -> AppError.ModelUnavailable(message, code)
        else -> AppError.Http("HTTP $code: $message", code)
    }
}
