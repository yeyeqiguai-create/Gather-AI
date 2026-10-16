package com.tim.futureai.feature.chat

import com.tim.futureai.domain.model.ChatSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

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

    @Test
    fun sortingKeepsPinnedSessionsFirstAndDoesNotMutateSource() {
        val old = Instant.parse("2026-01-01T00:00:00Z")
        val middle = Instant.parse("2026-01-02T00:00:00Z")
        val newest = Instant.parse("2026-01-03T00:00:00Z")
        val data = listOf(
            ChatSession(id = "recent", title = "日常", createdAt = old, updatedAt = newest),
            ChatSession(id = "pinned-old", title = "项目旧", createdAt = old, updatedAt = old, isPinned = true),
            ChatSession(id = "created", title = "项目新建", createdAt = middle, updatedAt = middle),
        )

        assertEquals(listOf("pinned-old", "recent", "created"), sortAndFilterSessions(data, "", SessionSortOrder.RECENT_ACTIVITY).map { it.id })
        assertEquals(listOf("pinned-old", "created", "recent"), sortAndFilterSessions(data, "", SessionSortOrder.CREATED).map { it.id })
        assertEquals(listOf("pinned-old", "created"), sortAndFilterSessions(data, "项目", SessionSortOrder.CREATED).map { it.id })
        assertEquals(listOf("recent", "pinned-old", "created"), data.map { it.id })
    }
}
