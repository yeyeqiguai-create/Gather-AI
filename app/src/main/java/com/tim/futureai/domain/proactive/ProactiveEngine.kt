package com.tim.futureai.domain.proactive

/**
 * Event queue with dedup keys, per-key cooldown, snooze/mute, and execution
 * records. Notification permission loss degrades gracefully: events stay
 * PENDING in the in-app center and are marked notificationDegraded.
 */
class ProactiveEngine(
    private val repository: ProactiveRepository,
    private val sink: ProactiveNotificationSink,
    private val clock: () -> Long,
    private val idSeed: () -> String,
    private val defaultCooldownMs: Long = 30 * 60 * 1000L,
) {
    /** Emits an event unless an unresolved one with the same key is inside its cooldown window. */
    suspend fun emit(
        type: ProactiveEventType,
        dedupKey: String,
        title: String,
        detail: String,
        severity: Int,
        relatedKind: String = "NONE",
        relatedId: String = "",
        cooldownMs: Long = defaultCooldownMs,
    ): ProactiveEvent? {
        val now = clock()
        val existing = repository.unresolvedByDedupKey(dedupKey)
        if (existing != null && now - existing.lastSeenAtMs < cooldownMs &&
            existing.status != ProactiveStatus.RESOLVED && existing.status != ProactiveStatus.MUTED
        ) {
            repository.save(existing.copy(lastSeenAtMs = now, occurrences = existing.occurrences + 1))
            return null // inside cooldown: recorded, not re-notified
        }
        val event = ProactiveEvent(
            eventId = "pe-$now-${idSeed()}", type = type, dedupKey = dedupKey,
            title = title, detail = detail, severity = severity.coerceIn(1, 3),
            createdAtMs = existing?.createdAtMs ?: now, lastSeenAtMs = now, occurrences = (existing?.occurrences ?: 0) + 1,
            status = ProactiveStatus.PENDING, relatedKind = relatedKind, relatedId = relatedId,
            snoozedUntilMs = null, notifiedAtMs = null, notificationDegraded = false,
        )
        val delivered = runCatching { sink.notify(event) }.getOrDefault(false)
        val stored = if (delivered) event.copy(status = ProactiveStatus.NOTIFIED, notifiedAtMs = now) else event
        repository.save(stored)
        return stored
    }

    suspend fun snooze(eventId: String, untilMs: Long): Boolean = transition(eventId) {
        it.copy(status = ProactiveStatus.SNOOZED, snoozedUntilMs = untilMs)
    }

    suspend fun mute(eventId: String): Boolean = transition(eventId) {
        it.copy(status = ProactiveStatus.MUTED)
    }

    suspend fun resolve(eventId: String): Boolean = transition(eventId) {
        it.copy(status = ProactiveStatus.RESOLVED, snoozedUntilMs = null)
    }

    /** Re-deliver PENDING events (e.g. created while notifications were unavailable). */
    suspend fun recoverOnStartup(): Int {
        var recovered = 0
        for (event in repository.all()) {
            if (event.status != ProactiveStatus.PENDING) continue
            val delivered = runCatching { sink.notify(event) }.getOrDefault(false)
            if (delivered) {
                repository.save(event.copy(status = ProactiveStatus.NOTIFIED, notifiedAtMs = clock(), notificationDegraded = true))
                recovered++
            }
        }
        return recovered
    }

    /** Center view: unresolved events first by severity/recency; snoozed until time arrives. */
    suspend fun center(): List<ProactiveEvent> {
        val now = clock()
        return repository.all()
            .filter { it.status != ProactiveStatus.RESOLVED && it.status != ProactiveStatus.MUTED }
            .map { if (it.status == ProactiveStatus.SNOOZED && (it.snoozedUntilMs ?: 0L) <= now) it.copy(status = ProactiveStatus.PENDING) else it }
            .sortedWith(compareByDescending<ProactiveEvent> { it.severity }.thenByDescending { it.lastSeenAtMs })
    }

    suspend fun history(): List<ProactiveEvent> = repository.all().sortedByDescending { it.lastSeenAtMs }

    private suspend fun transition(eventId: String, transform: (ProactiveEvent) -> ProactiveEvent): Boolean {
        val event = repository.byId(eventId) ?: return false
        repository.save(transform(event))
        return true
    }
}
