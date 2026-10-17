package com.tim.futureai.domain.task

/** Audit history is append-only through the engine and contains clipped, credential-redacted summaries. */
class TaskAuditLogger(private val clock: TaskClock = SystemTaskClock) {
    fun append(aggregate: TaskAggregate, type: TaskEventType, summary: String, stepId: String? = null): TaskAggregate {
        val event = TaskEvent(
            sequence = (aggregate.events.maxOfOrNull { it.sequence } ?: 0L) + 1,
            taskId = aggregate.task.id,
            stepId = stepId,
            type = type,
            summary = TaskPrivacy.sanitize(summary).take(600),
            createdAtMs = clock.nowMs(),
        )
        return aggregate.copy(events = (aggregate.events + event).takeLast(MAX_EVENTS_PER_TASK))
    }

    companion object { const val MAX_EVENTS_PER_TASK = 2_000 }
}
