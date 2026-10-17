package com.tim.futureai.domain.health

/**
 * A repair for one known, recoverable issue class. Repairs are bounded by
 * [maxAttempts] and [timeoutMs], and always end with a mandatory re-check.
 * No shell execution and no code modification is permitted here by design:
 * repairs may only rebuild connections, resume tracked work, or clear caches.
 */
data class RepairStrategy(
    val checkId: String,
    val repairId: String,
    val title: String,
    val description: String,
    val maxAttempts: Int,
    val timeoutMs: Long,
)

enum class RepairOutcome { SUCCESS, FAILED, STOPPED }

data class RepairRecord(
    val recordId: String,
    val repairId: String,
    val issueId: String,
    val checkId: String,
    val attempt: Int,
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val outcome: RepairOutcome,
    val detail: String,
    val recheckStatus: HealthStatus?,
    val recheckAtMs: Long?,
)

fun interface RepairAction {
    /** Perform exactly one bounded repair attempt. Must not mutate user data without consent. */
    suspend fun perform(issue: HealthIssue): Pair<RepairOutcome, String>
}

interface RepairRepository {
    suspend fun saveRecord(record: RepairRecord)
    suspend fun recordsFor(checkId: String): List<RepairRecord>
    suspend fun allRecords(): List<RepairRecord>
}

/** Emitted so the proactive engine can notify / persist repair failures. */
fun interface RepairEventSink {
    suspend fun onRepairFinished(record: RepairRecord, issue: HealthIssue?)
}

class SelfHealingEngine(
    private val repairRepository: RepairRepository,
    private val actions: Map<String, RepairAction>,
    private val strategies: List<RepairStrategy>,
    private val monitor: HealthMonitor,
    private val eventSink: RepairEventSink,
    private val clock: () -> Long,
    private val idSeed: () -> String,
) {
    fun strategyFor(checkId: String): RepairStrategy? = strategies.firstOrNull {
        checkId == it.checkId || (it.checkId.endsWith(".*") && checkId.startsWith(it.checkId.removeSuffix("*")))
    }

    fun actionsAvailable(): Set<String> = actions.keys

    /**
     * Runs one attempt now and returns the record; repeated calls accumulate
     * attempts up to [RepairStrategy.maxAttempts] — there is no internal loop,
     * so unbounded retries are impossible by construction.
     */
    suspend fun attemptRepair(checkId: String, manual: Boolean): RepairRecord? {
        val strategy = strategyFor(checkId) ?: return null
        val action = actions[strategy.repairId] ?: return null
        val currentIssue = monitor.openIssueFor(checkId)
        val attempt = repairRepository.recordsFor(checkId).count {
            it.repairId == strategy.repairId && it.outcome != RepairOutcome.STOPPED &&
                it.startedAtMs > clock() - ATTEMPT_WINDOW_MS
        } + 1
        if (attempt > strategy.maxAttempts) {
            val record = RepairRecord(
                recordId = "rr-$checkId-$attempt-${clock()}-${idSeed()}", repairId = strategy.repairId,
                issueId = currentIssue?.issueId.orEmpty(), checkId = checkId, attempt = attempt,
                startedAtMs = clock(), finishedAtMs = clock(), outcome = RepairOutcome.STOPPED,
                detail = "已达最大修复次数（${strategy.maxAttempts}）；请手动处理", recheckStatus = null, recheckAtMs = null,
            )
            repairRepository.saveRecord(record)
            eventSink.onRepairFinished(record, currentIssue)
            return record
        }
        val issueForAction = currentIssue ?: HealthIssue(
            issueId = "hi-$checkId-adhoc", checkId = checkId, status = HealthStatus.WARNING,
            detail = "手动触发修复", evidence = "", firstDetectedAtMs = clock(), lastSeenAtMs = clock(),
            occurrences = 1, resolvedAtMs = null,
        )
        val startedAt = clock()
        val (outcome, detail) = try {
            kotlinx.coroutines.withTimeout(strategy.timeoutMs) { action.perform(issueForAction) }
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            RepairOutcome.FAILED to "修复超时（${strategy.timeoutMs / 1000} 秒）"
        } catch (error: Exception) {
            RepairOutcome.FAILED to "修复执行异常：${error.message ?: error::class.simpleName}"
        }
        // Mandatory re-check: a repair is only SUCCESS when the check agrees.
        val recheck = monitor.runCheck(checkId, HealthTrigger.EVENT)
        val finalOutcome = if (outcome == RepairOutcome.SUCCESS && recheck?.status == HealthStatus.OK) {
            RepairOutcome.SUCCESS
        } else if (outcome == RepairOutcome.SUCCESS) {
            RepairOutcome.FAILED // action ran but issue persists → not repaired
        } else outcome
        val record = RepairRecord(
            recordId = "rr-$checkId-$attempt-$startedAt-${idSeed()}", repairId = strategy.repairId,
            issueId = currentIssue?.issueId.orEmpty(), checkId = checkId, attempt = attempt,
            startedAtMs = startedAt, finishedAtMs = clock(), outcome = finalOutcome,
            detail = detail + (recheck?.let { "；复检：${it.status}（${it.detail}）" } ?: "；复检不可用"),
            recheckStatus = recheck?.status, recheckAtMs = recheck?.checkedAtMs,
        )
        repairRepository.saveRecord(record)
        eventSink.onRepairFinished(record, currentIssue)
        return record
    }

    private companion object { const val ATTEMPT_WINDOW_MS = 24 * 60 * 60 * 1000L }
}
