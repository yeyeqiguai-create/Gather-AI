package com.tim.futureai.data.usage

import com.tim.futureai.domain.usage.UsageRecord
import com.tim.futureai.domain.usage.UsageSnapshot
import com.tim.futureai.domain.usage.UsagePriceKey
import com.tim.futureai.domain.usage.ModelUsagePrice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileUsageRepositoryTest {
    @Test fun usageBreakdownGroupsProviderAndModelAndKeepsMissingCostUnknown() {
        val snapshot = UsageSnapshot(listOf(
            UsageRecord("p1", "Provider A", "chat", 100, promptTokens = 1_000, completionTokens = 500),
            UsageRecord("p1", "Provider A", "chat", 200, errorType = "RateLimited"),
            UsageRecord("p2", "Provider B", "reasoner", 300, promptTokens = 2_000, completionTokens = 1_000),
        ))
        val prices = mapOf(
            UsagePriceKey("p1", "chat") to ModelUsagePrice(1.0, 2.0),
            UsagePriceKey("p2", "reasoner") to ModelUsagePrice(2.0, 4.0),
        )
        val groups = snapshot.byProviderAndModel(prices)
        assertEquals(2, groups.size)
        assertEquals("Provider A", groups[0].providerName)
        assertEquals("chat", groups[0].model)
        assertEquals(2, groups[0].requestCount)
        assertEquals(1, groups[0].errorCount)
        assertEquals(1, groups[0].knownUsageCount)
        assertEquals(1, groups[0].unknownUsageCount)
        assertNull(groups[0].estimatedUsd)
        assertEquals(0.008, groups[1].estimatedUsd!!, 0.0000001)
        assertNull(snapshot.estimatedUsdByModel(prices))
    }

    @Test fun persistsProviderCountsErrorsAndDurationsWithoutInventingUnknownTokens() = runBlocking {
        val directory = Files.createTempDirectory("usage-ledger").toFile()
        try {
            val repository = FileUsageRepository(File(directory, "usage.json"))
            repository.append(UsageRecord("p1", "Provider · model", "model", 125, promptTokens = 10, completionTokens = 4, sessionId = "session-a", taskId = "task-a", taskStepId = "plan"))
            repository.append(UsageRecord("p1", "Provider · model", "model", 90, errorType = "Network", sessionId = "session-b"))
            val snapshot = FileUsageRepository(File(directory, "usage.json")).load()
            assertEquals(2, snapshot.requestCount)
            assertEquals(1, snapshot.errorCount)
            assertEquals(1, snapshot.unknownTokenCount)
            assertEquals(10L, snapshot.promptTokens)
            assertEquals(4L, snapshot.completionTokens)
            assertEquals(215L, snapshot.totalDurationMs)
            assertEquals("session-a", snapshot.records.first().sessionId)
            assertEquals("task-a", snapshot.records.first().taskId)
            assertEquals("plan", snapshot.records.first().taskStepId)
            assertEquals(2, snapshot.bySession().size)
            assertEquals(1, snapshot.bySession().first { it.sessionId == "session-b" }.errorCount)
            assertNull(snapshot.estimatedUsdByModel(mapOf(UsagePriceKey("p1", "model") to ModelUsagePrice(0.15, 0.60))))
            assertTrue(snapshot.records.last().errorType == "Network")
        } finally { directory.deleteRecursively() }
    }

    @Test fun clearRemovesUsageRecordsWithoutTouchingOtherFiles() = runBlocking {
        val directory = Files.createTempDirectory("usage-clear").toFile()
        try {
            val ledger = File(directory, "usage.json")
            val other = File(directory, "chat.json").apply { writeText("history") }
            val repository = FileUsageRepository(ledger)
            repository.append(UsageRecord("p", "Provider", "model", 10))
            repository.clear()
            assertEquals(0, FileUsageRepository(ledger).load().requestCount)
            assertEquals("history", other.readText())
        } finally { directory.deleteRecursively() }
    }
}
