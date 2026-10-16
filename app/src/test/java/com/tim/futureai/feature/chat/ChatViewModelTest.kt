package com.tim.futureai.feature.chat

import com.tim.futureai.core.common.AppError
import com.tim.futureai.core.common.AppResult
import com.tim.futureai.domain.ai.AiProvider
import com.tim.futureai.domain.chat.ChatHistoryLoad
import com.tim.futureai.domain.chat.ChatHistoryRepository
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.MessageRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private class MemoryHistory(var messages: List<ChatMessage> = emptyList()) : ChatHistoryRepository {
        var failed = false
        override suspend fun load() = ChatHistoryLoad(messages, failed)
        override suspend fun save(messages: List<ChatMessage>): Boolean {
            this.messages = messages
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
        history.messages = listOf(first, ChatMessage(sessionId = "current", role = MessageRole.ASSISTANT, content = "先前的回答"))
        val scope = RecordingScope()
        val vm = ChatViewModel(scope, FakeProvider(AppResult.success("新回答")), historyRepository = history)
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
        val vm = ChatViewModel(RecordingScope(), FakeProvider(AppResult.success("ok")), historyRepository = history)
        assertFalse(vm.state.isHistoryLoading)
        assertTrue(vm.state.notice!!.contains("本地聊天记录读取失败"))
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
