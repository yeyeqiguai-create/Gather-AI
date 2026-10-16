package com.tim.futureai.feature.chat

import com.tim.futureai.domain.model.ChatSession
import java.time.Instant

internal enum class SessionSortOrder(val label: String) {
    RECENT_ACTIVITY("最近活动"),
    CREATED("创建时间"),
}

/** Filters the visible session list without mutating the source list. */
internal fun filterSessions(sessions: List<ChatSession>, query: String): List<ChatSession> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return sessions
    return sessions.filter { it.title.contains(normalizedQuery, ignoreCase = true) }
}

/** Sorting only affects the displayed copy; pinned sessions stay first in every mode. */
internal fun sortAndFilterSessions(
    sessions: List<ChatSession>,
    query: String,
    sortOrder: SessionSortOrder,
): List<ChatSession> {
    val comparator = compareByDescending<ChatSession> { it.isPinned }
        .thenByDescending { if (sortOrder == SessionSortOrder.RECENT_ACTIVITY) it.updatedAt else it.createdAt }
    return filterSessions(sessions.sortedWith(comparator), query)
}
