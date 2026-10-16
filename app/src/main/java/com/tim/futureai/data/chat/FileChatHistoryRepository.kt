package com.tim.futureai.data.chat

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.domain.chat.ChatHistoryLoad
import com.tim.futureai.domain.chat.ChatHistoryRepository
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/** JSON file storage for non-secret chat text. Writes use a temporary file and atomic rename. */
class FileChatHistoryRepository(private val file: File) : ChatHistoryRepository {
    override suspend fun load(): ChatHistoryLoad = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext ChatHistoryLoad(emptyList())
        try {
            val root = JsonMini.parse(file.readText()).asObject() ?: error("Invalid history")
            val items = root.entries["messages"]?.asArray()?.items ?: error("Missing messages")
            val messages = items.map { value ->
                val fields = value.asObject()?.entries ?: error("Invalid message")
                ChatMessage(
                    id = fields.string("id"),
                    sessionId = fields.string("sessionId"),
                    role = MessageRole.valueOf(fields.string("role")),
                    content = fields.string("content"),
                    createdAt = Instant.parse(fields.string("createdAt")),
                )
            }
            ChatHistoryLoad(messages)
        } catch (_: Exception) {
            ChatHistoryLoad(emptyList(), failed = true)
        }
    }

    override suspend fun save(messages: List<ChatMessage>): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = messages.joinToString(prefix = "{\"messages\":[", postfix = "]}") { message ->
                "{" + listOf(
                    "\"id\":" + JsonMini.quote(message.id),
                    "\"sessionId\":" + JsonMini.quote(message.sessionId),
                    "\"role\":" + JsonMini.quote(message.role.name),
                    "\"content\":" + JsonMini.quote(message.content),
                    "\"createdAt\":" + JsonMini.quote(message.createdAt.toString()),
                ).joinToString(",") + "}"
            }
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            val backup = File(file.parentFile, "${file.name}.bak")
            temp.writeText(json)
            if (backup.exists() && !backup.delete()) throw IllegalStateException("Cleanup failed")
            val hadPrevious = file.exists()
            if (hadPrevious && !file.renameTo(backup)) throw IllegalStateException("Backup failed")
            if (!temp.renameTo(file)) {
                if (hadPrevious) backup.renameTo(file)
                throw IllegalStateException("Replace failed")
            }
            if (backup.exists()) backup.delete()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun Map<String, JsonValue>.string(key: String): String =
        this[key]?.asString() ?: error("Invalid history field")
}
