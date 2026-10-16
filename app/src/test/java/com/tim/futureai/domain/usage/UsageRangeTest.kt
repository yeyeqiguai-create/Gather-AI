package com.tim.futureai.domain.usage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class UsageRangeTest {
    @Test fun rangesFilterRecordsByTimestampAndAllRetainsUnknownTimestamps() {
        val now = Instant.parse("2026-10-16T00:00:00Z")
        val snapshot = UsageSnapshot(listOf(
            UsageRecord(null, "P", "recent", 1, recordedAt = now.minusSeconds(5 * 86400L).toString()),
            UsageRecord(null, "P", "month", 1, recordedAt = now.minusSeconds(20 * 86400L).toString()),
            UsageRecord(null, "P", "old", 1, recordedAt = now.minusSeconds(40 * 86400L).toString()),
            UsageRecord(null, "P", "unknown-time", 1, recordedAt = "invalid"),
        ))
        assertEquals(listOf("recent"), snapshot.within(UsageRange.LAST_7_DAYS, now).records.map { it.model })
        assertEquals(listOf("recent", "month"), snapshot.within(UsageRange.LAST_30_DAYS, now).records.map { it.model })
        assertEquals(4, snapshot.within(UsageRange.ALL, now).requestCount)
    }
}
