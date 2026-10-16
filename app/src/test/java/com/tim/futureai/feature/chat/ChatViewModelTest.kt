package com.tim.futureai.feature.chat

import com.tim.futureai.core.common.AppError
import com.tim.futureai.core.common.AppResult
import com.tim.futureai.domain.ai.AiProvider
import com.tim.futureai.domain.chat.ChatSessionData
import com.tim.futureai.domain.chat.ChatSessionsLoad
import com.tim.futureai.domain.chat.ChatSessionsRepository
import com.tim.futureai.domain.chat.ChatSessionsSnapshot
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.ChatSession
import com.tim.futureai.domain.model.MessageRole
import com.tim.futureai.domain.model.MessageStatus
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
        assertEquals("鉴权失败：请检查 API Key 是否有效", vm.state.notice)
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
        val p = FakeProvider(AppResult.failure(AppError.Unknown("AI 服务未配置：缺少 Base URL、模型名或 API Key")))
        val vm = newVm(p)
        vm.updateInput("hi")
        vm.send()
        awaitSettled(vm)
        assertTrue(vm.state.notice!!.contains("未配置"))
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
        assertTrue(vm.state.notice!!.contains("网络错误"))
    }

    @Test
    fun retryReusesFailedUserMessageInsteadOfAppendingDuplicate() {
        val failed = ChatUiState(messages = listOf(ChatBubble(MessageRole.USER, "问题", MessageStatus.FAILED)))
        val retryVm = ChatViewModel(RecordingScope(), FakeProvider(AppResult.success("恢复成功")), failed)
        retryVm.retry(0)
        assertEquals(listOf("问题", "恢复成功"), retryVm.state.messages.map { it.text })
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
        assertTrue(ChatViewModel.errorToUserMessage(AppError.RateLimited(null)).contains("频繁"))
        assertTrue(ChatViewModel.errorToUserMessage(AppError.Server("boom")).contains("服务端"))
        assertTrue(ChatViewModel.errorToUserMessage(AppError.Cancelled).contains("取消"))
        assertTrue(ChatViewModel.errorToUserMessage(AppError.Unknown(null)).contains("未知"))
    }
}
