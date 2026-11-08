package com.tim.futureai.data.chat

import com.tim.futureai.domain.chat.ChatSessionData
import com.tim.futureai.domain.chat.ChatDraft
import com.tim.futureai.domain.chat.ChatSessionsSnapshot
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.ChatAttachment
import com.tim.futureai.domain.model.ChatSession
import com.tim.futureai.domain.model.MessageRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileChatSessionsRepositoryTest {
    @Test fun interruptedReplacementRecoversBackupAndPreservesOriginalBytes() = runBlocking {
        val dir = Files.createTempDirectory("sessions-recovery").toFile()
        try {
            val file = File(dir, "sessions.json")
            val backup = File(dir, "sessions.json.bak")
            val repo = FileChatSessionsRepository(file, File(dir, "legacy.json"))
            val session = ChatSession(id = "old", title = "需要恢复")
            val snapshot = ChatSessionsSnapshot(listOf(ChatSessionData(session, emptyList(), ChatDraft("未发送"))), session.id)
            assertTrue(repo.save(snapshot))
            val original = file.readBytes()
            assertTrue(file.renameTo(backup))
            val loaded = repo.load()
            assertTrue(loaded.recoveredBackup)
            assertEquals(snapshot, loaded.snapshot)
            assertTrue(repo.save(loaded.snapshot))
            assertEquals(snapshot, repo.load().snapshot)
            val preserved = dir.listFiles()!!.single { it.name.startsWith("sessions.json.recovered-") }
            assertArrayEquals(original, preserved.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun corruptPrimaryFallsBackToPreservedRecoveryWithoutDeletingEvidence() = runBlocking {
        val dir = Files.createTempDirectory("sessions-corrupt-primary").toFile()
        try {
            val file = File(dir, "sessions.json")
            val repo = FileChatSessionsRepository(file, File(dir, "legacy.json"))
            val s = ChatSession(title = "有效备份")
            val snapshot = ChatSessionsSnapshot(listOf(ChatSessionData(s, emptyList())), s.id)
            assertTrue(repo.save(snapshot))
            val original = file.readBytes()
            val saved = File(dir, "sessions.json.recovered-example.bak")
            assertTrue(file.renameTo(saved))
            file.writeText("broken")
            File(dir, "sessions.json.bak").writeText("also broken")
            val loaded = repo.load()
            assertTrue(loaded.recoveredBackup)
            assertEquals(snapshot, loaded.snapshot)
            assertEquals("broken", file.readText())
            assertArrayEquals(original, saved.readBytes())
        } finally { dir.deleteRecursively() }
    }
    @Test fun draftsRoundTripWithoutBecomingMessagesIncludingFailedAttachments() = runBlocking {
        val dir = Files.createTempDirectory("session-drafts").toFile()
        try {
            val file = File(dir, "sessions.json")
            val draft = ChatDraft("  未完成\n\"第二行\" 😀  ", listOf(
                ChatAttachment(name = "notes.txt", mimeType = "text/plain", textContent = "附件内容"),
                ChatAttachment(name = "photo.png", mimeType = "image/png", localPath = "/private/photo.png", previewPath = "/private/preview.jpg"),
                ChatAttachment(name = "report.pdf", mimeType = "application/pdf", error = "暂不支持此格式"),
            ))
            val session = ChatSession(title = "有草稿")
            val snapshot = ChatSessionsSnapshot(listOf(ChatSessionData(session, emptyList(), draft)), session.id)
            val repo = FileChatSessionsRepository(file, File(dir, "legacy.json"))
            assertTrue(repo.save(snapshot))
            val restored = FileChatSessionsRepository(file, File(dir, "legacy.json")).load()
            assertFalse(restored.failed)
            assertEquals(snapshot, restored.snapshot)
            assertTrue(restored.snapshot.sessions.single().messages.isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun savesAndRestoresSeparatedSessionsAndActiveSelection() = runBlocking {
        val dir = Files.createTempDirectory("sessions").toFile()
        try {
            val repo = FileChatSessionsRepository(File(dir, "sessions.json"), File(dir, "legacy.json"))
            val first = ChatSession(id = "s1", title = "第一段", modelProfileId = "provider-a")
            val second = ChatSession(id = "s2", title = "第二段", isPinned = true)
            val snapshot = ChatSessionsSnapshot(listOf(
                ChatSessionData(first, listOf(ChatMessage(
                    sessionId = "s1", role = MessageRole.USER, content = "甲",
                    attachments = listOf(ChatAttachment(name = "a.txt", mimeType = "text/plain", textContent = "附件正文")),
                ))),
                ChatSessionData(second, listOf(ChatMessage(sessionId = "s2", role = MessageRole.USER, content = "乙"))),
            ), "s2")
            assertTrue(repo.save(snapshot))
            val restored = repo.load().snapshot
            assertEquals(snapshot, restored)
            assertEquals("s2", restored.activeSessionId)
            assertEquals("provider-a", restored.sessions[0].session.modelProfileId)
            assertEquals(listOf("甲"), restored.sessions[0].messages.map { it.content })
            assertEquals("附件正文", restored.sessions[0].messages.single().attachments.single().textContent)
            assertEquals(listOf("乙"), restored.sessions[1].messages.map { it.content })
        } finally { dir.deleteRecursively() }
    }

    @Test fun oldSessionRecordsWithoutPinFieldLoadUnpinnedAndMessagesStayIntact() = runBlocking {
        val dir = Files.createTempDirectory("sessions-no-pin").toFile()
        try {
            val file = File(dir, "sessions.json")
            file.writeText("""{"version":"2","activeSessionId":"s1","sessions":[{"id":"s1","title":"保留","createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z","messages":[{"id":"m1","sessionId":"s1","role":"USER","content":"历史内容","createdAt":"2026-01-01T00:00:01Z","status":"SENT"}]}]}""")
            val loaded = FileChatSessionsRepository(file, File(dir, "legacy.json")).load()
            assertFalse(loaded.failed)
            assertTrue(loaded.snapshot.sessions.single().draft.isEmpty)
            assertFalse(loaded.snapshot.sessions.single().session.isPinned)
            assertEquals("历史内容", loaded.snapshot.sessions.single().messages.single().content)
            val pinned = loaded.snapshot.copy(sessions = loaded.snapshot.sessions.map { it.copy(session = it.session.copy(isPinned = true)) })
            assertTrue(FileChatSessionsRepository(file, File(dir, "legacy.json")).save(pinned))
            val restored = FileChatSessionsRepository(file, File(dir, "legacy.json")).load().snapshot
            assertTrue(restored.sessions.single().session.isPinned)
            assertEquals("历史内容", restored.sessions.single().messages.single().content)
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
