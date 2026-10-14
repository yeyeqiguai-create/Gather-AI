package com.tim.futureai.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ChatModelsTest {

    private val fixed = Instant.parse("2026-10-14T12:00:00Z")

    // ---- MessageRole ----

    @Test
    fun messageRole_hasAllFourValues() {
        assertEquals(
            listOf("SYSTEM", "USER", "ASSISTANT", "TOOL"),
            MessageRole.entries.map { it.name },
        )
    }

    // ---- ChatMessage ----

    @Test
    fun chatMessage_generatesUniqueStableIds() {
        val a = ChatMessage(sessionId = "s", role = MessageRole.USER, content = "hi")
        val b = ChatMessage(sessionId = "s", role = MessageRole.USER, content = "hi")
        assertNotEquals(a.id, b.id)
        // 同一实例 id 稳定不变
        assertEquals(a.id, a.copy().id)
    }

    @Test
    fun chatMessage_defaultsCreatedAtToNow() {
        val before = Instant.now().minusSeconds(1)
        val m = ChatMessage(sessionId = "s", role = MessageRole.SYSTEM, content = "x")
        assertTrue(m.createdAt.isAfter(before))
    }

    @Test
    fun chatMessage_acceptsExplicitInstant() {
        val m = ChatMessage(sessionId = "s", role = MessageRole.TOOL, content = "r", createdAt = fixed)
        assertEquals(fixed, m.createdAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun chatMessage_rejectsBlankContent() {
        ChatMessage(sessionId = "s", role = MessageRole.USER, content = "  ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun chatMessage_rejectsBlankSessionId() {
        ChatMessage(sessionId = "", role = MessageRole.USER, content = "hi")
    }

    @Test
    fun chatMessage_equalityBasedOnFields() {
        val a = ChatMessage(id = "m1", sessionId = "s", role = MessageRole.USER, content = "hi", createdAt = fixed)
        val b = ChatMessage(id = "m1", sessionId = "s", role = MessageRole.USER, content = "hi", createdAt = fixed)
        assertEquals(a, b)
    }

    // ---- ChatSession ----

    @Test
    fun chatSession_defaultsUpdatedAtToCreatedAt() {
        val s = ChatSession(title = "chat")
        assertEquals(s.createdAt, s.updatedAt)
    }

    @Test
    fun chatSession_acceptsExplicitTimes() {
        val later = fixed.plusSeconds(60)
        val s = ChatSession(id = "cs1", title = "t", createdAt = fixed, updatedAt = later)
        assertEquals(later, s.updatedAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun chatSession_rejectsBlankTitle() {
        ChatSession(title = "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun chatSession_rejectsUpdatedAtBeforeCreatedAt() {
        ChatSession(title = "t", createdAt = fixed, updatedAt = fixed.minusSeconds(1))
    }

    @Test
    fun chatSession_generatesUniqueIds() {
        val a = ChatSession(title = "a")
        val b = ChatSession(title = "b")
        assertNotEquals(a.id, b.id)
        assertTrue(a.id.isNotBlank())
    }
}
