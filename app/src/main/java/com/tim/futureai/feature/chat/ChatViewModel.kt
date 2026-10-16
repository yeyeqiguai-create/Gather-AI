package com.tim.futureai.feature.chat

import com.tim.futureai.core.common.AppError
import com.tim.futureai.core.common.AppResult
import com.tim.futureai.domain.ai.AiProvider
import com.tim.futureai.domain.chat.ChatHistoryRepository
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.MessageRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * 聊天页状态持有者：连接 UI 状态与 [AiProvider]。
 * 不持有 Android UI 引用，便于单元测试。
 */
class ChatViewModel(
    private val scope: CoroutineScope,
    private val provider: AiProvider,
    initial: ChatUiState = ChatUiState(),
    private val historyRepository: ChatHistoryRepository? = null,
) {
    private var _state: ChatUiState = initial.copy(isHistoryLoading = historyRepository != null)

    /** 当前状态。 */
    val state: ChatUiState get() = _state

    /** 状态变化回调（由 UI 设置以触发重组）。 */
    var onStateChanged: ((ChatUiState) -> Unit)? = null

    private var sendJob: Job? = null

    init {
        if (historyRepository != null) scope.launch {
            val loaded = runCatching { historyRepository.load() }.getOrNull()
            val messages = loaded?.messages.orEmpty()
                .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
                .map { ChatBubble(it.role, it.content) }
            setState(_state.copy(
                messages = messages,
                isHistoryLoading = false,
                notice = if (loaded?.failed == true || loaded == null) "本地聊天记录读取失败，已开始空白会话" else _state.notice,
            ))
        }
    }

    fun updateInput(text: String) = setState(_state.updateInput(text))

    fun dismissNotice() = setState(_state.dismissNotice())

    /** 发送当前输入；已在加载中则忽略。 */
    fun send() {
        val current = _state
        if (current.isLoading || current.isHistoryLoading || !current.canSend) return
        // 历史包含已上屏消息 + 本次发送的用户消息。
        val history = current.messages.map {
            ChatMessage(sessionId = "ui", role = it.role, content = it.text)
        } + ChatMessage(sessionId = "ui", role = MessageRole.USER, content = current.inputText.trim())
        // 先把用户消息上屏（trim 后），再发起请求。
        val userBubble = ChatBubble(role = MessageRole.USER, text = current.inputText.trim())
        val pending = current.copy(
            messages = current.messages + userBubble,
            inputText = "",
            isLoading = true,
            notice = null,
        )
        setState(pending)
        sendJob = scope.launch {
            persist(pending.messages)
            var s = pending
            when (val result = provider.complete(history)) {
                is AppResult.Success -> s = s.addAssistantReply(result.data).finishSending()
                is AppResult.Failure -> s = s.finishSending(notice = result.error.toUserMessage())
            }
            setState(s)
            persist(s.messages, showError = s.notice == null)
        }
    }

    /** 取消进行中的请求。 */
    fun cancel() {
        sendJob?.cancel()
        sendJob = null
        setState(_state.finishSending(notice = "已取消请求"))
    }

    /** 清空会话（取消进行中的请求）。 */
    fun clear() {
        scope.coroutineContext.cancelChildren()
        setState(ChatUiState(sessionTitle = _state.sessionTitle))
        historyRepository?.let { repository ->
            scope.launch { runCatching { repository.save(emptyList()) } }
        }
    }

    private fun setState(s: ChatUiState) {
        _state = s
        onStateChanged?.invoke(s)
    }

    private suspend fun persist(messages: List<ChatBubble>, showError: Boolean = true) {
        val repository = historyRepository ?: return
        val saved = runCatching {
            repository.save(messages.map { ChatMessage(sessionId = "current", role = it.role, content = it.text) })
        }.getOrDefault(false)
        if (!saved && showError) setState(_state.copy(notice = "聊天记录保存失败，请检查设备存储空间"))
    }

    companion object {
        /** 错误 → 用户可读提示（不泄露 API Key，不吞取消）。 */
        fun errorToUserMessage(e: AppError): String = when (e) {
            is AppError.Network -> "网络错误：${e.message ?: "连接失败或超时"}"
            is AppError.Auth -> "鉴权失败：请检查 API Key 是否有效"
            is AppError.RateLimited -> "请求过于频繁，请稍后重试"
            is AppError.Server -> "服务端错误：${e.message ?: "未知"}"
            is AppError.Parse -> "响应解析失败：${e.message ?: "格式异常"}"
            is AppError.Cancelled -> "请求已取消"
            is AppError.Unknown -> e.message ?: "未知错误"
        }
    }
}

/** 内部便捷扩展。 */
private fun AppError.toUserMessage(): String = ChatViewModel.errorToUserMessage(this)
