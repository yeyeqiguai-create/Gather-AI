package com.tim.futureai.domain.health

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryHealthRepository : HealthRepository {
    val issues = mutableListOf<HealthIssue>()
    var report: HealthReport? = null
    val history = mutableMapOf<String, MutableList<HealthHistoryEntry>>()

    override suspend fun saveReport(report: HealthReport): Boolean { this.report = report; return true }
    override suspend fun appendHistory(checkId: String, entry: HealthHistoryEntry) {
        history.getOrPut(checkId) { mutableListOf() }.add(0, entry)
    }
    override suspend fun latestReport(): HealthReport? = report
    override suspend fun openIssues(): List<HealthIssue> = issues
    override suspend fun allIssues(): List<HealthIssue> = issues
    override suspend fun upsertIssue(issue: HealthIssue) {
        issues.removeAll { it.issueId == issue.issueId }
        issues.add(issue)
        // single unresolved issue per check
        val same = issues.filter { it.checkId == issue.checkId && it.resolvedAtMs == null && it.issueId != issue.issueId }
        issues.removeAll(same.toSet())
    }
    override suspend fun historyFor(checkId: String): List<HealthHistoryEntry> = history[checkId].orEmpty()
    override suspend fun clear() { issues.clear(); history.clear(); report = null }
}

class HealthMonitorTest {
    private var now = 1_000L
    private var seed = 0
    private fun repository() = MemoryHealthRepository()

    @Test fun issueLifecycleDetectsEscalatesAndResolves() = runBlocking {
        val repo = repository()
        var failing = true
        val registry = HealthCheckRegistry()
        registry.register(HealthCheckRegistration("c1", "检查一", "cat", selfHealable = false) {
            HealthCheckResult("c1", if (failing) HealthStatus.FAILED else HealthStatus.OK, "检查一", if (failing) "失败详情" else "已恢复", "e=1", now, 1)
        })
        val monitor = HealthMonitor(repo, registry, clock = { now }, idSeed = { "${seed++}" })
        monitor.runAll(HealthTrigger.MANUAL)
        val open = repo.openIssues().single()
        assertEquals(HealthStatus.FAILED, open.status)
        assertEquals(1, open.occurrences)
        // second failing run: same issue, occurrences increase
        now += 10
        monitor.runAll(HealthTrigger.MANUAL)
        val escalated = repo.openIssues().single()
        assertEquals(2, escalated.occurrences)
        assertEquals(open.issueId, escalated.issueId)
        // recovery resolves the issue
        now += 10; failing = false
        monitor.runAll(HealthTrigger.MANUAL)
        val resolved = repo.issues.single()
        assertNotNull(resolved.resolvedAtMs)
        assertTrue(repo.historyFor("c1").size >= 3)
    }

    @Test fun hangingCheckBecomesUnverifiableInsteadOfBlocking() = runBlocking {
        val repo = repository()
        val registry = HealthCheckRegistry()
        registry.register(HealthCheckRegistration("slow", "慢检查", "cat", selfHealable = false) {
            kotlinx.coroutines.delay(5_000)
            HealthCheckResult("slow", HealthStatus.OK, "慢检查", "不应到达", "", now, 0)
        })
        val monitor = HealthMonitor(repo, registry, clock = { now }, idSeed = { "${seed++}" }, perCheckTimeoutMs = 50)
        val report = monitor.runAll(HealthTrigger.MANUAL)
        assertEquals(HealthStatus.UNVERIFIABLE, report.results.single().status)
    }
}

class SelfHealingTest {
    private var now = 1_000L
    private var seed = 0

    @Test fun repairRequiresPositiveRecheckAndStopsAtMaxAttempts() = runBlocking {
        val repo = MemoryHealthRepository()
        val issues = mutableListOf<HealthIssue>()
        val healthRepo = object : HealthRepository by repo {
            override suspend fun openIssues(): List<HealthIssue> = issues
            override suspend fun allIssues(): List<HealthIssue> = issues
            override suspend fun upsertIssue(issue: HealthIssue) {
                issues.removeAll { it.issueId == issue.issueId }; issues.add(issue)
            }
        }
        val registry = HealthCheckRegistry()
        var checkFailing = true
        registry.register(HealthCheckRegistration("c1", "检查一", "cat", selfHealable = true) {
            HealthCheckResult("c1", if (checkFailing) HealthStatus.FAILED else HealthStatus.OK, "检查一", "d", "e", now, 1)
        })
        val monitor = HealthMonitor(healthRepo, registry, clock = { now }, idSeed = { "${seed++}" })
        monitor.runAll(HealthTrigger.MANUAL)
        var attempts = 0
        val engine = SelfHealingEngine(
            repairRepository = MemoryRepairRepository(),
            actions = mapOf("r1" to RepairAction { _ ->
                attempts++; RepairOutcome.SUCCESS to "动作完成"
            }),
            strategies = listOf(RepairStrategy("c1", "r1", "修复一", "d", maxAttempts = 2, timeoutMs = 1_000)),
            monitor = monitor,
            eventSink = RepairEventSink { _, _ -> },
            clock = { now }, idSeed = { "${seed++}" },
        )
        // attempt 1: action succeeds but check still failing → repair FAILED
        val r1 = engine.attemptRepair("c1", manual = true)!!
        assertEquals(RepairOutcome.FAILED, r1.outcome)
        assertEquals(1, attempts)
        // attempt 2: action succeeds AND check now passes → SUCCESS with recheck
        checkFailing = false
        val r2 = engine.attemptRepair("c1", manual = true)!!
        assertEquals(RepairOutcome.SUCCESS, r2.outcome)
        assertEquals(HealthStatus.OK, r2.recheckStatus)
        // attempt 3: stopped by maxAttempts — no unbounded retry
        val r3 = engine.attemptRepair("c1", manual = true)!!
        assertEquals(RepairOutcome.STOPPED, r3.outcome)
        assertEquals(2, attempts)
    }

    @Test fun failingRepairEmitsReadableReport() = runBlocking {
        val repo = MemoryHealthRepository()
        val healthRepo = object : HealthRepository by repo {
            override suspend fun openIssues() = listOf(HealthIssue("hi", "c1", HealthStatus.FAILED, "d", "e", 1L, 1L, 1, null))
        }
        val registry = HealthCheckRegistry()
        registry.register(HealthCheckRegistration("c1", "检查一", "cat", selfHealable = true) {
            HealthCheckResult("c1", HealthStatus.FAILED, "检查一", "d", "e", now, 1)
        })
        val monitor = HealthMonitor(healthRepo, registry, clock = { now }, idSeed = { "${seed++}" })
        val reports = mutableListOf<RepairRecord>()
        val engine = SelfHealingEngine(
            MemoryRepairRepository(),
            mapOf("r1" to RepairAction { _ -> RepairOutcome.FAILED to "服务商仍不可达" }),
            listOf(RepairStrategy("c1", "r1", "重建连接", "d", 1, 1_000)),
            monitor, eventSink = RepairEventSink { record, _ -> reports.add(record) }, clock = { now }, idSeed = { "${seed++}" },
        )
        engine.attemptRepair("c1", manual = false)
        assertEquals(1, reports.size)
        assertTrue(reports.single().detail.contains("服务商仍不可达"))
        assertEquals(RepairOutcome.FAILED, reports.single().outcome)
    }
}

class MemoryRepairRepository : RepairRepository {
    val records = mutableListOf<RepairRecord>()
    override suspend fun saveRecord(record: RepairRecord) { records.removeAll { it.recordId == record.recordId }; records.add(record) }
    override suspend fun recordsFor(checkId: String): List<RepairRecord> = records.filter { it.checkId == checkId }
    override suspend fun allRecords(): List<RepairRecord> = records
}
