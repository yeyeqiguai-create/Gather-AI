package com.tim.futureai.data.usage

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.domain.usage.UsageRecord
import com.tim.futureai.domain.usage.UsageRepository
import com.tim.futureai.domain.usage.UsageSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Private app-file usage ledger, capped to the latest 500 requests. */
class FileUsageRepository(private val file: File) : UsageRepository {
    private val mutex = Mutex()

    override suspend fun load(): UsageSnapshot = withContext(Dispatchers.IO) {
        runCatching {
            val root = JsonMini.parse(file.readText()).asObject()?.entries ?: error("Invalid usage ledger")
            val records = root["records"]?.asArray()?.items.orEmpty().map { item ->
                val fields = item.asObject()?.entries ?: error("Invalid usage entry")
                UsageRecord(
                    profileId = fields["profileId"]?.asString(), providerName = fields.string("providerName"),
                    model = fields.string("model"), durationMs = fields.number("durationMs").toLong(),
                    errorType = fields["errorType"]?.asString(),
                    promptTokens = fields.numberOrNull("promptTokens"),
                    completionTokens = fields.numberOrNull("completionTokens"),
                    recordedAt = fields.string("recordedAt"),
                    sessionId = fields["sessionId"]?.asString(),
                    taskId = fields["taskId"]?.asString(),
                    taskStepId = fields["taskStepId"]?.asString(),
                )
            }
            UsageSnapshot(records)
        }.getOrDefault(UsageSnapshot())
    }

    override suspend fun append(record: UsageRecord) = mutex.withLock { withContext(Dispatchers.IO) {
        val snapshot = load()
        val records = (snapshot.records + record).takeLast(500)
        val json = "{\"records\":" + records.joinToString(",", prefix = "[", postfix = "]") { entry ->
            "{" + listOf(
                "\"profileId\":" + (entry.profileId?.let { JsonMini.quote(it) } ?: "null"),
                "\"providerName\":" + JsonMini.quote(entry.providerName),
                "\"model\":" + JsonMini.quote(entry.model),
                "\"durationMs\":" + entry.durationMs,
                "\"errorType\":" + (entry.errorType?.let { JsonMini.quote(it) } ?: "null"),
                "\"promptTokens\":" + (entry.promptTokens?.toString() ?: "null"),
                "\"completionTokens\":" + (entry.completionTokens?.toString() ?: "null"),
                "\"recordedAt\":" + JsonMini.quote(entry.recordedAt),
                "\"sessionId\":" + (entry.sessionId?.let(JsonMini::quote) ?: "null"),
                "\"taskId\":" + (entry.taskId?.let(JsonMini::quote) ?: "null"),
                "\"taskStepId\":" + (entry.taskStepId?.let(JsonMini::quote) ?: "null"),
            ).joinToString(",") + "}"
        } + "}"
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        val backup = File(file.parentFile, "${file.name}.bak")
        temp.writeText(json)
        if (backup.exists()) backup.delete()
        val hadOld = file.exists()
        if (hadOld && !file.renameTo(backup)) error("Unable to back up usage ledger")
        if (!temp.renameTo(file)) {
            if (hadOld) backup.renameTo(file)
            error("Unable to update usage ledger")
        }
        backup.delete()
        Unit
    } }

    override suspend fun clear() = mutex.withLock { withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        val backup = File(file.parentFile, "${file.name}.bak")
        temp.writeText("{\"records\":[]}")
        if (backup.exists()) backup.delete()
        val hadOld = file.exists()
        if (hadOld && !file.renameTo(backup)) error("Unable to back up usage ledger")
        if (!temp.renameTo(file)) {
            if (hadOld) backup.renameTo(file)
            temp.delete()
            error("Unable to clear usage ledger")
        }
        backup.delete()
        Unit
    } }

    private fun Map<String, JsonValue>.string(key: String): String = this[key]?.asString() ?: error("Missing usage field")
    private fun Map<String, JsonValue>.number(key: String): Double = (this[key] as? JsonValue.JNumber)?.value ?: error("Missing usage number")
    private fun Map<String, JsonValue>.numberOrNull(key: String): Int? = (this[key] as? JsonValue.JNumber)?.value?.toInt()
}
