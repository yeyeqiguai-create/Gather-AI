package com.tim.futureai.domain.model

import java.time.Instant
import java.util.UUID

/** 消息角色。 */
enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
}

/**
 * 聊天消息（纯 Kotlin，不依赖 Android / 数据库 / 具体 AI 服务商）。
 *
 * @param id 消息 ID，UUID，稳定且全局唯一。
 * @param sessionId 所属会话 ID，UUID。
 * @param role 消息角色。
 * @param content 文本内容；不允许空白（由 [ChatMessage] init 校验）。
 * @param createdAt 创建时间（UTC Instant）。
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val createdAt: Instant = Instant.now(),
) {
    init {
        require(id.isNotBlank()) { "message id must not be blank" }
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(content.isNotBlank()) { "message content must not be blank" }
    }
}

/**
 * 聊天会话（纯 Kotlin）。
 *
 * @param id 会话 ID，UUID，稳定且全局唯一。
 * @param title 会话标题；不允许空白。
 * @param createdAt 创建时间。
 * @param updatedAt 最后更新时间；不得早于创建时间。
 */
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
) {
    init {
        require(id.isNotBlank()) { "session id must not be blank" }
        require(title.isNotBlank()) { "session title must not be blank" }
        require(!updatedAt.isBefore(createdAt)) { "updatedAt must not be before createdAt" }
    }
}
