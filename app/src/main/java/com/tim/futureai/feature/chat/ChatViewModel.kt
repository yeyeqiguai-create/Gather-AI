package com.tim.futureai.feature.chat

import com.tim.futureai.core.common.AppError
import com.tim.futureai.core.common.AppResult
import com.tim.futureai.domain.ai.AiProvider
import com.tim.futureai.domain.ai.AiRequestPhase
import com.tim.futureai.domain.chat.ChatSessionData
import com.tim.futureai.domain.chat.ChatSessionsRepository
import com.tim.futureai.domain.chat.ChatSessionsSnapshot
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.ChatSession
import com.tim.futureai.domain.model.ChatAttachment
import com.tim.futureai.domain.ai.AiTokenUsage
import com.tim.futureai.domain.ai.AutomaticModelSelector
import com.tim.futureai.domain.ai.ModelRoutingRequest
import com.tim.futureai.domain.ai.ModelSelection
import com.tim.futureai.domain.ai.ModelCapabilityRegistry
import com.tim.futureai.domain.ai.ProviderAvailability
import com.tim.futureai.domain.ai.TaskClassifier
import com.tim.futureai.domain.usage.UsageRecord
import com.tim.futureai.domain.usage.UsageRepository
import com.tim.futureai.domain.model.MessageRole
import com.tim.futureai.domain.model.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import com.tim.futureai.domain.ai.AiSettings

/** Chat state holder and local-session coordinator. */
class ChatViewModel(
    private val scope: CoroutineScope,
    private val provider: AiProvider,
    initial: ChatUiState = ChatUiState(),
    private val sessionsRepository: ChatSessionsRepository? = null,
    private val providerForProfileId: ((String?) -> AiProvider)? = null,
    private val defaultModelProfileId: () -> String? = { null },
    private val usageRepository: UsageRepository? = null,
    private val providerForModel: ((profileId: String, modelId: String) -> AiProvider)? = null,
    private val smartLocalRouter: (suspend (String) -> com.tim.futureai.domain.ai.SmartLocalRoute?)? = null,
) {
    private var sessionData = mutableListOf<ChatSessionData>()
    private var _state = initial.copy(isHistoryLoading = sessionsRepository != null)
    val state: ChatUiState get() = _state
    var onStateChanged: ((ChatUiState) -> Unit)? = null
    private var sendJob: Job? = null
    private var activeUserIndex: Int? = null
    private var activeAssistantIndex: Int? = null
    private var activeAssistantHasDelta = false
    private val streamTextLock = Any()
    private var activeStreamText = StringBuilder()
    private var activeRequestStartedAtNanos: Long? = null
    private var activeRequestProfileId: String? = null
    private var activeRequestProviderName: String = "默认配置"
    private var activeRequestModelName: String = "未知"
    private var nextMessageChoiceId: String? = null
    private var historyLimit: Int = 0
    private var requestGeneration = 0L
    private val requestStatusBySession = mutableMapOf<String, ChatRequestStatus>()
    private val providerHealthByChoiceId = mutableMapOf<String, Pair<ProviderAvailability, Long>>()

    init {
        if (sessionsRepository != null) scope.launch {
            val loaded = runCatching { sessionsRepository.load() }.getOrNull()
            val snapshot = loaded?.snapshot
            sessionData = (snapshot?.sessions?.takeIf { it.isNotEmpty() } ?: listOf(defaultSession())).toMutableList()
            val active = sessionData.firstOrNull { it.session.id == snapshot?.activeSessionId } ?: sessionData.first()
            trimHistoryToLimit(active.session.id)
            val retainedActive = sessionData.firstOrNull { it.session.id == active.session.id } ?: sessionData.first()
            val bubbles = retainedActive.messages.map { m ->
                ChatBubble(m.role, m.content, if (m.status == MessageStatus.GENERATING) MessageStatus.FAILED else m.status, m.attachments)
            }
            sessionData.forEach { data ->
                requestStatusBySession[data.session.id] = statusForMessages(data.messages.map { ChatBubble(it.role, it.content, it.status, it.attachments) })
            }
            setState(_state.copy(
                activeSessionId = retainedActive.session.id,
                sessionTitle = retainedActive.session.title,
                sessions = sessionData.map { it.session },
                messages = bubbles,
                selectedModelId = retainedActive.session.modelProfileId ?: defaultModelProfileId(),
                isHistoryLoading = false,
                requestStatus = statusForMessages(bubbles),
                notice = if (loaded?.failed == true || loaded == null) "本地会话读取失败，已创建新会话" else null,
            ))
        } else {
            val first = defaultSession()
            sessionData += first
            _state = initial.copy(activeSessionId = first.session.id, sessionTitle = first.session.title, sessions = listOf(first.session), selectedModelId = defaultModelProfileId())
        }
    }

    fun updateInput(text: String) = setState(_state.updateInput(text))
    fun dismissNotice() = setState(_state.dismissNotice())
    fun showNotice(message: String) = setState(_state.copy(notice = message))

    fun addAttachments(attachments: List<ChatAttachment>) {
        if (_state.isLoading || attachments.isEmpty()) return
        val combined = (_state.pendingAttachments + attachments).take(5)
        val tooMany = _state.pendingAttachments.size + attachments.size > 5
        val limited = if (tooMany && combined.isNotEmpty()) combined.dropLast(1) + combined.last().copy(error = "每次最多添加 5 个附件") else combined
        val error = limited.firstOrNull { it.error != null }?.error
        val unsupportedImage = limited.any { it.mimeType.startsWith("image/") } &&
            _state.modelChoices.firstOrNull { it.id == _state.selectedModelId }?.supportsImages != true
        setState(_state.copy(
            pendingAttachments = limited,
            notice = error ?: if (unsupportedImage) "当前模型未启用图片输入；请确认模型能力或更换模型" else null,
        ))
    }

    fun removeAttachment(id: String) {
        val pending = _state.pendingAttachments.filterNot { it.id == id }
        val unsupportedImage = pending.any { it.mimeType.startsWith("image/") } &&
            _state.modelChoices.firstOrNull { it.id == _state.selectedModelId }?.supportsImages != true
        setState(_state.copy(
            pendingAttachments = pending,
            notice = pending.firstOrNull { it.error != null }?.error
                ?: if (unsupportedImage) "当前模型未启用图片输入；请更换模型" else null,
        ))
    }

    fun editUserMessage(index: Int): Boolean {
        if (_state.isLoading || _state.isHistoryLoading || index !in _state.messages.indices) return false
        val message = _state.messages[index]
        if (message.role != MessageRole.USER) return false
        val remaining = _state.messages.take(index)
        syncActiveMessages(remaining)
        setState(_state.copy(
            messages = remaining,
            inputText = message.text,
            pendingAttachments = message.attachments,
            requestStatus = statusForMessages(remaining),
            notice = null,
        ))
        persistAll()
        return true
    }

    fun setModelChoices(profiles: List<AiSettings>) {
        val choices = ModelCapabilityRegistry.fromProfiles(profiles).map { model ->
            ChatModelChoice(
                id = model.id,
                displayName = "${model.providerName} · ${model.modelId}",
                supportsImages = model.supportsImages,
                profileId = model.profileId,
                providerName = model.providerName,
                modelId = model.modelId,
                contextWindowTokens = model.contextWindowTokens,
                tasks = model.tasks,
                capabilityEvidence = model.capabilityEvidence,
                preference = model.preference,
            )
        }.toMutableList()
        if (choices.isNotEmpty()) choices += ChatModelChoice.automatic(choices.any { it.supportsImages })
        val currentId = sessionData.firstOrNull { it.session.id == _state.activeSessionId }?.session?.modelProfileId
        val selectedId = currentId ?: defaultModelProfileId()
        if (nextMessageChoiceId != null && choices.none { it.id == nextMessageChoiceId }) nextMessageChoiceId = null
        val effectiveChoiceId = nextMessageChoiceId ?: selectedId
        val unsupported = _state.pendingAttachments.any { it.mimeType.startsWith("image/") } &&
            choices.firstOrNull { it.id == effectiveChoiceId }?.supportsImages != true
        setState(_state.copy(
            modelChoices = choices,
            selectedModelId = selectedId,
            nextMessageModelId = nextMessageChoiceId,
            notice = if (unsupported) "当前模型未启用图片输入；请更换模型" else _state.notice,
        ))
    }

    fun selectModel(profileId: String?): Boolean {
        if (_state.isLoading || _state.isHistoryLoading) return false
        if (profileId != null && _state.modelChoices.none { it.id == profileId }) return false
        val index = sessionData.indexOfFirst { it.session.id == _state.activeSessionId }
        if (index < 0) return false
        val current = sessionData[index]
        nextMessageChoiceId = null
        sessionData[index] = current.copy(session = current.session.copy(modelProfileId = profileId))
        val unsupported = _state.pendingAttachments.any { it.mimeType.startsWith("image/") } &&
            _state.modelChoices.firstOrNull { it.id == profileId }?.supportsImages != true
        setState(_state.copy(
            selectedModelId = profileId,
            nextMessageModelId = null,
            activeModelExplanation = null,
            sessions = sessionData.map { it.session },
            notice = if (unsupported) "当前模型未启用图片输入；请更换模型" else null,
        ))
        persistAll()
        return true
    }

    fun selectModelForNextMessage(choiceId: String): Boolean {
        if (_state.isLoading || _state.isHistoryLoading || _state.modelChoices.none { it.id == choiceId }) return false
        nextMessageChoiceId = choiceId
        val choice = _state.modelChoices.first { it.id == choiceId }
        setState(_state.copy(
            nextMessageModelId = choiceId,
            activeModelExplanation = "仅下一条消息使用 ${choice.displayName}；会话默认不变",
        ))
        return true
    }

    fun newSession() {
        if (_state.isHistoryLoading) return
        if (_state.isLoading) stopActiveRequest(showNotice = false)
        syncActiveMessages(_state.messages)
        val data = defaultSession()
        sessionData.add(0, data)
        showSession(data)
        trimHistoryToLimit(data.session.id)
        persistAll()
    }

    fun setHistoryLimit(limit: Int) {
        historyLimit = limit.coerceIn(0, 5000)
        if (!_state.isHistoryLoading && trimHistoryToLimit(_state.activeSessionId)) persistAll()
    }

    /** Clears every conversation and starts a fresh session; provider credentials and usage are untouched. */
    fun clearAllHistory(onSaved: (Boolean) -> Unit = {}): Boolean {
        if (_state.isHistoryLoading) return false
        if (_state.isLoading) stopActiveRequest(showNotice = false)
        nextMessageChoiceId = null
        requestStatusBySession.clear()
        val fresh = defaultSession()
        sessionData = mutableListOf(fresh)
        showSession(fresh)
        scope.launch {
            val saved = runCatching { sessionsRepository?.save(snapshot()) ?: true }.getOrDefault(false)
            if (!saved) setState(_state.copy(notice = "会话保存失败，请检查设备存储空间"))
            onSaved(saved)
        }
        return true
    }

    fun selectSession(id: String) {
        if (_state.isHistoryLoading || id == _state.activeSessionId) return
        if (_state.isLoading) stopActiveRequest(showNotice = false)
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

    fun archiveSession(id: String, archived: Boolean): Boolean {
        if (_state.isLoading || _state.isHistoryLoading) return false
        val index = sessionData.indexOfFirst { it.session.id == id }
        if (index < 0) return false
        if (archived && sessionData[index].session.id == _state.activeSessionId) return false
        val current = sessionData[index]
        sessionData[index] = current.copy(session = current.session.copy(isArchived = archived))
        setState(_state.copy(sessions = sessionData.map { it.session }))
        persistAll()
        return true
    }

    fun deleteSessions(ids: Set<String>): Int {
        if (_state.isLoading || _state.isHistoryLoading) return 0
        val removable = sessionData.filter { it.session.id in ids && it.session.id != _state.activeSessionId }
        if (removable.isEmpty()) return 0
        sessionData.removeAll(removable.toSet())
        setState(_state.copy(sessions = sessionData.map { it.session }))
        persistAll()
        return removable.size
    }

    fun deleteSession(id: String): Boolean {
        if (_state.isLoading || _state.isHistoryLoading || sessionData.none { it.session.id == id }) return false
        sessionData.removeAll { it.session.id == id }
        requestStatusBySession.remove(id)
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
        val choiceOverride = nextMessageChoiceId
        nextMessageChoiceId = null
        val attachments = current.pendingAttachments
        val text = current.inputText.trim().ifBlank { "请分析附件" }
        val history = current.messages.map { ChatMessage(sessionId = sessionId, role = it.role, content = it.text, status = it.status, attachments = it.attachments) } +
            ChatMessage(sessionId = sessionId, role = MessageRole.USER, content = text, attachments = attachments)
        val userIndex = current.messages.size
        val user = ChatBubble(MessageRole.USER, text, MessageStatus.GENERATING, attachments)
        val messages = current.messages + user
        val defaultTitle = sessionData.firstOrNull { it.session.id == sessionId }?.session?.title == "新对话"
        val title = if (defaultTitle && userIndex == 0) text.take(48) else current.sessionTitle
        requestStatusBySession[sessionId] = ChatRequestStatus.PREPARING
        setState(current.copy(messages = messages, inputText = "", pendingAttachments = emptyList(), nextMessageModelId = null, isLoading = true, requestStatus = ChatRequestStatus.PREPARING, notice = null, sessionTitle = title))
        updateSessionTitle(sessionId, title)
        activeUserIndex = userIndex
        activeAssistantIndex = null
        activeAssistantHasDelta = false
        val requestId = ++requestGeneration
        sendJob = scope.launch { runRequest(requestId, sessionId, history, userIndex, null, choiceOverride) }
    }

    fun regenerate(assistantIndex: Int) {
        val current = _state
        if (current.isLoading || current.isHistoryLoading || assistantIndex !in current.messages.indices) return
        if (current.messages[assistantIndex].role != MessageRole.ASSISTANT) return
        val userIndex = (assistantIndex - 1 downTo 0).firstOrNull { current.messages[it].role == MessageRole.USER } ?: return
        val history = current.messages.take(userIndex + 1).map { ChatMessage(sessionId = current.activeSessionId, role = it.role, content = it.text, attachments = it.attachments) }
        requestStatusBySession[current.activeSessionId] = ChatRequestStatus.PREPARING
        setState(current.copy(isLoading = true, requestStatus = ChatRequestStatus.PREPARING, notice = null))
        activeUserIndex = userIndex
        activeAssistantIndex = assistantIndex
        activeAssistantHasDelta = false
        val requestId = ++requestGeneration
        sendJob = scope.launch { runRequest(requestId, current.activeSessionId, history, userIndex, assistantIndex) }
    }

    fun retry(userIndex: Int) {
        val current = _state
        if (current.isLoading || current.isHistoryLoading || userIndex != current.messages.lastIndex) return
        val user = current.messages.getOrNull(userIndex) ?: return
        if (user.role != MessageRole.USER || user.status !in setOf(MessageStatus.FAILED, MessageStatus.CANCELLED)) return
        val history = current.messages.take(userIndex + 1).map { ChatMessage(sessionId = current.activeSessionId, role = it.role, content = it.text, attachments = it.attachments) }
        val next = current.messages.toMutableList().also { it[userIndex] = user.copy(status = MessageStatus.GENERATING) }
        requestStatusBySession[current.activeSessionId] = ChatRequestStatus.PREPARING
        setState(current.copy(messages = next, isLoading = true, requestStatus = ChatRequestStatus.PREPARING, notice = null))
        activeUserIndex = userIndex
        activeAssistantIndex = null
        activeAssistantHasDelta = false
        val requestId = ++requestGeneration
        sendJob = scope.launch { runRequest(requestId, current.activeSessionId, history, userIndex, null) }
    }

    private suspend fun runRequest(
        requestId: Long,
        sessionId: String,
        history: List<ChatMessage>,
        userIndex: Int,
        assistantIndex: Int?,
        choiceOverride: String? = null,
    ) {
        if (!isCurrentRequest(requestId, sessionId)) return
        val streamText = StringBuilder()
        synchronized(streamTextLock) { activeStreamText = streamText }
        val savedChoiceId = sessionData.firstOrNull { it.session.id == sessionId }?.session?.modelProfileId
        val initialChoiceId = choiceOverride ?: savedChoiceId ?: _state.selectedModelId ?: defaultModelProfileId()
        val userPrompt = history.lastOrNull { it.role == MessageRole.USER }?.content.orEmpty()
        val smartRoute = if (initialChoiceId == ChatModelChoice.AUTO_ID && smartLocalRouter != null) {
            runCatching { kotlinx.coroutines.withTimeout(12_000) { smartLocalRouter?.invoke(userPrompt) } }.getOrNull()
        } else null
        val requestedChoiceId = smartRoute?.profileId ?: initialChoiceId
        val automatic = requestedChoiceId == ChatModelChoice.AUTO_ID
        val configuredChoices = _state.modelChoices.filterNot { it.isAutomatic }
        val choices = if (configuredChoices.isEmpty() && requestedChoiceId == null) {
            listOf(ChatModelChoice.legacyDefault())
        } else configuredChoices
        val manualRouteId = when {
            automatic -> null
            smartRoute != null -> smartRoute.profileId
            requestedChoiceId == null && configuredChoices.isEmpty() -> ChatModelChoice.DEFAULT_PROVIDER_ID
            else -> requestedChoiceId
        }
        val contextText = history.joinToString("\n") { message ->
            message.content + message.attachments.joinToString("") { it.textContent }
        }
        val routeResult = AutomaticModelSelector.select(
            ModelRoutingRequest(
                prompt = userPrompt,
                estimatedContextTokens = TaskClassifier.estimateTokens(contextText),
                requiresImages = history.any { message -> message.attachments.any { it.mimeType.startsWith("image/") } },
                manualProfileId = manualRouteId,
                preferredProfileId = defaultModelProfileId(),
            ),
            candidates = choices.map { choice -> choice.candidate(availabilityFor(choice.id)) },
        )
        val selection = routeResult as? ModelSelection.Selected
        if (selection == null) {
            val failure = routeResult as ModelSelection.Unavailable
            val reasons = failure.reasons.values.distinct().joinToString("；")
            val notice = if (reasons.isBlank()) "没有可用模型，请在设置中启用并配置服务商" else "没有可用模型：$reasons"
            val messages = _state.messages.toMutableList()
            if (assistantIndex == null && userIndex in messages.indices) messages[userIndex] = messages[userIndex].copy(status = MessageStatus.FAILED)
            activeAssistantIndex?.takeIf { it in messages.indices }?.let { index ->
                messages[index] = messages[index].copy(status = if (activeAssistantHasDelta) MessageStatus.FAILED else MessageStatus.CANCELLED)
            }
            activeUserIndex = null
            activeAssistantIndex = null
            activeRequestStartedAtNanos = null
            requestStatusBySession[sessionId] = ChatRequestStatus.FAILED
            setState(_state.copy(
                messages = messages, isLoading = false, requestStatus = ChatRequestStatus.FAILED,
                notice = notice, activeModelExplanation = "未发送请求 · ${failure.task}",
            ))
            syncActiveMessages(messages)
            persistAll()
            usageRepository?.let { repository -> scope.launch { runCatching {
                repository.append(UsageRecord(
                    profileId = null,
                    providerName = if (automatic) "自动选模" else "手动选模",
                    model = requestedChoiceId ?: "未选择",
                    durationMs = 0,
                    errorType = "NoModelAvailable",
                    sessionId = sessionId,
                ))
            } } }
            return
        }
        val candidate = selection.candidate
        activeRequestStartedAtNanos = System.nanoTime()
        activeRequestProfileId = candidate.profileId.takeUnless { it == ChatModelChoice.DEFAULT_PROVIDER_ID }
        activeRequestProviderName = candidate.providerName
        activeRequestModelName = candidate.modelId
        val tokenUsage = java.util.concurrent.atomic.AtomicReference<AiTokenUsage?>(null)
        requestStatusBySession[sessionId] = ChatRequestStatus.REQUESTING
        setState(_state.copy(requestStatus = ChatRequestStatus.REQUESTING, activeModelExplanation = smartRoute?.explanation ?: selection.explanation))
        persistAll()
        val result = try {
            (if (candidate.profileId == ChatModelChoice.DEFAULT_PROVIDER_ID) provider
            else providerForModel?.invoke(candidate.profileId, candidate.modelId)
                ?: providerForProfileId?.invoke(candidate.profileId)
                ?: provider)
                .streamWithUsage(history, onPhase = { phase ->
                val status = when (phase) {
                    AiRequestPhase.REQUESTING -> ChatRequestStatus.REQUESTING
                    AiRequestPhase.RECEIVING_REPLY -> ChatRequestStatus.RECEIVING_REPLY
                }
                scope.launch {
                    if (isCurrentRequest(requestId, sessionId)) {
                        requestStatusBySession[sessionId] = status
                        setState(_state.copy(requestStatus = status))
                    }
                }
            }, onDelta = { delta ->
                if (delta.isNotEmpty()) {
                    synchronized(streamTextLock) { streamText.append(delta) }
                    scope.launch {
                        if (isCurrentRequest(requestId, sessionId)) appendAssistantDelta(assistantIndex, delta)
                    }
                }
            }, onUsage = tokenUsage::set)
        }
        catch (_: kotlinx.coroutines.CancellationException) { return }
        catch (_: Exception) { AppResult.failure(AppError.Unknown("请求失败，请检查网络或服务配置后重试")) }
        val completePartial = synchronized(streamTextLock) { streamText.toString() }
        if (completePartial.isNotEmpty() && isCurrentRequest(requestId, sessionId)) {
            setAssistantStreamText(assistantIndex, completePartial)
        }
        if (!isCurrentRequest(requestId, sessionId)) return
        updateProviderAvailability(candidate.id, result)
        val startedAt = activeRequestStartedAtNanos
        activeRequestStartedAtNanos = null
        if (startedAt != null) runCatching {
            usageRepository?.append(UsageRecord(
                profileId = activeRequestProfileId,
                providerName = activeRequestProviderName,
                model = activeRequestModelName,
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0),
                errorType = (result as? AppResult.Failure)?.error?.javaClass?.simpleName,
                promptTokens = tokenUsage.get()?.promptTokens,
                completionTokens = tokenUsage.get()?.completionTokens,
                sessionId = sessionId,
            ))
        }
        val messages = _state.messages.toMutableList()
        var notice: String? = null
        var finalStatus = ChatRequestStatus.COMPLETED
        when (result) {
            is AppResult.Success -> {
                messages[userIndex] = messages[userIndex].copy(status = MessageStatus.SENT)
                val replyIndex = activeAssistantIndex
                if (replyIndex != null && replyIndex in messages.indices) {
                    messages[replyIndex] = messages[replyIndex].copy(text = result.data, status = MessageStatus.SENT)
                } else if (assistantIndex == null) messages.add(ChatBubble(MessageRole.ASSISTANT, result.data))
                else messages[assistantIndex] = messages[assistantIndex].copy(text = result.data, status = MessageStatus.SENT)
            }
            is AppResult.Failure -> {
                if (assistantIndex == null) messages[userIndex] = messages[userIndex].copy(status = if (result.error is AppError.Cancelled) MessageStatus.CANCELLED else MessageStatus.FAILED)
                activeAssistantIndex?.takeIf { it in messages.indices }?.let { index ->
                    if (activeAssistantHasDelta) messages[index] = messages[index].copy(
                        status = if (result.error is AppError.Cancelled) MessageStatus.CANCELLED else MessageStatus.FAILED,
                    )
                }
                notice = result.error.toUserMessage()
                finalStatus = if (result.error is AppError.Cancelled) ChatRequestStatus.CANCELLED else ChatRequestStatus.FAILED
            }
        }
        requestStatusBySession[sessionId] = finalStatus
        setState(_state.copy(messages = messages, isLoading = false, requestStatus = finalStatus, notice = notice))
        persistCurrent(showError = notice == null)
        activeUserIndex = null; activeAssistantIndex = null; activeAssistantHasDelta = false; sendJob = null
    }

    private fun appendAssistantDelta(existingIndex: Int?, delta: String) {
        val messages = _state.messages.toMutableList()
        val index = activeAssistantIndex ?: existingIndex?.takeIf { it in messages.indices } ?: messages.size.also {
            messages.add(ChatBubble(MessageRole.ASSISTANT, delta, MessageStatus.GENERATING))
        }
        if (index < messages.size) {
            val current = messages[index]
            messages[index] = current.copy(
                text = if (activeAssistantHasDelta) current.text + delta else delta,
                status = MessageStatus.GENERATING,
            )
        }
        activeAssistantIndex = index
        activeAssistantHasDelta = true
        setState(_state.copy(messages = messages, requestStatus = ChatRequestStatus.RECEIVING_REPLY))
    }

    private fun setAssistantStreamText(existingIndex: Int?, text: String) {
        val messages = _state.messages.toMutableList()
        val index = activeAssistantIndex ?: existingIndex?.takeIf { it in messages.indices } ?: messages.size
        if (index == messages.size) messages.add(ChatBubble(MessageRole.ASSISTANT, text, MessageStatus.GENERATING))
        else messages[index] = messages[index].copy(text = text, status = MessageStatus.GENERATING)
        activeAssistantIndex = index
        activeAssistantHasDelta = true
        setState(_state.copy(messages = messages, requestStatus = ChatRequestStatus.RECEIVING_REPLY))
    }

    fun cancel() {
        stopActiveRequest(showNotice = true)
        persistAll()
    }

    private fun stopActiveRequest(showNotice: Boolean) {
        if (!_state.isLoading) return
        val userIndex = activeUserIndex
        val streamText = synchronized(streamTextLock) { activeStreamText.toString() }
        if (streamText.isNotEmpty()) setAssistantStreamText(activeAssistantIndex, streamText)
        val assistant = activeAssistantIndex
        activeRequestStartedAtNanos?.let { startedAt ->
            activeRequestStartedAtNanos = null
            val record = UsageRecord(
                profileId = activeRequestProfileId, providerName = activeRequestProviderName,
                model = activeRequestModelName, durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0),
                errorType = "Cancelled",
                sessionId = _state.activeSessionId,
            )
            usageRepository?.let { repository -> scope.launch { runCatching { repository.append(record) } } }
        }
        requestGeneration++
        sendJob?.cancel(); sendJob = null
        activeUserIndex = null; activeAssistantIndex = null
        val messages = _state.messages.toMutableList()
        if (assistant == null && userIndex != null && userIndex in messages.indices) messages[userIndex] = messages[userIndex].copy(status = MessageStatus.CANCELLED)
        if (assistant != null && assistant in messages.indices && activeAssistantHasDelta) {
            messages[assistant] = messages[assistant].copy(status = MessageStatus.CANCELLED)
        }
        requestStatusBySession[_state.activeSessionId] = ChatRequestStatus.CANCELLED
        setState(_state.copy(messages = messages, isLoading = false, requestStatus = ChatRequestStatus.CANCELLED, notice = if (showNotice) "已取消请求" else null))
        activeAssistantHasDelta = false
        syncActiveMessages(messages)
    }

    fun clear() {
        if (_state.isLoading) cancel()
        nextMessageChoiceId = null
        setState(_state.copy(messages = emptyList(), pendingAttachments = emptyList(), nextMessageModelId = null, notice = null))
        persistAll()
    }

    private fun showSession(data: ChatSessionData) {
        nextMessageChoiceId = null
        setState(_state.copy(
            activeSessionId = data.session.id, sessionTitle = data.session.title,
            messages = data.messages.map { ChatBubble(it.role, it.content, if (it.status == MessageStatus.GENERATING) MessageStatus.FAILED else it.status, it.attachments) },
            sessions = sessionData.map { it.session }, inputText = "", pendingAttachments = emptyList(), notice = null, isLoading = false,
            selectedModelId = data.session.modelProfileId ?: defaultModelProfileId(),
            nextMessageModelId = null,
            activeModelExplanation = null,
            requestStatus = requestStatusBySession[data.session.id] ?: statusForMessages(data.messages.map { ChatBubble(it.role, it.content, if (it.status == MessageStatus.GENERATING) MessageStatus.FAILED else it.status, it.attachments) }),
        ))
    }

    private fun isCurrentRequest(requestId: Long, sessionId: String): Boolean =
        requestId == requestGeneration && sessionId == _state.activeSessionId && _state.isLoading

    private fun availabilityFor(choiceId: String): ProviderAvailability {
        val entry = providerHealthByChoiceId[choiceId] ?: return ProviderAvailability.UNKNOWN
        if (entry.second <= System.nanoTime()) {
            providerHealthByChoiceId.remove(choiceId)
            return ProviderAvailability.UNKNOWN
        }
        return entry.first
    }

    private fun updateProviderAvailability(choiceId: String, result: AppResult<String>) {
        val now = System.nanoTime()
        val state = when (result) {
            is AppResult.Success -> ProviderAvailability.READY to 60_000_000_000L
            is AppResult.Failure -> when (val error = result.error) {
                is AppError.RateLimited -> ProviderAvailability.RATE_LIMITED to
                    ((error.retryAfterSeconds ?: 30L).coerceIn(1L, 300L) * 1_000_000_000L)
                is AppError.Auth -> ProviderAvailability.UNAVAILABLE to 300_000_000_000L
                is AppError.Network, is AppError.Server, is AppError.ModelUnavailable -> ProviderAvailability.UNAVAILABLE to 30_000_000_000L
                else -> return
            }
        }
        providerHealthByChoiceId[choiceId] = state.first to (now + state.second)
    }

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
                if (old != null) old.copy(role = bubble.role, content = bubble.text, status = bubble.status, attachments = bubble.attachments)
                else ChatMessage(sessionId = _state.activeSessionId, role = bubble.role, content = bubble.text, status = bubble.status, attachments = bubble.attachments)
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
    /** Null stores inheritance; the effective global model is resolved when showing/sending. */
    private fun defaultSession() = ChatSessionData(ChatSession(title = "新对话", modelProfileId = null), emptyList())

    private fun trimHistoryToLimit(activeId: String): Boolean {
        if (historyLimit <= 0 || sessionData.size <= historyLimit) return false
        val active = sessionData.firstOrNull { it.session.id == activeId } ?: sessionData.firstOrNull() ?: return false
        val kept = sessionData.sortedByDescending { it.session.updatedAt }.take(historyLimit).toMutableList()
        if (kept.none { it.session.id == active.session.id }) kept[kept.lastIndex] = active
        sessionData = kept.sortedByDescending { it.session.updatedAt }.toMutableList()
        val retainedIds = sessionData.mapTo(mutableSetOf()) { it.session.id }
        requestStatusBySession.keys.retainAll(retainedIds)
        if (_state.activeSessionId != active.session.id) showSession(active)
        else setState(_state.copy(sessions = sessionData.map { it.session }))
        return true
    }

    private fun setState(s: ChatUiState) { _state = s; onStateChanged?.invoke(s) }

    companion object {
        fun errorToUserMessage(e: AppError): String = when (e) {
            is AppError.Network -> if (e.kind == com.tim.futureai.core.common.NetworkFailureKind.TIMEOUT) "网络请求超时，请检查网络后重试" else "网络连接失败，请检查网络或 Base URL 后重试"
            is AppError.Configuration -> "AI 配置无效，请检查 Base URL、Model 和 API Key"
            is AppError.Auth -> "鉴权失败（HTTP ${e.httpCode ?: 401}），请检查 API Key 后重试"
            is AppError.RateLimited -> "请求过于频繁（HTTP ${e.httpCode ?: 429}），请稍后重试"
            is AppError.ModelUnavailable -> "模型不可用（HTTP ${e.httpCode}），请检查 Model 名称"
            is AppError.Http -> "API 请求失败（HTTP ${e.httpCode}），请检查配置后重试"
            is AppError.Server -> "AI 服务端错误${e.httpCode?.let { "（HTTP $it）" }.orEmpty()}，请稍后重试"
            is AppError.Parse -> "响应解析失败，请确认服务返回 OpenAI-compatible 格式"
            is AppError.Cancelled -> "请求已取消"
            is AppError.Unknown -> "请求失败，请检查网络或服务配置后重试"
        }
    }
}

private fun AppError.toUserMessage(): String = ChatViewModel.errorToUserMessage(this)
