package com.tim.futureai.feature.chat

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import androidx.lifecycle.Lifecycle
import com.tim.futureai.MainActivity
import org.junit.Rule
import org.junit.Test
import java.io.File

/** End-to-end checks against the real activity and the app-private session store. */
class ChatDraftUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun draftSurvivesSwitchBackgroundAndActivityRecreation() {
        compose.waitForIdle()
        if (compose.onAllNodes(androidx.compose.ui.test.hasText("先体验界面")).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("先体验界面").performClick()
        }
        compose.onNodeWithContentDescription("新建对话").performClick()
        val first = "待完成的周末计划${java.util.UUID.randomUUID().toString().take(8)}"
        val second = "另一份草稿${java.util.UUID.randomUUID().toString().take(8)}"
        compose.onNodeWithContentDescription("消息输入框").performTextInput(first)
        compose.onNodeWithContentDescription("打开会话列表").performClick()
        compose.onNodeWithText("草稿 · $first").assertIsDisplayed()
        compose.onNodeWithContentDescription("侧栏新建对话").performClick()
        compose.onNodeWithContentDescription("消息输入框").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithContentDescription("消息输入框").performTextInput(second)
        compose.onNodeWithContentDescription("打开会话列表").performClick()
        compose.onNodeWithText("草稿 · $first").performClick()
        compose.onNodeWithContentDescription("消息输入框").assertTextEquals(first)

        // No debounce wait here: onPause must flush the latest edit.
        compose.onNodeWithContentDescription("消息输入框").performTextReplacement(first + "，保存到后台")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("消息输入框").assertTextEquals(first + "，保存到后台")
        compose.onNodeWithContentDescription("打开会话列表").performClick()
        compose.onNodeWithText("草稿 · $second").assertIsDisplayed()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val target = File(compose.activity.filesDir, "draft-drawer-test.png")
        target.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("管理与归档对话").performClick()
        compose.onNodeWithText("草稿 · $second").assertIsDisplayed()
        compose.onNodeWithContentDescription("搜索会话").performTextInput(second)
        compose.onNodeWithText("草稿 · $first，保存到后台").assertDoesNotExist()
        assert(compose.onAllNodes(androidx.compose.ui.test.hasText("草稿 · $second")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun clearingDraftRemovesItsListMarker() {
        compose.waitForIdle()
        if (compose.onAllNodes(androidx.compose.ui.test.hasText("先体验界面")).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("先体验界面").performClick()
        }
        compose.onNodeWithContentDescription("消息输入框").performTextInput("清空测试")
        compose.onNodeWithContentDescription("消息输入框").performTextClearance()
        compose.onNodeWithContentDescription("打开会话列表").performClick()
        compose.onNodeWithText("草稿 · 清空测试").assertDoesNotExist()
    }
}
