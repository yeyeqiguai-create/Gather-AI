package com.tim.futureai.feature.chat

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.tim.futureai.MainActivity
import com.tim.futureai.core.security.SecureKeyStore
import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.data.settings.AiSettingsManager
import com.tim.futureai.data.settings.AppPreferencesManager
import com.tim.futureai.domain.ai.AiSettings
import com.tim.futureai.domain.ai.SettingsSaveResult
import com.tim.futureai.domain.settings.AppPreferences
import com.tim.futureai.domain.settings.VisualTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Real activity + encrypted settings + HTTP/SSE provider. No external credentials or services. */
class V011ExperienceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var activity: ActivityScenario<MainActivity>? = null
    private lateinit var server: MockChatServer
    private var savedPreferences: Map<String, *> = emptyMap<String, Any?>()
    private var savedFiles = emptyMap<String, ByteArray?>()
    private val securePrefs get() = context.getSharedPreferences("futureai_secure", Context.MODE_PRIVATE)

    @Before fun prepare() {
        savedPreferences = securePrefs.all.toMap()
        savedFiles = listOf("chat-sessions.json", "chat-history.json", "usage-stats.json").associateWith {
            File(context.filesDir, it).takeIf(File::exists)?.readBytes()
        }
        server = MockChatServer()
        val secure = SecureKeyStore(context)
        assertTrue(AppPreferencesManager(secure).save(AppPreferences(onboardingDone = true, startupUpdateCheck = false,
            visualTheme = VisualTheme.GRAPHITE, glassIntensity = 0.55f)))
        val settings = AiSettingsManager(secure)
        assertEquals(SettingsSaveResult.Saved, settings.save(AiSettings(id = "v011-local-qa", name = "本地验收服务",
            baseUrl = server.url, model = "gather-ui-test", apiKey = "local-test-only")))
        assertTrue(settings.selectProfile("v011-local-qa"))
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("新建对话").performClick()
    }

    @After fun restore() {
        activity?.close()
        if (::server.isInitialized) server.close()
        val edit = securePrefs.edit().clear()
        savedPreferences.forEach { (key, value) -> if (value is String) edit.putString(key, value) }
        edit.commit()
        savedFiles.forEach { (name, bytes) ->
            val file = File(context.filesDir, name)
            if (bytes != null) file.writeBytes(bytes) else file.delete()
        }
    }

    @Test fun streamingStopRetryMarkdownNavigationThemesAndDrafts() {
        capture("04-dark-welcome")
        send("帮我整理一份专注工作的计划")
        waitForText("验证完成。")
        compose.onNodeWithText("复制代码").assertExists()
        capture("05-markdown-dark")
        compose.onAllNodesWithContentDescription("复制消息").onLast().performClick()
        compose.runOnUiThread {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            assertTrue(clipboard.primaryClip?.getItemAt(0)?.text.toString().contains("验证完成。"))
        }
        compose.onAllNodesWithContentDescription("消息操作").onLast().performClick()
        compose.onNodeWithText("收藏消息").performClick()
        compose.onNodeWithText("★").assertExists()

        server.slow.set(true)
        send("继续展开这个计划")
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("停止生成").fetchSemanticsNodes().isNotEmpty() }
        capture("06-streaming-glass")
        compose.onNodeWithContentDescription("停止生成").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("停止生成").fetchSemanticsNodes().isEmpty() }
        server.slow.set(false)
        assertTrue(server.requests.get() >= 2)

        server.failNext.set(true)
        send("重试验收")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("发送失败 · 可重试").fetchSemanticsNodes().isNotEmpty() }
        val beforeRetry = server.requests.get()
        compose.onAllNodesWithText("重试", useUnmergedTree = false).onFirst().performClick()
        compose.waitUntil(15_000) { server.requests.get() > beforeRetry && compose.onAllNodesWithContentDescription("停止生成").fetchSemanticsNodes().isEmpty() }
        waitForText("验证完成。")

        compose.onNodeWithContentDescription("消息输入框").performTextReplacement("明天继续完善这个想法")
        compose.onNodeWithContentDescription("打开会话列表").performClick()
        compose.onNodeWithText("草稿 · 明天继续完善这个想法").performScrollTo().assertIsDisplayed()
        capture("07-conversation-drawer")
        compose.onNodeWithText("设置", substring = false).performClick()
        compose.onNodeWithText("外观", substring = false).performClick()
        capture("08-appearance-dark")
        compose.onNodeWithText("纯白", substring = false).performClick()
        capture("09-appearance-light")
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitForIdle()
        capture("10-settings-light")
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("消息输入框").assertTextEquals("明天继续完善这个想法")
        activity!!.recreate()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("消息输入框").assertTextEquals("明天继续完善这个想法")
        capture("11-chat-light-restored")
        compose.onNodeWithContentDescription("更多功能").performClick()
        compose.onAllNodesWithText("设备互联", substring = false).onLast().performClick()
        compose.onNodeWithText("Gather Link").assertIsDisplayed()
        capture("12-gather-link")
    }

    @Test fun reducedMotionLargeTextAndExpandedDraftRemainUsable() {
        activity!!.close()
        val preferences = AppPreferencesManager(SecureKeyStore(context))
        assertTrue(preferences.save(preferences.load().copy(animationsEnabled = false, lowPerformanceMode = true,
            fontScale = 1.25f, visualTheme = VisualTheme.MONOCHROME_WHITE)))
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("展开编辑").performClick()
        val draft = "这是需要保留的长草稿。".repeat(18)
        compose.onNodeWithContentDescription("展开的草稿编辑框").performTextReplacement(draft)
        compose.onNodeWithText("保存草稿").performClick()
        compose.onNodeWithContentDescription("消息输入框").assertTextEquals(draft)
        capture("13-large-text-lightweight")
        compose.onNodeWithContentDescription("清空草稿").performClick()
        compose.onNodeWithText("清空", substring = false).performClick()
        compose.onNodeWithContentDescription("消息输入框").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithContentDescription("打开会话列表").performClick()
        compose.onNodeWithText("设置", substring = false).performClick()
        compose.onNodeWithText("AI 服务商", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("连接你信赖的模型").assertIsDisplayed()
        capture("14-providers-large-text")
    }

    private fun send(text: String) {
        compose.onNodeWithContentDescription("消息输入框").performTextReplacement(text)
        compose.onNodeWithContentDescription("发送消息").performClick()
    }
    private fun waitForText(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodesWithContentDescription("停止生成").fetchSemanticsNodes().isEmpty()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        // Native screenshot reads the GPU surface, one frame after Compose's
        // semantics become idle (especially for recorded graphics layers).
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(200)
        val dir = File(context.filesDir, "v011-verification").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private class MockChatServer : AutoCloseable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${server.localPort}/v1"
        val requests = AtomicInteger()
        val slow = AtomicBoolean(false)
        val failNext = AtomicBoolean(false)
        init { thread(isDaemon = true, name = "v011-mock-accept") {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { socket.use {
                    it.soTimeout = 10_000
                    val input = it.getInputStream().buffered()
                    val headerBytes = java.io.ByteArrayOutputStream()
                    var ending = ""
                    while (ending != "\r\n\r\n") {
                        val next = input.read(); if (next < 0) break
                        headerBytes.write(next); ending = (ending + next.toChar()).takeLast(4)
                    }
                    val headers = headerBytes.toString("US-ASCII")
                    val line = headers.lineSequence().first()
                    val length = Regex("(?im)^Content-Length:\\s*(\\d+)").find(headers)?.groupValues?.get(1)?.toInt() ?: 0
                    var remaining = length
                    while (remaining > 0) { val n = input.read(ByteArray(remaining)); if (n < 0) break; remaining -= n }
                    val out = it.getOutputStream().bufferedWriter()
                    if (line.startsWith("GET")) {
                        out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"data\":[{\"id\":\"gather-ui-test\"}]}"); out.flush()
                    } else {
                        requests.incrementAndGet()
                        if (failNext.compareAndSet(true, false)) {
                            out.write("HTTP/1.1 503 Service Unavailable\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":{\"message\":\"Local retry test\"}}"); out.flush()
                        } else {
                            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n"); out.flush()
                            val slowRequest = slow.get()
                            val answer = if (slowRequest) (1..50).joinToString("\n\n") { "第 $it 步：保持专注，清晰地完成一件事。" } else
                                "## 留白，让思路更清晰\n\n把今天最重要的一件事写下来，给它一段**不受打扰**的时间。\n\n| 时段 | 安排 |\n| :--- | :--- |\n| 上午 | 专注创作 |\n| 下午 | 协作与复盘 |\n\n```python\nfocus = [\"思考\", \"行动\", \"复盘\"]\nfor step in focus:\n    print(step)\n```\n\n> 好的计划，给变化留一点空间。\n\n验证完成。"
                            for (chunk in answer.chunked(12)) {
                                out.write("data: {\"choices\":[{\"delta\":{\"content\":${JsonMini.quote(chunk)}}}]}\n\n"); out.flush()
                                Thread.sleep(if (slowRequest) 160 else 30)
                            }
                            out.write("data: {\"choices\":[],\"usage\":{\"prompt_tokens\":24,\"completion_tokens\":96,\"total_tokens\":120}}\n\ndata: [DONE]\n\n"); out.flush()
                        }
                    }
                } } }
            }
        } }
        override fun close() { server.close() }
    }
}
