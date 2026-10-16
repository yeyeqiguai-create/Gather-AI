package com.tim.futureai.data.ai

import com.tim.futureai.core.common.AppError
import com.tim.futureai.core.common.AppResult
import com.tim.futureai.domain.ai.AiChatConfig
import com.tim.futureai.domain.ai.AiProvider
import com.tim.futureai.domain.ai.ProviderProtocol
import com.tim.futureai.domain.model.ChatMessage

fun interface AiProviderFactory {
    fun create(configProvider: () -> AiChatConfig?): AiProvider
}

/** Explicit protocol routing. An unregistered native protocol fails closed; it is never sent as OpenAI JSON. */
class AiProviderRegistry(
    private val factories: Map<ProviderProtocol, AiProviderFactory> = mapOf(
        ProviderProtocol.OPENAI_COMPATIBLE to AiProviderFactory { config -> OpenAiCompatProvider(config) },
    ),
) {
    fun create(protocol: ProviderProtocol, configProvider: () -> AiChatConfig?): AiProvider =
        factories[protocol]?.create(configProvider) ?: UnsupportedProtocolProvider(protocol)

    fun supportedProtocols(): Set<ProviderProtocol> = factories.keys
}

private class UnsupportedProtocolProvider(private val protocol: ProviderProtocol) : AiProvider {
    override suspend fun complete(messages: List<ChatMessage>): AppResult<String> =
        AppResult.failure(AppError.Configuration("尚未实现 ${protocol.name} 协议；请求未发送"))
}
