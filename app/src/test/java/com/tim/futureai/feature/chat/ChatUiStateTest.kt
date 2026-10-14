package com.tim.futureai.feature.chat

import com.tim.futureai.domain.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUiStateTest {

    @Test
    fun initialState_canSendIsFalse() {
        val s = ChatUiState()
        assertFalse(s.canSend)
        assertTrue(s.messages.isEmpty())
    }

    @Test
    fun updateInput_setsTextAndEnablesSend() {
        val s = ChatUiState().updateInput("hello")
        assertEquals("hello", s.inputText)
        assertTrue(s.canSend)
    }

    @Test
    fun updateInput_blankOnly_disablesSend() {
        val s = ChatUiState().updateInput("   ")
        assertFalse(s.canSend)
    }

    @Test
    fun send_appendsUserMessageAndClearsInput() {
        val s = ChatUiState().updateInput("hi there").send()
        assertEquals(1, s.messages.size)
        assertEquals(MessageRole.USER, s.messages[0].role)
        assertEquals("hi there", s.messages[0].text)
        assertEquals("", s.inputText)
    }

    @Test
    fun send_trimsInput() {
        val s = ChatUiState().updateInput("  padded  ").send()
        assertEquals("padded", s.messages[0].text)
    }

    @Test
    fun send_blankInputIsForbidden() {
        val before = ChatUiState().updateInput("   ")
        val after = before.send()
        assertTrue(after.messages.isEmpty())
        assertEquals(before, after)
    }

    @Test
    fun send_multipleTimes_appendsInOrder() {
        var s = ChatUiState()
        s = s.updateInput("one").send()
        s = s.updateInput("two").send()
        assertEquals(listOf("one", "two"), s.messages.map { it.text })
    }

    @Test
    fun send_doesNotMutateOriginalState() {
        val before = ChatUiState().updateInput("hi")
        val after = before.send()
        assertEquals(0, before.messages.size)
        assertEquals(1, after.messages.size)
    }
}
