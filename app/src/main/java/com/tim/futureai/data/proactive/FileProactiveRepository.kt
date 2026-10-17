package com.tim.futureai.data.proactive

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.domain.proactive.ProactiveEvent
import com.tim.futureai.domain.proactive.ProactiveEventType
import com.tim.futureai.domain.proactive.ProactiveRepository
import com.tim.futureai.domain.proactive.ProactiveStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class FileProactiveRepository(private val file: File) : ProactiveRepository {
    private val mutex = Mutex()

    private fun load(): List<ProactiveEvent> = runCatching {
        if (!file.isFile) return emptyList()
        (JsonMini.parse(file.readText()).asObject()?.entries?.get("events") as? JsonValue.JArray)
            ?.items?.mapNotNull { it.asObject() }?.map { o ->
                ProactiveEvent(
                    eventId = o["eventId"]?.asString().orEmpty(),
                    type = enumValueOrNull<ProactiveEventType>(o["type"]?.asString()) ?: ProactiveEventType.HEALTH_ISSUE,
                    dedupKey = o["dedupKey"]?.asString().orEmpty(),
                    title = o["title"]?.asString().orEmpty(),
                    detail = o["detail"]?.asString().orEmpty(),
                    severity = o["severity"]?.asString()?.toIntOrNull() ?: 1,
                    createdAtMs = o["createdAtMs"]?.asString()?.toLongOrNull() ?: 0L,
                    lastSeenAtMs = o["lastSeenAtMs"]?.asString()?.toLongOrNull() ?: 0L,
                    occurrences = o["occurrences"]?.asString()?.toIntOrNull() ?: 1,
                    status = enumValueOrNull<ProactiveStatus>(o["status"]?.asString()) ?: ProactiveStatus.PENDING,
                    relatedKind = o["relatedKind"]?.asString().orEmpty(),
                    relatedId = o["relatedId"]?.asString().orEmpty(),
                    snoozedUntilMs = o["snoozedUntilMs"]?.asString()?.toLongOrNull(),
                    notifiedAtMs = o["notifiedAtMs"]?.asString()?.toLongOrNull(),
                    notificationDegraded = (o["notificationDegraded"] as? JsonValue.JBool)?.value ?: false,
                )
            }.orEmpty()
    }.getOrDefault(emptyList())

    private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String?): T? =
        raw?.let { r -> enumValues<T>().firstOrNull { it.name == r } }

    override suspend fun save(event: ProactiveEvent) = mutex.withLock { withContext(Dispatchers.IO) {
        val others = load().filterNot { it.eventId == event.eventId }
        persist(listOf(event) + others)
    } }

    override suspend fun byId(eventId: String): ProactiveEvent? = mutex.withLock { withContext(Dispatchers.IO) { load().firstOrNull { it.eventId == eventId } } }

    override suspend fun all(): List<ProactiveEvent> = mutex.withLock { withContext(Dispatchers.IO) { load() } }

    override suspend fun unresolvedByDedupKey(dedupKey: String): ProactiveEvent? = mutex.withLock { withContext(Dispatchers.IO) {
        load().firstOrNull { it.dedupKey == dedupKey && it.status != ProactiveStatus.RESOLVED && it.status != ProactiveStatus.MUTED }
    } }

    private fun persist(events: List<ProactiveEvent>) {
        val items = events.take(200).joinToString(",") { e ->
            """{"eventId":${JsonMini.quote(e.eventId)},"type":${JsonMini.quote(e.type.name)},"dedupKey":${JsonMini.quote(e.dedupKey)},"title":${JsonMini.quote(e.title)},"detail":${JsonMini.quote(e.detail)},"severity":${JsonMini.quote(e.severity.toString())},"createdAtMs":${JsonMini.quote(e.createdAtMs.toString())},"lastSeenAtMs":${JsonMini.quote(e.lastSeenAtMs.toString())},"occurrences":${JsonMini.quote(e.occurrences.toString())},"status":${JsonMini.quote(e.status.name)},"relatedKind":${JsonMini.quote(e.relatedKind)},"relatedId":${JsonMini.quote(e.relatedId)},"snoozedUntilMs":${e.snoozedUntilMs?.let { JsonMini.quote(it.toString()) } ?: "null"},"notifiedAtMs":${e.notifiedAtMs?.let { JsonMini.quote(it.toString()) } ?: "null"},"notificationDegraded":${e.notificationDegraded}}"""
        }
        val json = """{"version":1,"events":[${items}]}"""
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) { file.writeText(json); tmp.delete() }
    }
}
