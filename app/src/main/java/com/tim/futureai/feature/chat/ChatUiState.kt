package com.tim.futureai.feature.chat

import com.tim.futureai.domain.model.MessageRole
import com.tim.futureai.domain.model.ChatSession
import com.tim.futureai.domain.model.MessageStatus
import com.tim.futureai.domain.model.ChatAttachment

/**
 * 聊天页状态与交互逻辑（纯 Kotlin，可单元测试；渲染交给 ChatScreen）。
 */
data class ChatUiState(
    val sessionTitle: String = "Gather AI",
    val activeSessionId: String = "",
    val sessions: List<ChatSession> = emptyList(),
    val messages: List<ChatBubble> = emptyList(),
    val inputText: String = "",
    /** Whether an AI request currently prevents duplicate sends. */
    val isLoading: Boolean = false,
    val isHistoryLoading: Boolean = false,
    val requestStatus: ChatRequestStatus = ChatRequestStatus.IDLE,
    /** 需要展示给用户的提示（错误/配置缺失）；用户可见后可清除。 */
    val notice: String? = null,
    val modelChoices: List<ChatModelChoice> = emptyList(),
    val selectedModelId: String? = null,
    val pendingAttachments: List<ChatAttachment> = emptyList(),
) {
    /** 当前文本或所有附件均有效且符合模型能力时可发送。 */
    val canSend: Boolean get() {
        val hasImages = pendingAttachments.any { it.mimeType.startsWith("image/") }
        val imageCapable = modelChoices.firstOrNull { it.id == selectedModelId }?.supportsImages == true
        val attachmentsReady = pendingAttachments.isNotEmpty() && pendingAttachments.all { it.error == null } && (!hasImages || imageCapable)
        val attachmentErrors = pendingAttachments.any { it.error != null }
        return (inputText.isNotBlank() || attachmentsReady) && !attachmentErrors && (!hasImages || imageCapable) && !isLoading && !isHistoryLoading
    }

    /**
     * 发送：把当前输入追加为一条 USER 消息并清空输入框。
     * 输入为空白时原样返回（禁止发送）。
     */
    fun send(): ChatUiState {
        val text = inputText.trim()
        if (text.isEmpty()) return this
        return copy(
            messages = messages + ChatBubble(role = MessageRole.USER, text = text),
            inputText = "",
        )
    }

    /** 更新输入框内容。 */
    fun updateInput(text: String): ChatUiState = copy(inputText = text)

    /** 发送开始：清提示、进入加载态。 */
    fun startSending(): ChatUiState = copy(isLoading = true, requestStatus = ChatRequestStatus.PREPARING, notice = null)

    /** 请求完成：清除加载态，可带提示。 */
    fun finishSending(notice: String? = null): ChatUiState = copy(
        isLoading = false,
        requestStatus = if (notice == null) ChatRequestStatus.COMPLETED else ChatRequestStatus.FAILED,
        notice = notice,
    )

    /** 追加一条助手回复气泡。 */
    fun addAssistantReply(text: String): ChatUiState =
        copy(messages = messages + ChatBubble(role = MessageRole.ASSISTANT, text = text))

    /** 清除提示。 */
    fun dismissNotice(): ChatUiState = copy(notice = null)
}

enum class ChatRequestStatus { IDLE, PREPARING, REQUESTING, RECEIVING_REPLY, COMPLETED, FAILED, CANCELLED }

internal fun statusForMessages(messages: List<ChatBubble>): ChatRequestStatus = when (messages.lastOrNull()?.status) {
    MessageStatus.GENERATING -> ChatRequestStatus.FAILED
    MessageStatus.FAILED -> ChatRequestStatus.FAILED
    MessageStatus.CANCELLED -> ChatRequestStatus.CANCELLED
    MessageStatus.SENT -> ChatRequestStatus.COMPLETED
    null -> ChatRequestStatus.IDLE
}
