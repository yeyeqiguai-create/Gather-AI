package com.tim.futureai.domain.chat

import com.tim.futureai.domain.model.ChatMessage

data class ChatHistoryLoad(val messages: List<ChatMessage>, val failed: Boolean = false)

/** Persistence boundary for the current conversation only. */
interface ChatHistoryRepository {
    suspend fun load(): ChatHistoryLoad
    suspend fun save(messages: List<ChatMessage>): Boolean
}
