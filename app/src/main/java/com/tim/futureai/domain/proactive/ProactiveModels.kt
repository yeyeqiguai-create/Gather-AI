package com.tim.futureai.domain.proactive

enum class ProactiveEventType {
    TASK_FAILED, TASK_STALLED, MODEL_UNAVAILABLE, UPDATE_AVAILABLE, HEALTH_ISSUE, REPAIR_FAILED, USER_REMINDER,
}

enum class ProactiveStatus { PENDING, NOTIFIED, SNOOZED, MUTED, RESOLVED }

/**
 * One proactive record. [dedupKey] drives cooldown so repeated failures do not
 * spam the user; [eventId] is the unified ID linking checks, repairs and records.
 */
data class ProactiveEvent(
    val eventId: String,
    val type: ProactiveEventType,
    val dedupKey: String,
    val title: String,
    val detail: String,
    val severity: Int, // 1=info 2=warning 3=critical
    val createdAtMs: Long,
    val lastSeenAtMs: Long,
    val occurrences: Int,
    val status: ProactiveStatus,
    val relatedKind: String, // TASK | CHECK | UPDATE | NONE
    val relatedId: String,
    val snoozedUntilMs: Long?,
    val notifiedAtMs: Long?,
    val notificationDegraded: Boolean, // true when system notification was impossible (e.g. permission off)
)

/** Platform boundary for system notifications; UI-less devices may no-op. */
fun interface ProactiveNotificationSink {
    /** Posts a system notification; returns false when impossible (permission denied etc.). */
    suspend fun notify(event: ProactiveEvent): Boolean
}

interface ProactiveRepository {
    suspend fun save(event: ProactiveEvent)
    suspend fun byId(eventId: String): ProactiveEvent?
    suspend fun all(): List<ProactiveEvent>
    suspend fun unresolvedByDedupKey(dedupKey: String): ProactiveEvent?
}
