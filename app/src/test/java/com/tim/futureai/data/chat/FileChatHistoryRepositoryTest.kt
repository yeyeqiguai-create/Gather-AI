package com.tim.futureai.data.chat

import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.MessageRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

class FileChatHistoryRepositoryTest {
    @Test
    fun savesAndRestoresMessagesInOrder() = runBlocking {
        val dir = Files.createTempDirectory("chat-history").toFile()
        try {
            val repo = FileChatHistoryRepository(File(dir, "history.json"))
            val messages = listOf(
                ChatMessage(id = "u1", sessionId = "current", role = MessageRole.USER, content = "你好", createdAt = Instant.parse("2026-01-01T00:00:00Z")),
                ChatMessage(id = "a1", sessionId = "current", role = MessageRole.ASSISTANT, content = "你好！", createdAt = Instant.parse("2026-01-01T00:00:01Z")),
            )
            assertTrue(repo.save(messages))
            assertEquals(messages, repo.load().messages)
            assertFalse(repo.load().failed)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun overwritingHistoryKeepsLatestMessages() = runBlocking {
        val dir = Files.createTempDirectory("chat-history-overwrite").toFile()
        try {
            val repo = FileChatHistoryRepository(File(dir, "history.json"))
            assertTrue(repo.save(listOf(ChatMessage(sessionId = "current", role = MessageRole.USER, content = "old"))))
            val latest = listOf(
                ChatMessage(sessionId = "current", role = MessageRole.USER, content = "new"),
                ChatMessage(sessionId = "current", role = MessageRole.ASSISTANT, content = "reply"),
            )
            assertTrue(repo.save(latest))
            assertEquals(latest, repo.load().messages)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun corruptHistoryReturnsRecoverableEmptyResult() = runBlocking {
        val dir = Files.createTempDirectory("chat-history-corrupt").toFile()
        try {
            val file = File(dir, "history.json").apply { writeText("not-json") }
            val loaded = FileChatHistoryRepository(file).load()
            assertTrue(loaded.failed)
            assertTrue(loaded.messages.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun storageFailureReturnsFalseWithoutThrowing() = runBlocking {
        val dir = Files.createTempDirectory("chat-history-failure").toFile()
        try {
            val blocker = File(dir, "not-a-directory").apply { writeText("x") }
            val repo = FileChatHistoryRepository(File(blocker, "history.json"))
            assertFalse(repo.save(listOf(ChatMessage(sessionId = "current", role = MessageRole.USER, content = "test"))))
        } finally {
            dir.deleteRecursively()
        }
    }
}
