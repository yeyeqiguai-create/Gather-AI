package com.tim.futureai.data.health

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.domain.health.HealthStatus
import com.tim.futureai.domain.health.RepairOutcome
import com.tim.futureai.domain.health.RepairRecord
import com.tim.futureai.domain.health.RepairRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class FileRepairRepository(private val file: File) : RepairRepository {
    private val mutex = Mutex()

    private fun load(): List<RepairRecord> = runCatching {
        if (!file.isFile) return emptyList()
        (JsonMini.parse(file.readText()).asObject()?.entries?.get("records") as? JsonValue.JArray)
            ?.items?.mapNotNull { it.asObject() }?.map { o ->
                RepairRecord(
                    recordId = o["recordId"]?.asString().orEmpty(),
                    repairId = o["repairId"]?.asString().orEmpty(),
                    issueId = o["issueId"]?.asString().orEmpty(),
                    checkId = o["checkId"]?.asString().orEmpty(),
                    attempt = o["attempt"]?.asString()?.toIntOrNull() ?: 0,
                    startedAtMs = o["startedAtMs"]?.asString()?.toLongOrNull() ?: 0L,
                    finishedAtMs = o["finishedAtMs"]?.asString()?.toLongOrNull() ?: 0L,
                    outcome = enumValueOrNull<RepairOutcome>(o["outcome"]?.asString()) ?: RepairOutcome.FAILED,
                    detail = o["detail"]?.asString().orEmpty(),
                    recheckStatus = o["recheckStatus"]?.asString()?.let { r ->
                        when (r) { "WARNING" -> HealthStatus.WARNING; "FAILED" -> HealthStatus.FAILED; "UNVERIFIABLE" -> HealthStatus.UNVERIFIABLE; else -> HealthStatus.OK }
                    },
                    recheckAtMs = o["recheckAtMs"]?.asString()?.toLongOrNull(),
                )
            }.orEmpty()
    }.getOrDefault(emptyList())

    private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String?): T? =
        raw?.let { r -> enumValues<T>().firstOrNull { it.name == r } }

    override suspend fun saveRecord(record: RepairRecord) = mutex.withLock { withContext(Dispatchers.IO) {
        persist((listOf(record) + load()).take(100))
    } }

    override suspend fun recordsFor(checkId: String): List<RepairRecord> = mutex.withLock { withContext(Dispatchers.IO) {
        load().filter { it.checkId == checkId }
    } }

    override suspend fun allRecords(): List<RepairRecord> = mutex.withLock { withContext(Dispatchers.IO) { load() } }

    private fun persist(records: List<RepairRecord>) {
        val items = records.joinToString(",") { r ->
            """{"recordId":${JsonMini.quote(r.recordId)},"repairId":${JsonMini.quote(r.repairId)},"issueId":${JsonMini.quote(r.issueId)},"checkId":${JsonMini.quote(r.checkId)},"attempt":${JsonMini.quote(r.attempt.toString())},"startedAtMs":${JsonMini.quote(r.startedAtMs.toString())},"finishedAtMs":${JsonMini.quote(r.finishedAtMs.toString())},"outcome":${JsonMini.quote(r.outcome.name)},"detail":${JsonMini.quote(r.detail)},"recheckStatus":${r.recheckStatus?.let { JsonMini.quote(it.name) } ?: "null"},"recheckAtMs":${r.recheckAtMs?.let { JsonMini.quote(it.toString()) } ?: "null"}}"""
        }
        val json = """{"version":1,"records":[${items}]}"""
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) { file.writeText(json); tmp.delete() }
    }
}
