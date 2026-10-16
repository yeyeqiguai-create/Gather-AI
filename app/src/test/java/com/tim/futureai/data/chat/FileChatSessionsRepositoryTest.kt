package com.tim.futureai.data.chat

import com.tim.futureai.domain.chat.ChatSessionData
import com.tim.futureai.domain.chat.ChatSessionsSnapshot
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.ChatSession
import com.tim.futureai.domain.model.MessageRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileChatSessionsRepositoryTest {
    @Test fun savesAndRestoresSeparatedSessionsAndActiveSelection() = runBlocking {
        val dir = Files.createTempDirectory("sessions").toFile()
        try {
            val repo = FileChatSessionsRepository(File(dir, "sessions.json"), File(dir, "legacy.json"))
            val first = ChatSession(id = "s1", title = "第一段")
            val second = ChatSession(id = "s2", title = "第二段")
            val snapshot = ChatSessionsSnapshot(listOf(
                ChatSessionData(first, listOf(ChatMessage(sessionId = "s1", role = MessageRole.USER, content = "甲"))),
                ChatSessionData(second, listOf(ChatMessage(sessionId = "s2", role = MessageRole.USER, content = "乙"))),
            ), "s2")
            assertTrue(repo.save(snapshot))
            val restored = repo.load().snapshot
            assertEquals(snapshot, restored)
            assertEquals("s2", restored.activeSessionId)
            assertEquals(listOf("甲"), restored.sessions[0].messages.map { it.content })
            assertEquals(listOf("乙"), restored.sessions[1].messages.map { it.content })
        } finally { dir.deleteRecursively() }
    }

    @Test fun migratesLegacyMessagesAndNeverOverwritesSource() = runBlocking {
        val dir = Files.createTempDirectory("sessions-migrate").toFile()
        try {
            val newFile = File(dir, "sessions.json")
            val oldFile = File(dir, "chat-history.json")
            oldFile.writeText("""{"messages":[{"id":"old-1","sessionId":"current","role":"USER","content":"旧问题","createdAt":"2026-01-01T00:00:00Z","status":"SENT"},{"id":"old-2","sessionId":"current","role":"ASSISTANT","content":"旧回答","createdAt":"2026-01-01T00:00:01Z","status":"SENT"}]}""")
            val repo = FileChatSessionsRepository(newFile, oldFile)
            val migrated = repo.load()
            assertTrue(migrated.migratedLegacy)
            assertEquals(listOf("旧问题", "旧回答"), migrated.snapshot.sessions.single().messages.map { it.content })
            assertEquals("旧问题", migrated.snapshot.sessions.single().session.title)
            assertTrue(repo.save(migrated.snapshot))
            assertTrue(oldFile.exists())
            assertEquals(listOf("旧问题", "旧回答"), repo.load().snapshot.sessions.single().messages.map { it.content })
        } finally { dir.deleteRecursively() }
    }

    @Test fun migrationFailureKeepsLegacyBytesUntouched() = runBlocking {
        val dir = Files.createTempDirectory("sessions-bad-migrate").toFile()
        try {
            val legacy = File(dir, "legacy.json").apply { writeText("broken-json") }
            val before = legacy.readBytes()
            val loaded = FileChatSessionsRepository(File(dir, "sessions.json"), legacy).load()
            assertTrue(loaded.failed)
            assertArrayEquals(before, legacy.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun saveFailureDoesNotThrow() = runBlocking {
        val dir = Files.createTempDirectory("sessions-fail").toFile()
        try {
            val blocker = File(dir, "not-dir").apply { writeText("x") }
            val repo = FileChatSessionsRepository(File(blocker, "sessions.json"), File(dir, "old.json"))
            assertFalse(repo.save(ChatSessionsSnapshot(listOf(ChatSessionData(ChatSession(title = "x"), emptyList())), null)))
        } finally { dir.deleteRecursively() }
    }
}
