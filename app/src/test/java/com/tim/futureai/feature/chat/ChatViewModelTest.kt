package com.tim.futureai.feature.chat

import com.tim.futureai.core.common.AppError
import com.tim.futureai.core.common.AppResult
import com.tim.futureai.domain.ai.AiProvider
import com.tim.futureai.domain.ai.AiRequestPhase
import com.tim.futureai.domain.ai.AiTokenUsage
import com.tim.futureai.domain.ai.ModelTask
import com.tim.futureai.domain.chat.ChatSessionData
import com.tim.futureai.domain.chat.ChatSessionsLoad
import com.tim.futureai.domain.chat.ChatSessionsRepository
import com.tim.futureai.domain.chat.ChatSessionsSnapshot
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.ChatAttachment
import com.tim.futureai.domain.model.ChatSession
import com.tim.futureai.domain.model.MessageRole
import com.tim.futureai.domain.model.MessageStatus
import com.tim.futureai.domain.ai.AiSettings
import com.tim.futureai.domain.usage.UsageRecord
import com.tim.futureai.domain.usage.UsageRepository
import com.tim.futureai.domain.usage.UsageSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 使用 CoroutineScope(Dispatchers.Unconfined)：
 * 测试线程无外层事件循环时 launch 同步执行，
 * FakeProvider 无挂起点，send() 返回后状态即最终，可直接断言。
 */
class ChatViewModelTest {

    private class MemoryHistory(messages: List<ChatMessage> = emptyList()) : ChatSessionsRepository {
        var snapshot = ChatSessionsSnapshot(
            if (messages.isEmpty()) emptyList() else listOf(ChatSessionData(ChatSession(id = "current", title = "会话"), messages)),
            if (messages.isEmpty()) null else "current",
        )
        var failed = false
        val messages: List<ChatMessage> get() = snapshot.sessions.firstOrNull { it.session.id == snapshot.activeSessionId }?.messages.orEmpty()
        override suspend fun load() = ChatSessionsLoad(snapshot, failed)
        override suspend fun save(snapshot: ChatSessionsSnapshot): Boolean {
            this.snapshot = snapshot
            return !failed
        }
    }

    private class MemoryUsage : UsageRepository {
        val records = mutableListOf<UsageRecord>()
        override suspend fun load() = UsageSnapshot(records.toList())
        override suspend fun append(record: UsageRecord) { records += record }
    }

    private class FakeProvider(
        var result: AppResult<String>,
        val received: MutableList<List<ChatMessage>> = mutableListOf(),
    ) : AiProvider {
        override suspend fun complete(messages: List<ChatMessage>): AppResult<String> {
            received.add(messages)
            return result
        }
    }

    private class RecordingScope : CoroutineScope {
        val caught = mutableListOf<Throwable>()
        override val coroutineContext = Dispatchers.Unconfined +
            kotlinx.coroutines.CoroutineExceptionHandler { _, e -> caught.add(e) }
    }

    private var scope: RecordingScope = RecordingScope()

    private fun newVm(provider: AiProvider): ChatViewModel {
        scope = RecordingScope()
        return ChatViewModel(scope, provider)
    }

    /** 等待加载态结束（协程调度可能跨线程，不假设同步完成）。 */
    private fun awaitSettled(vm: ChatViewModel, timeoutMs: Long = 3000) {
        val end = System.currentTimeMillis() + timeoutMs
        while (vm.state.isLoading && System.currentTimeMillis() < end) Thread.sleep(5)
        if (scope.caught.isNotEmpty()) {
            fail("coroutine crashed: " + scope.caught.joinToString("\n") { it.toString() + "\n" + it.stackTraceToString().take(800) })
        }
        assertFalse("request did not settle in time", vm.state.isLoading)
    }

    @Test
    fun send_success_appendsReplyAndClearsLoading() {
        val p = FakeProvider(AppResult.success("AI says hi"))
        val vm = newVm(p)
        vm.updateInput("hello")
        vm.send()
        awaitSettled(vm)

        assertFalse(vm.state.isLoading)
        assertEquals(2, vm.state.messages.size)
        assertEquals("hello", vm.state.messages[0].text)
        assertEquals("AI says hi", vm.state.messages[1].text)
        assertNull(vm.state.notice)
        assertEquals(ChatRequestStatus.COMPLETED, vm.state.requestStatus)
        assertEquals(1, p.received[0].size)
        assertEquals("hello", p.received[0][0].content)
    }

    @Test
    fun send_failure_showsNoticeAndClearsLoading() {
        val p = FakeProvider(AppResult.failure(AppError.Auth("401")))
        val vm = newVm(p)
        vm.updateInput("q")
        vm.send()
        awaitSettled(vm)

        assertFalse(vm.state.isLoading)
        assertEquals(1, vm.state.messages.size) // 只有用户消息，无伪造回复
        assertEquals("鉴权失败（HTTP 401），请检查 API Key 后重试", vm.state.notice)
    }

    @Test
    fun send_blankInput_isIgnored() {
        val p = FakeProvider(AppResult.success("ok"))
        val vm = newVm(p)
        vm.updateInput("  ")
        vm.send()
        assertEquals(0, p.received.size)
    }

    @Test
    fun send_whileLoading_isIgnored() {
        val p = FakeProvider(AppResult.success("ok"))
        val vm = newVm(p)
        vm.updateInput("first")
        vm.send()
        awaitSettled(vm)
        vm.updateInput("second")
        vm.send()
        awaitSettled(vm)
        assertEquals(2, p.received.size)
        assertEquals("", vm.state.inputText) // 发送后输入清空
    }

    @Test
    fun unconfiguredProvider_showsConfigNotice() {
        val p = FakeProvider(AppResult.failure(AppError.Configuration("请检查配置")))
        val vm = newVm(p)
        vm.updateInput("hi")
        vm.send()
        awaitSettled(vm)
        assertTrue(vm.state.notice!!.contains("配置"))
        assertEquals(ChatRequestStatus.FAILED, vm.state.requestStatus)
        assertEquals(1, vm.state.messages.size)
    }

    @Test
    fun updateInput_and_dismissNotice_work() {
        val p = FakeProvider(AppResult.failure(AppError.Parse("bad")))
        val vm = newVm(p)
        vm.updateInput("x")
        assertEquals("x", vm.state.inputText)
        vm.send()
        awaitSettled(vm)
        vm.dismissNotice()
        assertNull(vm.state.notice)
    }

    @Test
    fun historyRestoresAndPersistsUserAndAssistantMessages() {
        val history = MemoryHistory()
        val first = ChatMessage(sessionId = "current", role = MessageRole.USER, content = "先前的问题")
        history.snapshot = ChatSessionsSnapshot(
            listOf(ChatSessionData(ChatSession(id = "current", title = "会话"), listOf(first, ChatMessage(sessionId = "current", role = MessageRole.ASSISTANT, content = "先前的回答")))),
            "current",
        )
        val scope = RecordingScope()
        val vm = ChatViewModel(scope, FakeProvider(AppResult.success("新回答")), sessionsRepository = history)
        assertEquals(listOf("先前的问题", "先前的回答"), vm.state.messages.map { it.text })
        vm.updateInput("新问题")
        vm.send()
        awaitSettled(vm)
        assertEquals(listOf("先前的问题", "先前的回答", "新问题", "新回答"), history.messages.map { it.content })
        assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT, MessageRole.USER, MessageRole.ASSISTANT), history.messages.map { it.role })
        vm.clear()
        assertTrue(history.messages.isEmpty())
    }

    @Test
    fun historyReadFailureDoesNotPreventChatStartup() {
        val history = MemoryHistory().apply { failed = true }
        val vm = ChatViewModel(RecordingScope(), FakeProvider(AppResult.success("ok")), sessionsRepository = history)
        assertFalse(vm.state.isHistoryLoading)
        assertTrue(vm.state.notice!!.contains("本地会话读取失败"))
    }

    @Test
    fun sessionsCreateSwitchRenameDeleteAndKeepRequestContextIsolated() {
        val repository = MemoryHistory()
        val provider = FakeProvider(AppResult.success("答复"))
        val vm = ChatViewModel(RecordingScope(), provider, sessionsRepository = repository)
        val firstId = vm.state.activeSessionId
        vm.updateInput("只属于会话一")
        vm.send()
        vm.newSession()
        val secondId = vm.state.activeSessionId
        assertTrue(firstId.isNotBlank() && secondId.isNotBlank() && firstId != secondId)
        assertTrue(vm.state.messages.isEmpty())
        vm.updateInput("只属于会话二")
        vm.send()
        assertEquals(listOf("只属于会话二"), provider.received.last().map { it.content })
        assertTrue(vm.renameSession(firstId, "自定义标题"))
        vm.selectSession(firstId)
        assertEquals("自定义标题", vm.state.sessionTitle)
        assertEquals(listOf("只属于会话一", "答复"), vm.state.messages.map { it.text })
        assertTrue(vm.deleteSession(firstId))
        assertEquals(secondId, vm.state.activeSessionId)
        assertEquals(listOf("只属于会话二", "答复"), vm.state.messages.map { it.text })
        assertTrue(vm.deleteSession(secondId))
        assertTrue(vm.state.activeSessionId.isNotBlank())
        assertTrue(vm.state.messages.isEmpty())
        assertEquals(1, vm.state.sessions.size)
    }

    @Test
    fun modelChoiceIsSavedIndependentlyPerSession() {
        val repository = MemoryHistory().apply {
            snapshot = ChatSessionsSnapshot(
                listOf(
                    ChatSessionData(ChatSession(id = "one", title = "一"), emptyList()),
                    ChatSessionData(ChatSession(id = "two", title = "二"), emptyList()),
                ),
                "one",
            )
        }
        val vm = ChatViewModel(RecordingScope(), FakeProvider(AppResult.success("ok")), sessionsRepository = repository)
        vm.setModelChoices(listOf(
            AiSettings(id = "model-a", name = "A", baseUrl = "https://a.example/v1", model = "a", hasSavedApiKey = true),
            AiSettings(id = "model-b", name = "B", baseUrl = "https://b.example/v1", model = "b", hasSavedApiKey = true),
        ))
        assertTrue(vm.selectModel("model-b"))
        vm.selectSession("two")
        assertNull(vm.state.selectedModelId)
        assertTrue(vm.selectModel("model-a"))

        assertEquals("model-b", repository.snapshot.sessions.first { it.session.id == "one" }.session.modelProfileId)
        assertEquals("model-a", repository.snapshot.sessions.first { it.session.id == "two" }.session.modelProfileId)
    }

    @Test
    fun automaticRoutingChoosesTaskFitAndRecordsActualProviderModel() {
        val provider = FakeProvider(AppResult.success("implemented"))
        val usage = MemoryUsage()
        val routed = mutableListOf<Pair<String, String>>()
        val vm = ChatViewModel(
            RecordingScope(), provider,
            providerForModel = { profileId, modelId -> routed += profileId to modelId; provider },
            defaultModelProfileId = { "general" },
            usageRepository = usage,
        )
        vm.setModelChoices(listOf(
            AiSettings(id = "general", name = "General", baseUrl = "https://g.example/v1", model = "general-model", hasSavedApiKey = true),
            AiSettings(
                id = "coding", name = "Coding", baseUrl = "https://c.example/v1", model = "coder-model",
                hasSavedApiKey = true, supportedTasks = setOf(ModelTask.CODING),
                capabilityEvidence = com.tim.futureai.domain.ai.CapabilityEvidence.PROVIDER_DECLARED,
            ),
        ))
        assertTrue(vm.selectModel(ChatModelChoice.AUTO_ID))
        vm.updateInput("Please implement a parser")
        vm.send()
        awaitSettled(vm)

        assertEquals(listOf("coding" to "coder-model"), routed)
        assertTrue(vm.state.activeModelExplanation!!.contains("编程"))
        assertEquals("coding", usage.records.single().profileId)
        assertEquals("Coding", usage.records.single().providerName)
        assertEquals("coder-model", usage.records.single().model)
    }

    @Test
    fun manualSessionChoiceIsNotReplacedByAutomaticTaskMatching() {
        val provider = FakeProvider(AppResult.success("ok"))
        val routed = mutableListOf<Pair<String, String>>()
        val vm = ChatViewModel(
            RecordingScope(), provider,
            providerForModel = { profileId, modelId -> routed += profileId to modelId; provider },
        )
        vm.setModelChoices(listOf(
            AiSettings(id = "manual", name = "Manual", baseUrl = "https://m.example/v1", model = "manual-model", hasSavedApiKey = true),
            AiSettings(id = "coder", name = "Coder", baseUrl = "https://c.example/v1", model = "coder-model", hasSavedApiKey = true, supportedTasks = setOf(ModelTask.CODING)),
        ))
        assertTrue(vm.selectModel("manual"))
        vm.updateInput("implement a parser")
        vm.send()
        awaitSettled(vm)
        assertEquals(listOf("manual" to "manual-model"), routed)
        assertTrue(vm.state.activeModelExplanation!!.contains("手动指定"))
    }

    @Test
    fun failedAutomaticAttemptDoesNotSilentlyFallbackAndExplicitRetryReRoutes() {
        val attempts = mutableListOf<String>()
        val limitedProvider = FakeProvider(AppResult.failure(AppError.RateLimited("slow down")))
        val healthyProvider = FakeProvider(AppResult.success("retry succeeded"))
        val vm = ChatViewModel(
            RecordingScope(), FakeProvider(AppResult.success("unused")),
            providerForModel = { profileId, _ ->
                attempts += profileId
                if (profileId == "code") limitedProvider else healthyProvider
            },
            defaultModelProfileId = { "general" },
        )
        vm.setModelChoices(listOf(
            AiSettings(id = "general", name = "General", baseUrl = "https://g.example/v1", model = "g", hasSavedApiKey = true),
            AiSettings(id = "code", name = "Code", baseUrl = "https://c.example/v1", model = "c", hasSavedApiKey = true, supportedTasks = setOf(ModelTask.CODING)),
        ))
        vm.selectModel(ChatModelChoice.AUTO_ID)
        vm.updateInput("implement this function")
        vm.send()
        awaitSettled(vm)
        assertEquals(listOf("code"), attempts) // one provider per request; no implicit retry/failover
        assertTrue(vm.state.notice!!.contains("频繁"))

        vm.retry(0) // a user action in Auto mode authorizes a fresh routing decision
        awaitSettled(vm)
        assertEquals(listOf("code", "general"), attempts)
        assertEquals("retry succeeded", vm.state.messages.last().text)
    }

    @Test
    fun oneMessageModelOverrideDoesNotChangeSessionDefault() {
        val provider = FakeProvider(AppResult.success("ok"))
        val routed = mutableListOf<String>()
        val vm = ChatViewModel(
            RecordingScope(), provider,
            defaultModelProfileId = { "general" },
            providerForModel = { profileId, _ -> routed += profileId; provider },
        )
        vm.setModelChoices(listOf(
            AiSettings(id = "general", name = "General", baseUrl = "https://g.example/v1", model = "g", hasSavedApiKey = true),
            AiSettings(id = "coder", name = "Coder", baseUrl = "https://c.example/v1", model = "c", hasSavedApiKey = true),
        ))
        assertTrue(vm.selectModelForNextMessage("coder"))
        assertEquals("general", vm.state.selectedModelId)
        vm.updateInput("first")
        vm.send()
        awaitSettled(vm)
        assertEquals(listOf("coder"), routed)
        assertEquals("general", vm.state.selectedModelId)

        vm.updateInput("second")
        vm.send()
        awaitSettled(vm)
        assertEquals(listOf("coder", "general"), routed)
    }

    @Test
    fun oneMessageVisionOverrideAllowsImageWithoutChangingTextOnlySessionDefault() {
        val provider = FakeProvider(AppResult.success("image analyzed"))
        val routed = mutableListOf<String>()
        val vm = ChatViewModel(
            RecordingScope(), provider,
            defaultModelProfileId = { "text" },
            providerForModel = { profileId, _ -> routed += profileId; provider },
        )
        vm.setModelChoices(listOf(
            AiSettings(id = "text", name = "Text", baseUrl = "https://t.example/v1", model = "text", hasSavedApiKey = true),
            AiSettings(id = "vision", name = "Vision", baseUrl = "https://v.example/v1", model = "vision", hasSavedApiKey = true, supportsImages = true),
        ))
        vm.addAttachments(listOf(ChatAttachment(name = "image.png", mimeType = "image/png", localPath = "/tmp/image.png")))
        assertFalse(vm.state.canSend)
        assertTrue(vm.selectModelForNextMessage("vision"))
        assertTrue(vm.state.canSend)
        vm.send()
        awaitSettled(vm)
        assertEquals(listOf("vision"), routed)
        assertEquals("text", vm.state.selectedModelId)
        assertNull(vm.state.nextMessageModelId)
    }

    @Test
    fun imageAttachmentRequiresVisionModelAndIsSentWithUserTurn() {
        val provider = FakeProvider(AppResult.success("看到了"))
        val vm = newVm(provider)
        vm.setModelChoices(listOf(
            AiSettings(id = "text", name = "文本", baseUrl = "https://text.example/v1", model = "text", hasSavedApiKey = true),
            AiSettings(id = "vision", name = "视觉", baseUrl = "https://vision.example/v1", model = "vision", hasSavedApiKey = true, supportsImages = true),
        ))
        vm.selectModel("text")
        val image = ChatAttachment(name = "photo.png", mimeType = "image/png", localPath = "/private/photo.png")
        vm.addAttachments(listOf(image))
        assertFalse(vm.state.canSend)
        assertTrue(vm.state.notice!!.contains("图片输入"))

        vm.selectModel("vision")
        assertTrue(vm.state.canSend)
        vm.send()
        assertEquals(image, provider.received.single().last().attachments.single())
        assertEquals(listOf(image), vm.state.messages.first().attachments)
        assertTrue(vm.state.pendingAttachments.isEmpty())
    }

    @Test
    fun requestStoresOnlyRealProviderTokenUsageAndTiming() {
        val usage = MemoryUsage()
        val provider = object : AiProvider {
            override suspend fun complete(messages: List<ChatMessage>) = AppResult.success("unused")
            override suspend fun streamWithUsage(
                messages: List<ChatMessage>,
                onPhase: (AiRequestPhase) -> Unit,
                onDelta: (String) -> Unit,
                onUsage: (AiTokenUsage) -> Unit,
            ): AppResult<String> {
                onDelta("reply")
                onUsage(AiTokenUsage(11, 5))
                return AppResult.success("reply")
            }
        }
        val testScope = RecordingScope()
        val vm = ChatViewModel(testScope, provider, usageRepository = usage)
        vm.updateInput("question")
        vm.send()

        assertEquals(1, usage.records.size)
        assertEquals(11, usage.records.single().promptTokens)
        assertEquals(5, usage.records.single().completionTokens)
        assertTrue(usage.records.single().durationMs >= 0)
        assertNull(usage.records.single().errorType)
    }

    @Test
    fun editingUserMessageRestoresInputAndTruncatesDependentReplies() {
        val attachment = ChatAttachment(name = "notes.md", mimeType = "text/markdown", textContent = "body")
        val history = MemoryHistory(listOf(
            ChatMessage(id = "u1", sessionId = "current", role = MessageRole.USER, content = "first"),
            ChatMessage(id = "a1", sessionId = "current", role = MessageRole.ASSISTANT, content = "answer"),
            ChatMessage(id = "u2", sessionId = "current", role = MessageRole.USER, content = "second", attachments = listOf(attachment)),
        ))
        val vm = ChatViewModel(RecordingScope(), FakeProvider(AppResult.success("unused")), sessionsRepository = history)

        assertTrue(vm.editUserMessage(2))
        assertEquals("second", vm.state.inputText)
        assertEquals(listOf("first", "answer"), vm.state.messages.map { it.text })
        assertEquals(listOf(attachment), vm.state.pendingAttachments)
        assertEquals(listOf("first", "answer"), history.messages.map { it.content })
    }

    @Test
    fun pinTogglePersistsWithoutChangingMessagesOrSessionIdentity() {
        val history = MemoryHistory(listOf(ChatMessage(sessionId = "current", role = MessageRole.USER, content = "保留内容")))
        val vm = ChatViewModel(RecordingScope(), FakeProvider(AppResult.success("回复")), sessionsRepository = history)
        val id = vm.state.activeSessionId
        val beforeMessages = history.messages

        assertTrue(vm.toggleSessionPinned(id))
        assertTrue(vm.state.sessions.single().isPinned)
        assertEquals(id, history.snapshot.sessions.single().session.id)
        assertEquals(beforeMessages, history.messages)
        assertTrue(history.snapshot.sessions.single().session.isPinned)
        assertTrue(vm.toggleSessionPinned(id))
        assertFalse(vm.state.sessions.single().isPinned)
        assertEquals(beforeMessages, history.messages)
    }

    @Test
    fun requestReportsPreparingRequestingReceivingAndCompletedPhases() {
        val phases = mutableListOf<ChatRequestStatus>()
        val provider = object : AiProvider {
            override suspend fun complete(messages: List<ChatMessage>) = AppResult.success("完成")
            override suspend fun complete(messages: List<ChatMessage>, onPhase: (AiRequestPhase) -> Unit): AppResult<String> {
                onPhase(AiRequestPhase.REQUESTING)
                kotlinx.coroutines.yield()
                onPhase(AiRequestPhase.RECEIVING_REPLY)
                kotlinx.coroutines.yield()
                return AppResult.success("完成")
            }
        }
        val vm = newVm(provider)
        vm.onStateChanged = { phases += it.requestStatus }
        vm.updateInput("状态测试")
        vm.send()

        assertTrue(phases.contains(ChatRequestStatus.PREPARING))
        assertTrue(phases.contains(ChatRequestStatus.REQUESTING))
        assertTrue(phases.contains(ChatRequestStatus.RECEIVING_REPLY))
        assertEquals(ChatRequestStatus.COMPLETED, vm.state.requestStatus)
    }

    @Test
    fun switchingSessionCancelsOldRequestAndDoesNotShowItsResultInNewSession() {
        val history = MemoryHistory().apply {
            val first = ChatSessionData(ChatSession(id = "current", title = "当前"), emptyList())
            val second = ChatSessionData(ChatSession(id = "other", title = "另一个"), emptyList())
            snapshot = ChatSessionsSnapshot(listOf(first, second), "current")
        }
        var cancelled = false
        val provider = object : AiProvider {
            override suspend fun complete(messages: List<ChatMessage>): AppResult<String> = suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { cancelled = true }
            }
        }
        val vm = ChatViewModel(RecordingScope(), provider, sessionsRepository = history)
        vm.updateInput("旧会话请求")
        vm.send()
        vm.selectSession("other")

        assertTrue(cancelled)
        assertEquals("other", vm.state.activeSessionId)
        assertTrue(vm.state.messages.isEmpty())
        assertEquals(ChatRequestStatus.IDLE, vm.state.requestStatus)
        assertFalse(vm.state.isLoading)
        vm.selectSession("current")
        assertEquals(listOf(MessageStatus.CANCELLED), vm.state.messages.map { it.status })
        assertEquals(listOf("旧会话请求"), history.snapshot.sessions.first { it.session.id == "current" }.messages.map { it.content })
    }

    @Test
    fun regenerateReplacesReplyWithoutDuplicatingAndUsesOriginalQuestion() {
        val history = MemoryHistory(listOf(
            ChatMessage(id = "u", sessionId = "current", role = MessageRole.USER, content = "原问题"),
            ChatMessage(id = "a", sessionId = "current", role = MessageRole.ASSISTANT, content = "旧回答"),
        ))
        val provider = FakeProvider(AppResult.success("新回答"))
        val vm = ChatViewModel(RecordingScope(), provider, sessionsRepository = history)
        vm.regenerate(1)
        assertEquals(listOf("原问题"), provider.received.single().map { it.content })
        assertEquals(listOf("原问题", "新回答"), vm.state.messages.map { it.text })
        assertEquals(listOf("原问题", "新回答"), history.messages.map { it.content })
    }

    @Test
    fun failedRegenerationPreservesExistingReplyAndHistory() {
        val history = MemoryHistory(listOf(
            ChatMessage(id = "u", sessionId = "current", role = MessageRole.USER, content = "原问题"),
            ChatMessage(id = "a", sessionId = "current", role = MessageRole.ASSISTANT, content = "旧回答"),
        ))
        val vm = ChatViewModel(RecordingScope(), FakeProvider(AppResult.failure(AppError.Network("offline"))), sessionsRepository = history)
        vm.regenerate(1)
        assertEquals("旧回答", vm.state.messages[1].text)
        assertEquals("旧回答", history.messages[1].content)
        assertFalse(vm.state.isLoading)
        assertTrue(vm.state.notice!!.contains("网络连接失败"))
    }

    @Test
    fun regenerationFailureStatusDoesNotLeakAcrossSessions() {
        val history = MemoryHistory(listOf(
            ChatMessage(id = "u", sessionId = "current", role = MessageRole.USER, content = "原问题"),
            ChatMessage(id = "a", sessionId = "current", role = MessageRole.ASSISTANT, content = "原回答"),
        )).apply {
            snapshot = snapshot.copy(
                sessions = snapshot.sessions + ChatSessionData(ChatSession(id = "other", title = "另一会话"), emptyList()),
            )
        }
        val vm = ChatViewModel(RecordingScope(), FakeProvider(AppResult.failure(AppError.Network("offline"))), sessionsRepository = history)
        vm.regenerate(1)
        assertEquals(ChatRequestStatus.FAILED, vm.state.requestStatus)
        vm.selectSession("other")
        assertEquals(ChatRequestStatus.IDLE, vm.state.requestStatus)
        vm.selectSession("current")
        assertEquals(ChatRequestStatus.FAILED, vm.state.requestStatus)
        assertEquals("原回答", vm.state.messages.last().text)
    }

    @Test
    fun retryReusesFailedUserMessageInsteadOfAppendingDuplicate() {
        val failed = ChatUiState(messages = listOf(ChatBubble(MessageRole.USER, "问题", MessageStatus.FAILED)))
        val retryVm = ChatViewModel(RecordingScope(), FakeProvider(AppResult.success("恢复成功")), failed)
        retryVm.retry(0)
        assertEquals(listOf("问题", "恢复成功"), retryVm.state.messages.map { it.text })
    }

    @Test
    fun retryAfterNetworkFailureReusesOriginalMessageAndCompletes() {
        var calls = 0
        val provider = object : AiProvider {
            override suspend fun complete(messages: List<ChatMessage>): AppResult<String> {
                calls++
                return if (calls == 1) AppResult.failure(AppError.Network("offline")) else AppResult.success("恢复")
            }
        }
        val vm = newVm(provider)
        vm.updateInput("同一条问题")
        vm.send()
        assertEquals(ChatRequestStatus.FAILED, vm.state.requestStatus)
        vm.retry(0)

        assertEquals(2, calls)
        assertEquals(listOf("同一条问题", "恢复"), vm.state.messages.map { it.text })
        assertEquals(ChatRequestStatus.COMPLETED, vm.state.requestStatus)
    }

    @Test
    fun cancelMarksUserMessageCancelledAndDoesNotCreateReply() {
        var cancelled = false
        val history = MemoryHistory()
        val provider = object : AiProvider {
            override suspend fun complete(messages: List<ChatMessage>): AppResult<String> = suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { cancelled = true }
            }
        }
        val vm = ChatViewModel(RecordingScope(), provider, sessionsRepository = history)
        vm.updateInput("停止我")
        vm.send()
        vm.cancel()
        assertTrue(cancelled)
        assertEquals(1, vm.state.messages.size)
        assertEquals(MessageStatus.CANCELLED, vm.state.messages.single().status)
        assertEquals(MessageStatus.CANCELLED, history.messages.single().status)
        assertFalse(vm.state.isLoading)
        assertEquals(ChatRequestStatus.CANCELLED, vm.state.requestStatus)
    }

    @Test
    fun streamedPartialReplyIsVisibleAndPreservedWhenConnectionFails() {
        val history = MemoryHistory()
        var streamCalled = false
        val provider = object : AiProvider {
            override suspend fun complete(messages: List<ChatMessage>) = AppResult.failure<String>(AppError.Network("offline"))
            override suspend fun stream(
                messages: List<ChatMessage>,
                onPhase: (AiRequestPhase) -> Unit,
                onDelta: (String) -> Unit,
            ): AppResult<String> {
                streamCalled = true
                onDelta("partial ")
                onDelta("reply")
                return AppResult.failure(AppError.Network("offline"))
            }
        }
        val requestScope = RecordingScope()
        val vm = ChatViewModel(requestScope, provider, sessionsRepository = history)
        vm.updateInput("流式问题")
        vm.send()

        assertTrue("provider.stream was not invoked", streamCalled)
        assertTrue(requestScope.caught.joinToString { it.stackTraceToString() }, requestScope.caught.isEmpty())
        assertEquals(listOf("流式问题", "partial reply"), vm.state.messages.map { it.text })
        assertEquals(MessageStatus.FAILED, vm.state.messages[0].status)
        assertEquals(MessageStatus.FAILED, vm.state.messages[1].status)
        assertEquals(listOf("流式问题", "partial reply"), history.messages.map { it.content })
    }

    @Test
    fun cancellationKeepsPartialStreamMarkedCancelled() {
        val provider = object : AiProvider {
            override suspend fun complete(messages: List<ChatMessage>): AppResult<String> = error("stream expected")
            override suspend fun stream(
                messages: List<ChatMessage>,
                onPhase: (AiRequestPhase) -> Unit,
                onDelta: (String) -> Unit,
            ): AppResult<String> = suspendCancellableCoroutine { continuation ->
                onDelta("partial")
                continuation.invokeOnCancellation { }
            }
        }
        val vm = newVm(provider)
        vm.updateInput("停止")
        vm.send()
        assertEquals("partial", vm.state.messages.last().text)
        vm.cancel()
        assertEquals(MessageStatus.CANCELLED, vm.state.messages.last().status)
        assertEquals(ChatRequestStatus.CANCELLED, vm.state.requestStatus)
    }

    @Test
    fun interruptedGenerationRestoresAsRetryableFailure() {
        val history = MemoryHistory(listOf(
            ChatMessage(sessionId = "current", role = MessageRole.USER, content = "中断的问题", status = MessageStatus.GENERATING),
        ))
        val vm = ChatViewModel(RecordingScope(), FakeProvider(AppResult.success("回复")), sessionsRepository = history)
        assertEquals(MessageStatus.FAILED, vm.state.messages.single().status)
        vm.retry(0)
        assertEquals(listOf("中断的问题", "回复"), vm.state.messages.map { it.text })
    }

    @Test
    fun providerExceptionBecomesRetryableFailureWithoutCrashing() {
        val vm = newVm(object : AiProvider {
            override suspend fun complete(messages: List<ChatMessage>): AppResult<String> = error("transport failure")
        })
        vm.updateInput("检查异常")
        vm.send()
        assertFalse(vm.state.isLoading)
        assertEquals(MessageStatus.FAILED, vm.state.messages.single().status)
        assertTrue(vm.state.notice!!.contains("请求失败"))
        assertTrue(scope.caught.isEmpty())
    }

    @Test
    fun copyHelperCopiesCompleteTextAndSignalsSuccess() {
        var copied: String? = null
        var feedback = false
        copyMessageText("第一行\n完整的第二行", { copied = it }) { feedback = true }
        assertEquals("第一行\n完整的第二行", copied)
        assertTrue(feedback)
    }

    @Test
    fun errorToUserMessage_coversAllTypes() {
        assertTrue(ChatViewModel.errorToUserMessage(AppError.Network(null)).contains("网络"))
        assertTrue(ChatViewModel.errorToUserMessage(AppError.Network(kind = com.tim.futureai.core.common.NetworkFailureKind.TIMEOUT)).contains("超时"))
        assertTrue(ChatViewModel.errorToUserMessage(AppError.Configuration("sensitive" )).contains("配置"))
        assertTrue(ChatViewModel.errorToUserMessage(AppError.RateLimited(null)).contains("频繁"))
        assertTrue(ChatViewModel.errorToUserMessage(AppError.Server("sensitive", httpCode = 503)).contains("503"))
        assertTrue(ChatViewModel.errorToUserMessage(AppError.ModelUnavailable("sensitive", 404)).contains("模型"))
        assertTrue(ChatViewModel.errorToUserMessage(AppError.Http("sensitive", 400)).contains("400"))
        assertTrue(ChatViewModel.errorToUserMessage(AppError.Cancelled).contains("取消"))
        assertTrue(ChatViewModel.errorToUserMessage(AppError.Unknown("sensitive")).contains("请求失败"))
        assertFalse(ChatViewModel.errorToUserMessage(AppError.Http("test-key-do-not-log", 400)).contains("test-key-do-not-log"))
    }
}
