package com.tim.futureai.domain.proactive

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveEngineTest {
    private var now = 1_000L
    private val posted = mutableListOf<ProactiveEvent>()

    private fun engine(cooldownMs: Long = 30 * 60 * 1000L) = ProactiveEngine(
        MemoryProactiveRepository(), sink = ProactiveNotificationSink { posted.add(it); true },
        clock = { now }, idSeed = { "s" }, defaultCooldownMs = cooldownMs,
    )

    @Test fun duplicateEventsInsideCooldownAreRecordedButNotReposted() = runBlocking {
        val engine = engine()
        val first = engine.emit(ProactiveEventType.TASK_FAILED, "task-t1", "任务失败", "d", 2)
        assertNotNull(first)
        assertEquals(1, posted.size)
        now += 60_000 // 1 minute later: inside 30min cooldown
        val second = engine.emit(ProactiveEventType.TASK_FAILED, "task-t1", "任务失败", "d", 2)
        assertNull(second)
        assertEquals(1, posted.size)
        // occurrences still tracked on the stored event
        assertEquals(2, engine.history().single().occurrences)
    }

    @Test fun afterCooldownOrResolveNewNotificationIsPosted() = runBlocking {
        val engine = engine(cooldownMs = 60_000)
        engine.emit(ProactiveEventType.TASK_FAILED, "task-t1", "t", "d", 2)
        now += 120_000
        assertNotNull(engine.emit(ProactiveEventType.TASK_FAILED, "task-t1", "t", "d", 2))
        assertEquals(2, posted.size)
    }

    @Test fun snoozeMuteResolveTransitionsWork() = runBlocking {
        val engine = engine()
        val event = engine.emit(ProactiveEventType.HEALTH_ISSUE, "health-c1", "t", "d", 3)!!
        assertTrue(engine.snooze(event.eventId, now + 3_600_000))
        now += 60_000
        assertTrue(engine.center().single().status == ProactiveStatus.SNOOZED)
        now += 4_000_000
        // snooze expired: reappears in center
        assertEquals(ProactiveStatus.PENDING, engine.center().single().status)
        assertTrue(engine.mute(event.eventId))
        assertTrue(engine.center().isEmpty())
        assertTrue(engine.resolve(event.eventId))
    }

    @Test fun deniedNotificationsStayPendingAsDegraded() = runBlocking {
        var allowed = false
        val engine = ProactiveEngine(
            MemoryProactiveRepository(), sink = ProactiveNotificationSink { allowed },
            clock = { now }, idSeed = { "s" },
        )
        val event = engine.emit(ProactiveEventType.MODEL_UNAVAILABLE, "m1", "t", "d", 3)!!
        assertEquals(ProactiveStatus.PENDING, event.status)
        allowed = true
        assertEquals(1, engine.recoverOnStartup()) // startup recovery re-delivers
        val stored = engine.history().single()
        assertEquals(ProactiveStatus.NOTIFIED, stored.status)
        assertTrue(stored.notificationDegraded)
    }
}

class MemoryProactiveRepository : ProactiveRepository {
    val events = mutableListOf<ProactiveEvent>()
    override suspend fun save(event: ProactiveEvent) { events.removeAll { it.eventId == event.eventId }; events.add(event) }
    override suspend fun byId(eventId: String): ProactiveEvent? = events.firstOrNull { it.eventId == eventId }
    override suspend fun all(): List<ProactiveEvent> = events.toList()
    override suspend fun unresolvedByDedupKey(dedupKey: String): ProactiveEvent? =
        events.firstOrNull { it.dedupKey == dedupKey && it.status != ProactiveStatus.RESOLVED && it.status != ProactiveStatus.MUTED }
}
