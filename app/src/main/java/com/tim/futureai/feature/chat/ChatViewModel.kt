package com.tim.futureai.feature.chat

import com.tim.futureai.core.common.AppError
import com.tim.futureai.core.common.AppResult
import com.tim.futureai.domain.ai.AiProvider
import com.tim.futureai.domain.chat.ChatHistoryRepository
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.MessageRole
import com.tim.futureai.domain.model.MessageStatus
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
    private var activeUserIndex: Int? = null
    private var activeAssistantIndex: Int? = null

    init {
        if (historyRepository != null) scope.launch {
            val loaded = runCatching { historyRepository.load() }.getOrNull()
            val messages = loaded?.messages.orEmpty()
                .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
                .mapIndexed { index, message ->
                    val status = if (message.status == MessageStatus.GENERATING) MessageStatus.FAILED else message.status
                    ChatBubble(message.role, message.content, status)
                }
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
        val userIndex = current.messages.size
        val userBubble = ChatBubble(role = MessageRole.USER, text = current.inputText.trim(), status = MessageStatus.GENERATING)
        val pending = current.copy(
            messages = current.messages + userBubble,
            inputText = "",
            isLoading = true,
            notice = null,
        )
        setState(pending)
        activeUserIndex = userIndex
        activeAssistantIndex = null
        sendJob = scope.launch { runRequest(history, userIndex, null) }
    }

    /** Regenerates the selected assistant reply using the preceding user message and context. */
    fun regenerate(assistantIndex: Int) {
        val current = _state
        if (current.isLoading || current.isHistoryLoading || assistantIndex !in current.messages.indices) return
        if (current.messages[assistantIndex].role != MessageRole.ASSISTANT) return
        val userIndex = (assistantIndex - 1 downTo 0).firstOrNull { current.messages[it].role == MessageRole.USER } ?: return
        val history = current.messages.take(userIndex + 1).map { ChatMessage(sessionId = "ui", role = it.role, content = it.text) }
        setState(current.copy(isLoading = true, notice = null))
        activeUserIndex = userIndex
        activeAssistantIndex = assistantIndex
        sendJob = scope.launch { runRequest(history, userIndex, assistantIndex) }
    }

    /** Retries a failed or cancelled user message without adding a duplicate. */
    fun retry(userIndex: Int) {
        val current = _state
        if (current.isLoading || current.isHistoryLoading || userIndex !in current.messages.indices) return
        if (userIndex != current.messages.lastIndex) return
        val user = current.messages[userIndex]
        if (user.role != MessageRole.USER || user.status !in setOf(MessageStatus.FAILED, MessageStatus.CANCELLED)) return
        val history = current.messages.take(userIndex + 1).map { ChatMessage(sessionId = "ui", role = it.role, content = it.text) }
        val next = current.messages.toMutableList().also { it[userIndex] = user.copy(status = MessageStatus.GENERATING) }
        setState(current.copy(messages = next, isLoading = true, notice = null))
        activeUserIndex = userIndex
        activeAssistantIndex = null
        sendJob = scope.launch { runRequest(history, userIndex, null) }
    }

    private suspend fun runRequest(history: List<ChatMessage>, userIndex: Int, assistantIndex: Int?) {
        persist(_state.messages)
        val result = try {
            provider.complete(history)
        } catch (e: kotlinx.coroutines.CancellationException) {
            return
        } catch (_: Exception) {
            AppResult.failure(AppError.Unknown("请求失败，请重试"))
        }
        val messages = _state.messages.toMutableList()
        var notice: String? = null
        when (result) {
            is AppResult.Success -> {
                messages[userIndex] = messages[userIndex].copy(status = MessageStatus.SENT)
                if (assistantIndex == null) messages.add(ChatBubble(MessageRole.ASSISTANT, result.data))
                else messages[assistantIndex] = messages[assistantIndex].copy(text = result.data, status = MessageStatus.SENT)
            }
            is AppResult.Failure -> {
                if (assistantIndex == null) messages[userIndex] = messages[userIndex].copy(
                    status = if (result.error is AppError.Cancelled) MessageStatus.CANCELLED else MessageStatus.FAILED,
                )
                notice = result.error.toUserMessage()
            }
        }
        setState(_state.copy(messages = messages, isLoading = false, notice = notice))
        persist(messages, showError = notice == null)
        activeUserIndex = null
        activeAssistantIndex = null
        sendJob = null
    }

    /** 取消进行中的请求。 */
    fun cancel() {
        val userIndex = activeUserIndex
        val assistantIndex = activeAssistantIndex
        sendJob?.cancel()
        sendJob = null
        activeUserIndex = null
        activeAssistantIndex = null
        val messages = _state.messages.toMutableList()
        if (assistantIndex == null && userIndex != null && userIndex in messages.indices) {
            messages[userIndex] = messages[userIndex].copy(status = MessageStatus.CANCELLED)
        }
        setState(_state.copy(messages = messages, isLoading = false, notice = "已取消请求"))
        historyRepository?.let { repo -> scope.launch { persist(messages) } }
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
            repository.save(messages.map { ChatMessage(sessionId = "current", role = it.role, content = it.text, status = it.status) })
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
