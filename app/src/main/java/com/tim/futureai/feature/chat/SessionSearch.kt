package com.tim.futureai.feature.chat

import com.tim.futureai.domain.model.ChatSession

/** Filters the visible session list without mutating the source list. */
internal fun filterSessions(sessions: List<ChatSession>, query: String): List<ChatSession> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return sessions
    return sessions.filter { it.title.contains(normalizedQuery, ignoreCase = true) }
}
