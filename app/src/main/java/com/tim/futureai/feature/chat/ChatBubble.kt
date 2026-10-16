package com.tim.futureai.feature.chat

import com.tim.futureai.domain.model.MessageRole
import com.tim.futureai.domain.model.MessageStatus
import com.tim.futureai.domain.model.ChatAttachment

/** 聊天页气泡：渲染层的最小消息视图模型。 */
data class ChatBubble(
    val role: MessageRole,
    val text: String,
    val status: MessageStatus = MessageStatus.SENT,
    val attachments: List<ChatAttachment> = emptyList(),
)

internal fun copyMessageText(text: String, writeClipboard: (String) -> Unit, onCopied: () -> Unit) {
    writeClipboard(text)
    onCopied()
}
