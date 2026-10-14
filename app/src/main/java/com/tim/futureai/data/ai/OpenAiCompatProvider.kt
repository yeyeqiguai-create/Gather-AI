package com.tim.futureai.data.ai

import com.tim.futureai.core.common.AppError
import com.tim.futureai.core.common.AppResult
import com.tim.futureai.core.common.toAppError
import com.tim.futureai.domain.ai.AiChatConfig
import com.tim.futureai.domain.ai.AiProvider
import com.tim.futureai.domain.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
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

    override suspend fun complete(messages: List<ChatMessage>): AppResult<String> {
        val config = configProvider()
            ?: return AppResult.failure(AppError.Unknown("AI 服务未配置：缺少 Base URL、模型名或 API Key"))
        if (!config.isConfigured()) {
            return AppResult.failure(AppError.Unknown("AI 服务未配置：缺少 Base URL、模型名或 API Key"))
        }
        return withContext(Dispatchers.IO) {
            execute(config, messages)
        }
    }

    private fun execute(config: AiChatConfig, messages: List<ChatMessage>): AppResult<String> {
        val conn = try {
            val url = URL("${config.baseUrl.trimEnd('/')}/chat/completions")
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return AppResult.failure(e.toAppError())
        }

        try {
            val body = OpenAiWire.buildRequestBody(messages, config.model)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code in 200..299) {
                val responseBody = conn.inputStream.bufferedReader().readText()
                return try {
                    AppResult.success(OpenAiWire.extractReplyContent(responseBody))
                } catch (e: OpenAiWire.ResponseParseException) {
                    AppResult.failure(AppError.Parse(e.message, e))
                }
            }
            // 非 2xx：读错误流并映射
            val errorBody = conn.errorStream?.bufferedReader()?.readText().orEmpty()
            val message = OpenAiWire.extractErrorMessage(errorBody)
            return AppResult.failure(mapHttpError(code, message))
        } catch (e: CancellationException) {
            throw e
        } catch (e: OpenAiWire.ResponseParseException) {
            return AppResult.failure(AppError.Parse(e.message, e))
        } catch (e: IOException) {
            return AppResult.failure(AppError.Network(e.message, e))
        } catch (e: TimeoutException) {
            return AppResult.failure(AppError.Network("Request timed out", e))
        } catch (e: Exception) {
            return AppResult.failure(AppError.Unknown(e.message ?: "Unexpected error", e))
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** HTTP 状态码 → [AppError] 的统一映射（独立函数，便于测试）。 */
        fun mapHttpError(code: Int, message: String): AppError = when (code) {
            401, 403 -> AppError.Auth(message)
            429 -> AppError.RateLimited(message)
            in 500..599 -> AppError.Server(message, httpCode = code)
            else -> AppError.Server("HTTP $code: $message", httpCode = code)
        }
    }
}
