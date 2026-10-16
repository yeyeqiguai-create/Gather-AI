package com.tim.futureai.feature.chat

import com.tim.futureai.domain.model.MessageRole
import com.tim.futureai.domain.model.ChatSession

/**
 * 聊天页状态与交互逻辑（纯 Kotlin，可单元测试；渲染交给 ChatScreen）。
 */
data class ChatUiState(
    val sessionTitle: String = "Gather AI",
    val activeSessionId: String = "",
    val sessions: List<ChatSession> = emptyList(),
    val messages: List<ChatBubble> = emptyList(),
    val inputText: String = "",
    /** 请求进行中（等待 AI 回复）。 */
    val isLoading: Boolean = false,
    val isHistoryLoading: Boolean = false,
    /** 需要展示给用户的提示（错误/配置缺失）；用户可见后可清除。 */
    val notice: String? = null,
) {
    /** 当前输入是否可发送（非空白且不在请求中）。 */
    val canSend: Boolean get() = inputText.isNotBlank() && !isLoading && !isHistoryLoading

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
    fun startSending(): ChatUiState = copy(isLoading = true, notice = null)

    /** 请求完成：清除加载态，可带提示。 */
    fun finishSending(notice: String? = null): ChatUiState = copy(isLoading = false, notice = notice)

    /** 追加一条助手回复气泡。 */
    fun addAssistantReply(text: String): ChatUiState =
        copy(messages = messages + ChatBubble(role = MessageRole.ASSISTANT, text = text))

    /** 清除提示。 */
    fun dismissNotice(): ChatUiState = copy(notice = null)
}
