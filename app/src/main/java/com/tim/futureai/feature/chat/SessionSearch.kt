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

/** Toggle all selectable sessions in the current filtered view, preserving selections outside it. */
internal fun toggleVisibleSessionSelection(
    selectedIds: Set<String>,
    visibleSessions: List<ChatSession>,
    activeSessionId: String,
): Set<String> {
    val visibleIds = visibleSessions.asSequence().map { it.id }.filter { it != activeSessionId }.toSet()
    if (visibleIds.isEmpty()) return selectedIds
    return if (selectedIds.containsAll(visibleIds)) selectedIds - visibleIds else selectedIds + visibleIds
}

internal fun toggleSessionSelection(selectedIds: Set<String>, sessionId: String, activeSessionId: String): Set<String> {
    if (sessionId == activeSessionId) return selectedIds
    return if (sessionId in selectedIds) selectedIds - sessionId else selectedIds + sessionId
}

/** Sorting only affects the displayed copy; pinned sessions stay first in every mode. */
internal fun sortAndFilterSessions(
    sessions: List<ChatSession>,
    query: String,
    sortOrder: SessionSortOrder,
    contentMatches: Set<String> = emptySet(),
): List<ChatSession> {
    val comparator = compareByDescending<ChatSession> { it.isPinned }
        .thenByDescending { if (sortOrder == SessionSortOrder.RECENT_ACTIVITY) it.updatedAt else it.createdAt }
    val sorted = sessions.sortedWith(comparator)
    if (query.isBlank()) return sorted
    val normalizedQuery = query.trim()
    return sorted.filter { it.title.contains(normalizedQuery, ignoreCase = true) || it.id in contentMatches }
}

/** Compact, local-only context around the first match. */
internal fun searchSnippet(text: String, query: String): String? {
    val needle = query.trim()
    if (needle.isEmpty()) return null
    val normalized = text.replace(Regex("\\s+"), " ").trim()
    val index = normalized.indexOf(needle, ignoreCase = true)
    if (index < 0) return null
    var start = (index - 25).coerceAtLeast(0)
    var end = (index + needle.length + 30).coerceAtMost(normalized.length)
    if (start > 0 && Character.isLowSurrogate(normalized[start]) && Character.isHighSurrogate(normalized[start - 1])) start--
    if (end < normalized.length && end > 0 && Character.isHighSurrogate(normalized[end - 1]) && Character.isLowSurrogate(normalized[end])) end++
    return (if (start > 0) "…" else "") + normalized.substring(start, end) + if (end < normalized.length) "…" else ""
}
