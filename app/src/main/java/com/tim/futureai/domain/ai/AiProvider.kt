package com.tim.futureai.domain.ai

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
}

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
