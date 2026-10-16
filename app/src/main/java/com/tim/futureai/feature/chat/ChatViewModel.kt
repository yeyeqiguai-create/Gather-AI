package com.tim.futureai.feature.chat

import com.tim.futureai.core.common.AppError
import com.tim.futureai.core.common.AppResult
import com.tim.futureai.domain.ai.AiProvider
import com.tim.futureai.domain.chat.ChatSessionData
import com.tim.futureai.domain.chat.ChatSessionsRepository
import com.tim.futureai.domain.chat.ChatSessionsSnapshot
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.ChatSession
import com.tim.futureai.domain.model.MessageRole
import com.tim.futureai.domain.model.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.time.Instant

/** Chat state holder and local-session coordinator. */
class ChatViewModel(
    private val scope: CoroutineScope,
    private val provider: AiProvider,
    initial: ChatUiState = ChatUiState(),
    private val sessionsRepository: ChatSessionsRepository? = null,
) {
    private var sessionData = mutableListOf<ChatSessionData>()
    private var _state = initial.copy(isHistoryLoading = sessionsRepository != null)
    val state: ChatUiState get() = _state
    var onStateChanged: ((ChatUiState) -> Unit)? = null
    private var sendJob: Job? = null
    private var activeUserIndex: Int? = null
    private var activeAssistantIndex: Int? = null

    init {
        if (sessionsRepository != null) scope.launch {
            val loaded = runCatching { sessionsRepository.load() }.getOrNull()
            val snapshot = loaded?.snapshot
            sessionData = (snapshot?.sessions?.takeIf { it.isNotEmpty() } ?: listOf(defaultSession())).toMutableList()
            val active = sessionData.firstOrNull { it.session.id == snapshot?.activeSessionId } ?: sessionData.first()
            val bubbles = active.messages.map { m ->
                ChatBubble(m.role, m.content, if (m.status == MessageStatus.GENERATING) MessageStatus.FAILED else m.status)
            }
            setState(_state.copy(
                activeSessionId = active.session.id,
                sessionTitle = active.session.title,
                sessions = sessionData.map { it.session },
                messages = bubbles,
                isHistoryLoading = false,
                notice = if (loaded?.failed == true || loaded == null) "本地会话读取失败，已创建新会话" else null,
            ))
        } else {
            val first = defaultSession()
            sessionData += first
            _state = initial.copy(activeSessionId = first.session.id, sessionTitle = first.session.title, sessions = listOf(first.session))
        }
    }

    fun updateInput(text: String) = setState(_state.updateInput(text))
    fun dismissNotice() = setState(_state.dismissNotice())

    fun newSession() {
        if (_state.isLoading || _state.isHistoryLoading) return
        syncActiveMessages(_state.messages)
        val data = defaultSession()
        sessionData.add(0, data)
        showSession(data)
        persistAll()
    }

    fun selectSession(id: String) {
        if (_state.isLoading || _state.isHistoryLoading || id == _state.activeSessionId) return
        syncActiveMessages(_state.messages)
        sessionData.firstOrNull { it.session.id == id }?.let { showSession(it); persistAll() }
    }

    fun renameSession(id: String, title: String): Boolean {
        val normalized = title.trim().take(60)
        if (normalized.isEmpty() || _state.isLoading) return false
        val index = sessionData.indexOfFirst { it.session.id == id }
        if (index < 0) return false
        val current = sessionData[index]
        val renamed = current.session.copy(title = normalized, updatedAt = maxOf(Instant.now(), current.session.createdAt))
        sessionData[index] = current.copy(session = renamed)
        setState(_state.copy(sessionTitle = if (id == _state.activeSessionId) normalized else _state.sessionTitle, sessions = sessionData.map { it.session }))
        persistAll()
        return true
    }

    fun toggleSessionPinned(id: String): Boolean {
        if (_state.isLoading || _state.isHistoryLoading) return false
        val index = sessionData.indexOfFirst { it.session.id == id }
        if (index < 0) return false
        val current = sessionData[index]
        sessionData[index] = current.copy(session = current.session.copy(isPinned = !current.session.isPinned))
        setState(_state.copy(sessions = sessionData.map { it.session }))
        persistAll()
        return true
    }

    fun deleteSession(id: String): Boolean {
        if (_state.isLoading || _state.isHistoryLoading || sessionData.none { it.session.id == id }) return false
        sessionData.removeAll { it.session.id == id }
        if (sessionData.isEmpty()) sessionData += defaultSession()
        if (id == _state.activeSessionId) showSession(sessionData.first())
        else setState(_state.copy(sessions = sessionData.map { it.session }))
        persistAll()
        return true
    }

    fun send() {
        val current = _state
        if (current.isLoading || current.isHistoryLoading || !current.canSend) return
        val sessionId = current.activeSessionId
        val history = current.messages.map { ChatMessage(sessionId = sessionId, role = it.role, content = it.text, status = it.status) } +
            ChatMessage(sessionId = sessionId, role = MessageRole.USER, content = current.inputText.trim())
        val userIndex = current.messages.size
        val text = current.inputText.trim()
        val user = ChatBubble(MessageRole.USER, text, MessageStatus.GENERATING)
        val messages = current.messages + user
        val defaultTitle = sessionData.firstOrNull { it.session.id == sessionId }?.session?.title == "新对话"
        val title = if (defaultTitle && userIndex == 0) text.take(48) else current.sessionTitle
        setState(current.copy(messages = messages, inputText = "", isLoading = true, notice = null, sessionTitle = title))
        updateSessionTitle(sessionId, title)
        activeUserIndex = userIndex
        activeAssistantIndex = null
        sendJob = scope.launch { runRequest(history, userIndex, null) }
    }

    fun regenerate(assistantIndex: Int) {
        val current = _state
        if (current.isLoading || current.isHistoryLoading || assistantIndex !in current.messages.indices) return
        if (current.messages[assistantIndex].role != MessageRole.ASSISTANT) return
        val userIndex = (assistantIndex - 1 downTo 0).firstOrNull { current.messages[it].role == MessageRole.USER } ?: return
        val history = current.messages.take(userIndex + 1).map { ChatMessage(sessionId = current.activeSessionId, role = it.role, content = it.text) }
        setState(current.copy(isLoading = true, notice = null))
        activeUserIndex = userIndex
        activeAssistantIndex = assistantIndex
        sendJob = scope.launch { runRequest(history, userIndex, assistantIndex) }
    }

    fun retry(userIndex: Int) {
        val current = _state
        if (current.isLoading || current.isHistoryLoading || userIndex != current.messages.lastIndex) return
        val user = current.messages.getOrNull(userIndex) ?: return
        if (user.role != MessageRole.USER || user.status !in setOf(MessageStatus.FAILED, MessageStatus.CANCELLED)) return
        val history = current.messages.take(userIndex + 1).map { ChatMessage(sessionId = current.activeSessionId, role = it.role, content = it.text) }
        val next = current.messages.toMutableList().also { it[userIndex] = user.copy(status = MessageStatus.GENERATING) }
        setState(current.copy(messages = next, isLoading = true, notice = null))
        activeUserIndex = userIndex
        activeAssistantIndex = null
        sendJob = scope.launch { runRequest(history, userIndex, null) }
    }

    private suspend fun runRequest(history: List<ChatMessage>, userIndex: Int, assistantIndex: Int?) {
        persistAll()
        val result = try { provider.complete(history) }
        catch (_: kotlinx.coroutines.CancellationException) { return }
        catch (_: Exception) { AppResult.failure(AppError.Unknown("请求失败，请重试")) }
        val messages = _state.messages.toMutableList()
        var notice: String? = null
        when (result) {
            is AppResult.Success -> {
                messages[userIndex] = messages[userIndex].copy(status = MessageStatus.SENT)
                if (assistantIndex == null) messages.add(ChatBubble(MessageRole.ASSISTANT, result.data))
                else messages[assistantIndex] = messages[assistantIndex].copy(text = result.data, status = MessageStatus.SENT)
            }
            is AppResult.Failure -> {
                if (assistantIndex == null) messages[userIndex] = messages[userIndex].copy(status = if (result.error is AppError.Cancelled) MessageStatus.CANCELLED else MessageStatus.FAILED)
                notice = result.error.toUserMessage()
            }
        }
        setState(_state.copy(messages = messages, isLoading = false, notice = notice))
        persistCurrent(showError = notice == null)
        activeUserIndex = null; activeAssistantIndex = null; sendJob = null
    }

    fun cancel() {
        val userIndex = activeUserIndex
        val assistant = activeAssistantIndex
        sendJob?.cancel(); sendJob = null
        activeUserIndex = null; activeAssistantIndex = null
        val messages = _state.messages.toMutableList()
        if (assistant == null && userIndex != null && userIndex in messages.indices) messages[userIndex] = messages[userIndex].copy(status = MessageStatus.CANCELLED)
        setState(_state.copy(messages = messages, isLoading = false, notice = "已取消请求"))
        persistAll()
    }

    fun clear() {
        if (_state.isLoading) cancel()
        setState(_state.copy(messages = emptyList(), notice = null))
        persistAll()
    }

    private fun showSession(data: ChatSessionData) = setState(_state.copy(
        activeSessionId = data.session.id, sessionTitle = data.session.title,
        messages = data.messages.map { ChatBubble(it.role, it.content, if (it.status == MessageStatus.GENERATING) MessageStatus.FAILED else it.status) },
        sessions = sessionData.map { it.session }, inputText = "", notice = null,
    ))

    private fun updateSessionTitle(id: String, title: String) {
        val i = sessionData.indexOfFirst { it.session.id == id }
        if (i >= 0) sessionData[i] = sessionData[i].copy(session = sessionData[i].session.copy(title = title, updatedAt = maxOf(Instant.now(), sessionData[i].session.createdAt)))
        setState(_state.copy(sessions = sessionData.map { it.session }))
    }

    private fun syncActiveMessages(messages: List<ChatBubble>) {
        val i = sessionData.indexOfFirst { it.session.id == _state.activeSessionId }
        if (i >= 0) {
            val previous = sessionData[i].messages
            val synchronized = messages.mapIndexed { index, bubble ->
                val old = previous.getOrNull(index)
                if (old != null) old.copy(role = bubble.role, content = bubble.text, status = bubble.status)
                else ChatMessage(sessionId = _state.activeSessionId, role = bubble.role, content = bubble.text, status = bubble.status)
            }
            sessionData[i] = sessionData[i].copy(messages = synchronized)
        }
    }

    private suspend fun persistCurrent(showError: Boolean = true) {
        syncActiveMessages(_state.messages)
        val ok = sessionsRepository?.save(snapshot()) ?: true
        if (!ok && showError) setState(_state.copy(notice = "会话保存失败，请检查设备存储空间"))
    }

    private fun persistAll() { scope.launch { persistCurrent() } }
    private fun snapshot() = ChatSessionsSnapshot(sessionData.toList(), _state.activeSessionId)
    private fun defaultSession() = ChatSessionData(ChatSession(title = "新对话"), emptyList())

    private fun setState(s: ChatUiState) { _state = s; onStateChanged?.invoke(s) }

    companion object {
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

private fun AppError.toUserMessage(): String = ChatViewModel.errorToUserMessage(this)
