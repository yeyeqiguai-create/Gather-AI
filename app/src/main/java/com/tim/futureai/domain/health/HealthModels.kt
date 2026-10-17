package com.tim.futureai.domain.health

/** Health check outcomes are always one of four explicitly distinct states. */
enum class HealthStatus { OK, WARNING, FAILED, UNVERIFIABLE }

enum class HealthTrigger { MANUAL, PERIODIC, EVENT, STARTUP }

/** One executed check. [evidence] carries concrete, user-visible proof (values, paths, counts). */
data class HealthCheckResult(
    val checkId: String,
    val status: HealthStatus,
    val title: String,
    val detail: String,
    val evidence: String,
    val checkedAtMs: Long,
    val durationMs: Long,
    val canSelfHeal: Boolean = false,
)

/** A tracked problem. [issueId] is stable per (check, first detection) so records stay linkable. */
data class HealthIssue(
    val issueId: String,
    val checkId: String,
    val status: HealthStatus,
    val detail: String,
    val evidence: String,
    val firstDetectedAtMs: Long,
    val lastSeenAtMs: Long,
    val occurrences: Int,
    val resolvedAtMs: Long?,
)

data class HealthReport(
    val reportId: String,
    val trigger: HealthTrigger,
    val checkedAtMs: Long,
    val results: List<HealthCheckResult>,
) {
    fun statusFor(checkId: String): HealthStatus? = results.firstOrNull { it.checkId == checkId }?.status
}

/** Bounded history per check, newest first. */
data class HealthHistoryEntry(val checkedAtMs: Long, val status: HealthStatus, val detail: String)

/** Pure storage contract; implementations live in the data layer. */
interface HealthRepository {
    suspend fun saveReport(report: HealthReport): Boolean
    suspend fun appendHistory(checkId: String, entry: HealthHistoryEntry)
    suspend fun latestReport(): HealthReport?
    suspend fun openIssues(): List<HealthIssue>
    suspend fun allIssues(): List<HealthIssue>
    suspend fun upsertIssue(issue: HealthIssue)
    suspend fun historyFor(checkId: String): List<HealthHistoryEntry>
    suspend fun clear()
}
