package com.tim.futureai.data.health

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.domain.health.HealthCheckResult
import com.tim.futureai.domain.health.HealthHistoryEntry
import com.tim.futureai.domain.health.HealthIssue
import com.tim.futureai.domain.health.HealthReport
import com.tim.futureai.domain.health.HealthRepository
import com.tim.futureai.domain.health.HealthStatus
import com.tim.futureai.domain.health.HealthTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Versioned JSON snapshot with temp+rename commit; keeps report, issues and per-check history. */
class FileHealthRepository(private val file: File) : HealthRepository {
    private val mutex = Mutex()

    private data class Snapshot(
        val report: HealthReport?,
        val issues: List<HealthIssue>,
        val history: Map<String, List<HealthHistoryEntry>>,
    )

    private var cache: Snapshot = Snapshot(null, emptyList(), emptyMap())

    private fun load(): Snapshot = runCatching {
        if (!file.isFile) return Snapshot(null, emptyList(), emptyMap())
        val root = JsonMini.parse(file.readText()).asObject() ?: return Snapshot(null, emptyList(), emptyMap())
        val report = root["report"]?.asObject()?.let { r ->
            val results = (r["results"] as? JsonValue.JArray)?.items?.mapNotNull { it.asObject() }?.map { o ->
                HealthCheckResult(
                    checkId = o["checkId"]?.asString().orEmpty(),
                    status = status(o["status"]?.asString()),
                    title = o["title"]?.asString().orEmpty(),
                    detail = o["detail"]?.asString().orEmpty(),
                    evidence = o["evidence"]?.asString().orEmpty(),
                    checkedAtMs = o["checkedAtMs"]?.asString()?.toLongOrNull() ?: 0L,
                    durationMs = o["durationMs"]?.asString()?.toLongOrNull() ?: 0L,
                    canSelfHeal = (o["canSelfHeal"] as? JsonValue.JBool)?.value ?: false,
                )
            }.orEmpty()
            HealthReport(
                reportId = r["reportId"]?.asString().orEmpty(),
                trigger = when (r["trigger"]?.asString()) { "PERIODIC" -> HealthTrigger.PERIODIC; "EVENT" -> HealthTrigger.EVENT; "STARTUP" -> HealthTrigger.STARTUP; else -> HealthTrigger.MANUAL },
                checkedAtMs = r["checkedAtMs"]?.asString()?.toLongOrNull() ?: 0L,
                results = results,
            )
        }
        val issues = (root["issues"] as? JsonValue.JArray)?.items?.mapNotNull { it.asObject() }?.map { o ->
            HealthIssue(
                issueId = o["issueId"]?.asString().orEmpty(), checkId = o["checkId"]?.asString().orEmpty(),
                status = status(o["status"]?.asString()), detail = o["detail"]?.asString().orEmpty(),
                evidence = o["evidence"]?.asString().orEmpty(),
                firstDetectedAtMs = o["firstDetectedAtMs"]?.asString()?.toLongOrNull() ?: 0L,
                lastSeenAtMs = o["lastSeenAtMs"]?.asString()?.toLongOrNull() ?: 0L,
                occurrences = o["occurrences"]?.asString()?.toIntOrNull() ?: 0,
                resolvedAtMs = o["resolvedAtMs"]?.asString()?.toLongOrNull(),
            )
        }.orEmpty()
        val history = (root["history"] as? JsonValue.JObject)?.entries?.mapValues { (_, v) ->
            (v as? JsonValue.JArray)?.items?.mapNotNull { it.asObject() }?.map { o ->
                HealthHistoryEntry(
                    checkedAtMs = o["at"]?.asString()?.toLongOrNull() ?: 0L,
                    status = status(o["status"]?.asString()),
                    detail = o["detail"]?.asString().orEmpty(),
                )
            }.orEmpty()
        } ?: emptyMap()
        Snapshot(report, issues, history)
    }.getOrDefault(Snapshot(null, emptyList(), emptyMap()))

    private fun status(raw: String?): HealthStatus = when (raw) { "WARNING" -> HealthStatus.WARNING; "FAILED" -> HealthStatus.FAILED; "UNVERIFIABLE" -> HealthStatus.UNVERIFIABLE; else -> HealthStatus.OK }

    private fun persist(snapshot: Snapshot) {
        val issuesJson = snapshot.issues.joinToString(",") { i ->
            """{"issueId":${JsonMini.quote(i.issueId)},"checkId":${JsonMini.quote(i.checkId)},"status":${JsonMini.quote(i.status.name)},"detail":${JsonMini.quote(i.detail)},"evidence":${JsonMini.quote(i.evidence)},"firstDetectedAtMs":${JsonMini.quote(i.firstDetectedAtMs.toString())},"lastSeenAtMs":${JsonMini.quote(i.lastSeenAtMs.toString())},"occurrences":${JsonMini.quote(i.occurrences.toString())},"resolvedAtMs":${i.resolvedAtMs?.let { JsonMini.quote(it.toString()) } ?: "null"}}"""
        }
        val resultsJson = snapshot.report?.results?.joinToString(",") { r ->
            """{"checkId":${JsonMini.quote(r.checkId)},"status":${JsonMini.quote(r.status.name)},"title":${JsonMini.quote(r.title)},"detail":${JsonMini.quote(r.detail)},"evidence":${JsonMini.quote(r.evidence)},"checkedAtMs":${JsonMini.quote(r.checkedAtMs.toString())},"durationMs":${JsonMini.quote(r.durationMs.toString())},"canSelfHeal":${r.canSelfHeal}}"""
        }
        val reportJson = snapshot.report?.let { r ->
            """{"reportId":${JsonMini.quote(r.reportId)},"trigger":${JsonMini.quote(r.trigger.name)},"checkedAtMs":${JsonMini.quote(r.checkedAtMs.toString())},"results":[$resultsJson]}"""
        } ?: "null"
        val historyJson = snapshot.history.entries.joinToString(",") { (checkId, entries) ->
            val items = entries.joinToString(",") { e -> """{"at":${JsonMini.quote(e.checkedAtMs.toString())},"status":${JsonMini.quote(e.status.name)},"detail":${JsonMini.quote(e.detail)}}""" }
            "${JsonMini.quote(checkId)}:[${items}]"
        }
        val json = """{"version":1,"report":$reportJson,"issues":[$issuesJson],"history":{${historyJson}}}"""
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            file.writeText(json); tmp.delete()
        }
    }

    override suspend fun saveReport(report: HealthReport): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val snapshot = Snapshot(report, load().issues, load().history)
            cache = snapshot; persist(snapshot)
            true
        }
    }

    override suspend fun appendHistory(checkId: String, entry: HealthHistoryEntry) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = load()
            val updated = current.history.toMutableMap()
            updated[checkId] = (listOf(entry) + updated[checkId].orEmpty()).take(50)
            val snapshot = Snapshot(current.report, current.issues, updated)
            cache = snapshot; persist(snapshot)
        }
    }

    override suspend fun latestReport(): HealthReport? = mutex.withLock { withContext(Dispatchers.IO) { load().report.also { cache = load() } } }

    override suspend fun openIssues(): List<HealthIssue> = mutex.withLock { withContext(Dispatchers.IO) { load().issues } }

    override suspend fun allIssues(): List<HealthIssue> = mutex.withLock { withContext(Dispatchers.IO) { load().issues } }

    override suspend fun upsertIssue(issue: HealthIssue) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = load()
            val others = current.issues.filterNot { it.issueId == issue.issueId }
            val sameCheckUnresolved = others.filter { it.checkId == issue.checkId && it.resolvedAtMs == null && it.issueId != issue.issueId }
            val merged = (listOf(issue) + others.map { o -> if (o.checkId == issue.checkId && o.resolvedAtMs == null && issue.resolvedAtMs == null) o.copy(resolvedAtMs = issue.lastSeenAtMs) else o }) - sameCheckUnresolved.toSet()
            val snapshot = Snapshot(current.report, merged.distinctBy { it.issueId }, current.history)
            cache = snapshot; persist(snapshot)
        }
    }

    override suspend fun historyFor(checkId: String): List<HealthHistoryEntry> = mutex.withLock {
        withContext(Dispatchers.IO) { load().history[checkId].orEmpty() }
    }

    override suspend fun clear() = mutex.withLock { withContext(Dispatchers.IO) { cache = Snapshot(null, emptyList(), emptyMap()); persist(cache) } }
}
