package com.tim.futureai.data.chat

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.domain.chat.ChatSessionData
import com.tim.futureai.domain.chat.ChatSessionsLoad
import com.tim.futureai.domain.chat.ChatSessionsRepository
import com.tim.futureai.domain.chat.ChatSessionsSnapshot
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.ChatAttachment
import com.tim.futureai.domain.model.ChatSession
import com.tim.futureai.domain.model.MessageRole
import com.tim.futureai.domain.model.MessageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/** Versioned session store. The legacy single-session file is only read, never overwritten. */
class FileChatSessionsRepository(private val file: File, private val legacyFile: File) : ChatSessionsRepository {
    private val writeMutex = Mutex()
    override suspend fun load(): ChatSessionsLoad = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                if (!legacyFile.exists()) return@withContext ChatSessionsLoad(defaultSnapshot())
                val old = decodeMessages(JsonMini.parse(legacyFile.readText()).asObject()?.entries ?: error("Invalid legacy history"))
                val session = ChatSession(title = old.firstOrNull { it.role == MessageRole.USER }?.content?.take(48) ?: "新对话")
                val migrated = old.map { it.copy(sessionId = session.id) }
                return@withContext ChatSessionsLoad(ChatSessionsSnapshot(listOf(ChatSessionData(session, migrated)), session.id), migratedLegacy = true)
            }
            val root = JsonMini.parse(file.readText()).asObject()?.entries ?: error("Invalid sessions")
            require(root.string("version") == "2")
            val sessions = root["sessions"]?.asArray()?.items?.map { item ->
                val fields = item.asObject()?.entries ?: error("Invalid session")
                val session = ChatSession(
                    id = fields.string("id"), title = fields.string("title"),
                    createdAt = Instant.parse(fields.string("createdAt")),
                    updatedAt = Instant.parse(fields.string("updatedAt")),
                    isPinned = fields["isPinned"]?.let { (it as? JsonValue.JBool)?.value ?: error("Invalid pin state") } ?: false,
                    isArchived = (fields["isArchived"] as? JsonValue.JBool)?.value ?: false,
                    modelProfileId = fields["modelProfileId"]?.asString(),
                )
                ChatSessionData(session, decodeMessages(fields))
            } ?: error("Missing sessions")
            val active = root.string("activeSessionId").takeIf { id -> sessions.any { it.session.id == id } }
                ?: sessions.firstOrNull()?.session?.id
            ChatSessionsLoad(ChatSessionsSnapshot(sessions, active))
        } catch (_: Exception) {
            ChatSessionsLoad(defaultSnapshot(), failed = true)
        }
    }

    override suspend fun save(snapshot: ChatSessionsSnapshot): Boolean = writeMutex.withLock { withContext(Dispatchers.IO) {
        try {
            require(snapshot.sessions.isNotEmpty())
            require(snapshot.sessions.map { it.session.id }.distinct().size == snapshot.sessions.size)
            require(snapshot.activeSessionId == null || snapshot.sessions.any { it.session.id == snapshot.activeSessionId })
            val sessionsJson = snapshot.sessions.joinToString(",") { data ->
                val s = data.session
                "{" + listOf(
                    "\"id\":" + JsonMini.quote(s.id), "\"title\":" + JsonMini.quote(s.title),
                    "\"createdAt\":" + JsonMini.quote(s.createdAt.toString()), "\"updatedAt\":" + JsonMini.quote(s.updatedAt.toString()),
                    "\"isPinned\":" + s.isPinned + ",\"isArchived\":" + s.isArchived,
                    "\"modelProfileId\":" + (s.modelProfileId?.let(JsonMini::quote) ?: "null"),
                    "\"messages\":" + encodeMessages(data.messages),
                ).joinToString(",") + "}"
            }
            val json = "{\"version\":\"2\",\"activeSessionId\":" + JsonMini.quote(snapshot.activeSessionId ?: "") + ",\"sessions\":[$sessionsJson]}"
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            val backup = File(file.parentFile, "${file.name}.bak")
            temp.writeText(json)
            if (backup.exists()) backup.delete()
            val hadOld = file.exists()
            if (hadOld && !file.renameTo(backup)) error("Backup failed")
            if (!temp.renameTo(file)) { if (hadOld) backup.renameTo(file); error("Replace failed") }
            backup.delete()
            true
        } catch (_: Exception) { false }
    } }

    private fun decodeMessages(fields: Map<String, JsonValue>): List<ChatMessage> =
        (fields["messages"]?.asArray()?.items ?: error("Missing messages")).map { item ->
            val m = item.asObject()?.entries ?: error("Invalid message")
            ChatMessage(
                id = m.string("id"), sessionId = m.string("sessionId"), role = MessageRole.valueOf(m.string("role")),
                content = m.string("content"), createdAt = Instant.parse(m.string("createdAt")),
                status = m["status"]?.asString()?.let(MessageStatus::valueOf) ?: MessageStatus.SENT,
                attachments = m["attachments"]?.asArray()?.items.orEmpty().map { item ->
                    val a = item.asObject()?.entries ?: error("Invalid attachment")
                    ChatAttachment(
                        id = a["id"]?.asString().orEmpty().ifBlank { java.util.UUID.randomUUID().toString() },
                        name = a.string("name"), mimeType = a.string("mimeType"),
                        localPath = a["localPath"]?.asString().orEmpty(),
                        previewPath = a["previewPath"]?.asString().orEmpty(),
                        textContent = a["textContent"]?.asString().orEmpty(),
                    )
                },
            )
        }

    private fun encodeMessages(messages: List<ChatMessage>): String = messages.joinToString(prefix = "[", postfix = "]") { m ->
        "{" + listOf(
            "\"id\":" + JsonMini.quote(m.id), "\"sessionId\":" + JsonMini.quote(m.sessionId),
            "\"role\":" + JsonMini.quote(m.role.name), "\"content\":" + JsonMini.quote(m.content),
            "\"createdAt\":" + JsonMini.quote(m.createdAt.toString()), "\"status\":" + JsonMini.quote(m.status.name),
            "\"attachments\":" + m.attachments.joinToString(",", prefix = "[", postfix = "]") { a ->
                "{" + listOf(
                    "\"id\":" + JsonMini.quote(a.id), "\"name\":" + JsonMini.quote(a.name),
                    "\"mimeType\":" + JsonMini.quote(a.mimeType), "\"localPath\":" + JsonMini.quote(a.localPath),
                    "\"previewPath\":" + JsonMini.quote(a.previewPath), "\"textContent\":" + JsonMini.quote(a.textContent),
                ).joinToString(",") + "}"
            },
        ).joinToString(",") + "}"
    }

    private fun defaultSnapshot(): ChatSessionsSnapshot {
        val session = ChatSession(title = "新对话")
        return ChatSessionsSnapshot(listOf(ChatSessionData(session, emptyList())), session.id)
    }

    private fun Map<String, JsonValue>.string(key: String): String = this[key]?.asString() ?: error("Invalid field")
}
