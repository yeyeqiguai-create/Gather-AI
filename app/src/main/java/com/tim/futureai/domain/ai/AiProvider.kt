package com.tim.futureai.domain.ai

import com.tim.futureai.core.common.AppError
import com.tim.futureai.core.common.AppResult
import com.tim.futureai.domain.model.ChatMessage

/**
 * AI 服务商统一接口。业务层（feature）只依赖本接口，
 * 具体协议实现位于 data 层，经 DI 注入。
 */
interface AiProvider {

    /**
     * 发送一轮对话（完整历史），返回助手回复文本。
     * 实现不得吞掉异常：所有失败以 [AppResult.Failure] 结构化返回。
     */
    suspend fun complete(messages: List<ChatMessage>): AppResult<String>

    /** Authenticated lightweight connectivity check; implementations must not generate a completion. */
    suspend fun testConnection(): AppResult<Unit> =
        AppResult.failure(AppError.Configuration("当前服务商未实现连接测试"))

    /** Providers may report transport phases; default keeps simple/fake providers source-compatible. */
    suspend fun complete(messages: List<ChatMessage>, onPhase: (AiRequestPhase) -> Unit): AppResult<String> {
        onPhase(AiRequestPhase.REQUESTING)
        return complete(messages)
    }

    /** Streams assistant deltas; simple providers remain compatible by emitting their final reply once. */
    suspend fun stream(
        messages: List<ChatMessage>,
        onPhase: (AiRequestPhase) -> Unit = {},
        onDelta: (String) -> Unit,
    ): AppResult<String> {
        val result = complete(messages, onPhase)
        if (result is AppResult.Success) onDelta(result.data)
        return result
    }

    suspend fun streamWithUsage(
        messages: List<ChatMessage>,
        onPhase: (AiRequestPhase) -> Unit = {},
        onDelta: (String) -> Unit,
        onUsage: (AiTokenUsage) -> Unit,
    ): AppResult<String> = stream(messages, onPhase, onDelta)
}

/** Network protocol is explicit so a profile cannot accidentally be sent through the wrong wire format. */
enum class ProviderProtocol { OPENAI_COMPATIBLE, ANTHROPIC_MESSAGES, GEMINI_GENERATE_CONTENT }

enum class AiRequestPhase { REQUESTING, RECEIVING_REPLY }

data class AiTokenUsage(val promptTokens: Int, val completionTokens: Int)

/**
 * OpenAI Chat Completions 兼容服务商配置。
 *
 * @param baseUrl 例如 https://api.openai.com/v1（以 /v1 结尾，无尾部斜杠）
 * @param model 模型名，如 gpt-4o-mini
 * @param apiKey API Key；运行时从安全存储读取，不得硬编码
 */
data class AiChatConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
) {
    fun isConfigured(): Boolean =
        baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()
}
