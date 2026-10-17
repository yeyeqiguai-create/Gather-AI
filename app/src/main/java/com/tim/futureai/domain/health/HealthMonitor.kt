package com.tim.futureai.domain.health

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Executes registered checks, tracks issue lifecycle (OK→WARNING/FAILED→resolved),
 * persists history, and produces report IDs usable as unified event IDs downstream.
 *
 * Per-check timeout is hard-bounded: a hanging probe becomes UNVERIFIABLE, never blocks the run.
 */
class HealthMonitor(
    private val repository: HealthRepository,
    private val registry: HealthCheckRegistry,
    private val clock: () -> Long,
    private val idSeed: () -> String,
    private val perCheckTimeoutMs: Long = 15_000,
) {
    suspend fun runAll(trigger: HealthTrigger, checkIds: Collection<String>? = null): HealthReport {
        val startedAt = clock()
        val reportId = "hr-$startedAt-${idSeed()}"
        val results = mutableListOf<HealthCheckResult>()
        for (registration in registry.all()) {
            if (checkIds != null && registration.id !in checkIds) continue
            results += runCheck(registration)
        }
        val report = HealthReport(reportId, trigger, startedAt, results)
        val now = clock()
        for (result in results) {
            repository.appendHistory(result.checkId, HealthHistoryEntry(result.checkedAtMs, result.status, result.detail))
            val existing = repository.openIssues().firstOrNull { it.checkId == result.checkId }
            when {
                result.status == HealthStatus.OK -> {
                    if (existing != null && existing.resolvedAtMs == null) {
                        repository.upsertIssue(existing.copy(resolvedAtMs = now, status = HealthStatus.OK))
                    }
                }
                else -> {
                    val issue = if (existing == null || existing.resolvedAtMs != null) {
                        HealthIssue(
                            issueId = "hi-${result.checkId}-$startedAt-${idSeed()}",
                            checkId = result.checkId,
                            status = result.status,
                            detail = result.detail,
                            evidence = result.evidence,
                            firstDetectedAtMs = now,
                            lastSeenAtMs = now,
                            occurrences = 1,
                            resolvedAtMs = null,
                        )
                    } else {
                        existing.copy(
                            status = result.status,
                            detail = result.detail,
                            evidence = result.evidence,
                            lastSeenAtMs = now,
                            occurrences = existing.occurrences + 1,
                        )
                    }
                    repository.upsertIssue(issue)
                }
            }
        }
        repository.saveReport(report)
        return report
    }

    /** Run a single check (used for repair re-checks) and refresh its issue state. */
    suspend fun runCheck(checkId: String, trigger: HealthTrigger = HealthTrigger.EVENT): HealthCheckResult? {
        val registration = registry.find(checkId) ?: return null
        val result = runCheck(registration)
        val now = clock()
        val existing = repository.openIssues().firstOrNull { it.checkId == checkId }
        when {
            result.status == HealthStatus.OK -> {
                if (existing != null && existing.resolvedAtMs == null) {
                    repository.upsertIssue(existing.copy(resolvedAtMs = now, status = HealthStatus.OK))
                }
            }
            else -> {
                val issue = if (existing == null) HealthIssue(
                    issueId = "hi-$checkId-$now-${idSeed()}",
                    checkId = checkId,
                    status = result.status,
                    detail = result.detail,
                    evidence = result.evidence,
                    firstDetectedAtMs = now,
                    lastSeenAtMs = now,
                    occurrences = 1,
                    resolvedAtMs = null,
                ) else existing.copy(
                    status = result.status,
                    detail = result.detail,
                    evidence = result.evidence,
                    lastSeenAtMs = now,
                    occurrences = existing.occurrences + 1,
                )
                repository.upsertIssue(issue)
            }
        }
        repository.saveReport(HealthReport("hr-$now-${idSeed()}", trigger, now, listOf(result)))
        repository.appendHistory(checkId, HealthHistoryEntry(result.checkedAtMs, result.status, result.detail))
        return result
    }

    /** Latest unresolved issue for a check, or null. */
    suspend fun openIssueFor(checkId: String): HealthIssue? =
        repository.openIssues().firstOrNull { it.checkId == checkId && it.resolvedAtMs == null }

    suspend fun allIssues(): List<HealthIssue> = repository.allIssues()

    private suspend fun runCheck(registration: HealthCheckRegistration): HealthCheckResult {
        val startedAt = clock()
        return try {
            withTimeout(perCheckTimeoutMs) { registration.check() }.let { result ->
                if (result.durationMs == 0L) result.copy(durationMs = clock() - startedAt) else result
            }
        } catch (timeout: TimeoutCancellationException) {
            HealthCheckResult(
                checkId = registration.id, status = HealthStatus.UNVERIFIABLE,
                title = registration.title, detail = "检查超时（${perCheckTimeoutMs / 1000} 秒）",
                evidence = "timeout=${perCheckTimeoutMs}ms", checkedAtMs = clock(), durationMs = clock() - startedAt,
            )
        } catch (error: Exception) {
            HealthCheckResult(
                checkId = registration.id, status = HealthStatus.UNVERIFIABLE,
                title = registration.title, detail = "检查执行异常：${error.message ?: error::class.simpleName}",
                evidence = error.message ?: "", checkedAtMs = clock(), durationMs = clock() - startedAt,
            )
        }
    }

}
