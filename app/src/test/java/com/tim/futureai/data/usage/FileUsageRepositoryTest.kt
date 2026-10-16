package com.tim.futureai.data.usage

import com.tim.futureai.domain.usage.UsageRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileUsageRepositoryTest {
    @Test fun persistsProviderCountsErrorsAndDurationsWithoutInventingUnknownTokens() = runBlocking {
        val directory = Files.createTempDirectory("usage-ledger").toFile()
        try {
            val repository = FileUsageRepository(File(directory, "usage.json"))
            repository.append(UsageRecord("p1", "Provider · model", "model", 125, promptTokens = 10, completionTokens = 4))
            repository.append(UsageRecord("p1", "Provider · model", "model", 90, errorType = "Network"))
            val snapshot = FileUsageRepository(File(directory, "usage.json")).load()
            assertEquals(2, snapshot.requestCount)
            assertEquals(1, snapshot.errorCount)
            assertEquals(1, snapshot.unknownTokenCount)
            assertEquals(10L, snapshot.promptTokens)
            assertEquals(4L, snapshot.completionTokens)
            assertEquals(215L, snapshot.totalDurationMs)
            assertNull(snapshot.estimatedUsd(mapOf("p1" to 0.15), mapOf("p1" to 0.60)))
            assertTrue(snapshot.records.last().errorType == "Network")
        } finally { directory.deleteRecursively() }
    }
}
