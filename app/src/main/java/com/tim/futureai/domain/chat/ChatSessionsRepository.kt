package com.tim.futureai.domain.chat

import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.ChatSession
import com.tim.futureai.domain.model.ChatAttachment

/** Unsent content stays local and is never part of the provider's message history. */
data class ChatDraft(val text: String = "", val attachments: List<ChatAttachment> = emptyList()) {
    val isEmpty: Boolean get() = text.isEmpty() && attachments.isEmpty()
    val preview: String get() = text.trim().replace(Regex("\\s+"), " ").take(60)
        .ifEmpty { attachments.joinToString("、") { it.name }.take(60).ifEmpty { "未发送的文字" } }
}

data class ChatSessionData(
    val session: ChatSession,
    val messages: List<ChatMessage>,
    val draft: ChatDraft = ChatDraft(),
)
data class ChatSessionsSnapshot(val sessions: List<ChatSessionData>, val activeSessionId: String?)
data class ChatSessionsLoad(
    val snapshot: ChatSessionsSnapshot,
    val failed: Boolean = false,
    val migratedLegacy: Boolean = false,
    val recoveredBackup: Boolean = false,
)

interface ChatSessionsRepository {
    /** Reads the session store; legacy import must not mutate the source file. */
    suspend fun load(): ChatSessionsLoad
    suspend fun save(snapshot: ChatSessionsSnapshot): Boolean
}
