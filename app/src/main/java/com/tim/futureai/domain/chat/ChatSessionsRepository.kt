package com.tim.futureai.domain.chat

import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.ChatSession

data class ChatSessionData(val session: ChatSession, val messages: List<ChatMessage>)
data class ChatSessionsSnapshot(val sessions: List<ChatSessionData>, val activeSessionId: String?)
data class ChatSessionsLoad(val snapshot: ChatSessionsSnapshot, val failed: Boolean = false, val migratedLegacy: Boolean = false)

interface ChatSessionsRepository {
    /** Reads the session store; legacy import must not mutate the source file. */
    suspend fun load(): ChatSessionsLoad
    suspend fun save(snapshot: ChatSessionsSnapshot): Boolean
}
