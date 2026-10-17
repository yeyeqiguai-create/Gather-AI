package com.tim.futureai.data.reputation

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.domain.ai.ModelTask
import com.tim.futureai.domain.reputation.ReputationChange
import com.tim.futureai.domain.reputation.ReputationRecord
import com.tim.futureai.domain.reputation.ReputationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class FileReputationRepository(private val file: File) : ReputationRepository {
    private val mutex = Mutex()

    private fun load(): List<ReputationRecord> = runCatching {
        if (!file.isFile) return emptyList()
        (JsonMini.parse(file.readText()).asObject()?.entries?.get("records") as? JsonValue.JArray)
            ?.items?.mapNotNull { it.asObject() }?.map { o ->
                ReputationRecord(
                    modelKey = o["modelKey"]?.asString().orEmpty(),
                    category = enumValueOrNull<ModelTask>(o["category"]?.asString()) ?: ModelTask.GENERAL,
                    success = o["success"]?.asString()?.toIntOrNull() ?: 0,
                    failure = o["failure"]?.asString()?.toIntOrNull() ?: 0,
                    failureTypes = (o["failureTypes"]?.asObject()?.entries ?: emptyMap()).mapValues { it.value.asString()?.toIntOrNull() ?: 0 },
                    totalLatencyMs = o["totalLatencyMs"]?.asString()?.toLongOrNull() ?: 0L,
                    totalCostUsd = o["totalCostUsd"]?.asString()?.toDoubleOrNull() ?: 0.0,
                    updatedAtMs = o["updatedAtMs"]?.asString()?.toLongOrNull() ?: 0L,
                    history = (o["history"] as? JsonValue.JArray)?.items?.mapNotNull { h -> h.asObject() }?.map { h ->
                        ReputationChange(
                            atMs = h["atMs"]?.asString()?.toLongOrNull() ?: 0L,
                            success = (h["success"] as? JsonValue.JBool)?.value ?: false,
                            reason = h["reason"]?.asString().orEmpty(),
                            source = h["source"]?.asString().orEmpty(),
                            detail = h["detail"]?.asString().orEmpty(),
                        )
                    }.orEmpty(),
                )
            }.orEmpty()
    }.getOrDefault(emptyList())

    private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String?): T? =
        raw?.let { r -> enumValues<T>().firstOrNull { it.name == r } }

    override suspend fun record(modelKey: String, category: ModelTask): ReputationRecord? = mutex.withLock { withContext(Dispatchers.IO) {
        load().firstOrNull { it.modelKey == modelKey && it.category == category }
    } }

    override suspend fun upsert(record: ReputationRecord) = mutex.withLock { withContext(Dispatchers.IO) {
        val others = load().filterNot { it.modelKey == record.modelKey && it.category == record.category }
        persist(listOf(record) + others)
    } }

    override suspend fun all(): List<ReputationRecord> = mutex.withLock { withContext(Dispatchers.IO) { load() } }

    override suspend fun clear(modelKey: String?, category: ModelTask?) = mutex.withLock { withContext(Dispatchers.IO) {
        persist(load().filterNot { r ->
            (modelKey == null || r.modelKey == modelKey) && (category == null || r.category == category)
        })
    } }

    private fun persist(records: List<ReputationRecord>) {
        val items = records.joinToString(",") { r ->
            val types = r.failureTypes.entries.joinToString(",") { (k, v) -> "${JsonMini.quote(k)}:${JsonMini.quote(v.toString())}" }
            val history = r.history.joinToString(",") { h ->
                """{"atMs":${JsonMini.quote(h.atMs.toString())},"success":${h.success},"reason":${JsonMini.quote(h.reason)},"source":${JsonMini.quote(h.source)},"detail":${JsonMini.quote(h.detail)}}"""
            }
            """{"modelKey":${JsonMini.quote(r.modelKey)},"category":${JsonMini.quote(r.category.name)},"success":${JsonMini.quote(r.success.toString())},"failure":${JsonMini.quote(r.failure.toString())},"failureTypes":{${types}},"totalLatencyMs":${JsonMini.quote(r.totalLatencyMs.toString())},"totalCostUsd":${JsonMini.quote(r.totalCostUsd.toString())},"updatedAtMs":${JsonMini.quote(r.updatedAtMs.toString())},"history":[${history}]}"""
        }
        val json = """{"version":1,"records":[${items}]}"""
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) { file.writeText(json); tmp.delete() }
    }
}
