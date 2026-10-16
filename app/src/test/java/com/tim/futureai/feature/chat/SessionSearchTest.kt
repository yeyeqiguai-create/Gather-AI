package com.tim.futureai.feature.chat

import com.tim.futureai.domain.model.ChatSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SessionSearchTest {
    private val sessions = listOf(
        ChatSession(id = "1", title = "旅行计划"),
        ChatSession(id = "2", title = "Kotlin 问题"),
        ChatSession(id = "3", title = "工作整理"),
    )

    @Test
    fun filtersTitlesCaseInsensitivelyWithoutChangingSource() {
        assertEquals(listOf("2"), filterSessions(sessions, "kOtLiN").map { it.id })
        assertEquals(listOf("1"), filterSessions(sessions, "  旅行  ").map { it.id })
        assertEquals(listOf("1", "2", "3"), sessions.map { it.id })
    }

    @Test
    fun noMatchReturnsEmptyList() {
        assertEquals(emptyList<ChatSession>(), filterSessions(sessions, "不存在"))
    }

    @Test
    fun clearingSearchRestoresOriginalList() {
        filterSessions(sessions, "旅行")
        assertSame(sessions, filterSessions(sessions, ""))
        assertSame(sessions, filterSessions(sessions, "   "))
    }
}
