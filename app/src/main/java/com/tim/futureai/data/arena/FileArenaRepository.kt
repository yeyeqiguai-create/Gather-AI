package com.tim.futureai.data.arena

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.domain.arena.ArenaConclusion
import com.tim.futureai.domain.arena.ArenaEntry
import com.tim.futureai.domain.arena.ArenaRepository
import com.tim.futureai.domain.arena.ArenaReview
import com.tim.futureai.domain.arena.ArenaSession
import com.tim.futureai.domain.arena.ArenaStatus
import com.tim.futureai.domain.arena.ArenaVerification
import com.tim.futureai.domain.arena.ArenaVerificationMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Stores completed arena sessions (bounded to the most recent 30). */
class FileArenaRepository(private val file: File) : ArenaRepository {
    private val mutex = Mutex()

    private fun load(): List<ArenaSession> = runCatching {
        if (!file.isFile) return emptyList()
        (JsonMini.parse(file.readText()).asObject()?.entries?.get("sessions") as? JsonValue.JArray)
            ?.items?.mapNotNull { it.asObject() }?.map { o -> session(o.entries) }.orEmpty()
    }.getOrDefault(emptyList())

    private fun session(o: Map<String, JsonValue>) = ArenaSession(
        sessionId = o["sessionId"]?.asString().orEmpty(),
        prompt = o["prompt"]?.asString().orEmpty(),
        category = o["category"]?.asString().orEmpty(),
        createdAtMs = o["createdAtMs"]?.asString()?.toLongOrNull() ?: 0L,
        status = enumValueOrNull<ArenaStatus>(o["status"]?.asString()) ?: ArenaStatus.DONE,
        entries = (o["entries"] as? JsonValue.JArray)?.items?.mapNotNull { e -> e.asObject() }?.map { e ->
            ArenaEntry(
                modelKey = e["modelKey"]?.asString().orEmpty(), profileId = e["profileId"]?.asString().orEmpty(),
                answer = e["answer"]?.asString(), latencyMs = e["latencyMs"]?.asString()?.toLongOrNull() ?: 0L,
                promptTokens = e["promptTokens"]?.asString()?.toIntOrNull(),
                completionTokens = e["completionTokens"]?.asString()?.toIntOrNull(),
                costUsd = e["costUsd"]?.asString()?.toDoubleOrNull(), error = e["error"]?.asString(),
            )
        }.orEmpty(),
        review = o["review"]?.asObject()?.let { r ->
            ArenaReview(
                reviewerModelKey = r["reviewer"]?.asString().orEmpty(),
                agreements = strList(r["agreements"]), conflicts = strList(r["conflicts"]),
                notes = r["notes"]?.asString().orEmpty(), latencyMs = r["latencyMs"]?.asString()?.toLongOrNull() ?: 0L,
            )
        },
        verification = o["verification"]?.asObject()?.let { v ->
            ArenaVerification(
                method = enumValueOrNull<ArenaVerificationMethod>(v["method"]?.asString()) ?: ArenaVerificationMethod.NONE,
                passed = when ((v["passed"] as? JsonValue.JBool)?.value) { true -> true; false -> false; else -> null },
                detail = v["detail"]?.asString().orEmpty(),
                perEntry = (v["perEntry"]?.asObject()?.entries ?: emptyMap()).mapValues { (it.value as? JsonValue.JBool)?.value ?: false },
            )
        },
        conclusion = o["conclusion"]?.asObject()?.let { c ->
            ArenaConclusion(c["recommended"]?.asString(), c["confidence"]?.asString().orEmpty(), c["basis"]?.asString().orEmpty())
        },
    )

    private fun strList(v: JsonValue?): List<String> = (v as? JsonValue.JArray)?.items?.mapNotNull { it.asString() }.orEmpty()

    private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String?): T? =
        raw?.let { r -> enumValues<T>().firstOrNull { it.name == r } }

    override suspend fun save(session: ArenaSession) = mutex.withLock { withContext(Dispatchers.IO) {
        val others = load().filterNot { it.sessionId == session.sessionId }
        persist((listOf(session) + others).take(30))
    } }

    override suspend fun byId(sessionId: String): ArenaSession? = mutex.withLock { withContext(Dispatchers.IO) { load().firstOrNull { it.sessionId == sessionId } } }

    override suspend fun all(): List<ArenaSession> = mutex.withLock { withContext(Dispatchers.IO) { load() } }

    private fun persist(sessions: List<ArenaSession>) {
        val items = sessions.joinToString(",") { s ->
            val entries = s.entries.joinToString(",") { e ->
                """{"modelKey":${JsonMini.quote(e.modelKey)},"profileId":${JsonMini.quote(e.profileId)},"answer":${e.answer?.let { JsonMini.quote(it) } ?: "null"},"latencyMs":${JsonMini.quote(e.latencyMs.toString())},"promptTokens":${e.promptTokens?.let { JsonMini.quote(it.toString()) } ?: "null"},"completionTokens":${e.completionTokens?.let { JsonMini.quote(it.toString()) } ?: "null"},"costUsd":${e.costUsd?.let { JsonMini.quote(it.toString()) } ?: "null"},"error":${e.error?.let { JsonMini.quote(it) } ?: "null"}}"""
            }
            val review = s.review?.let { r ->
                """{"reviewer":${JsonMini.quote(r.reviewerModelKey)},"agreements":${arr(r.agreements)},"conflicts":${arr(r.conflicts)},"notes":${JsonMini.quote(r.notes)},"latencyMs":${JsonMini.quote(r.latencyMs.toString())}}"""
            } ?: "null"
            val verification = s.verification?.let { v ->
                val per = v.perEntry.entries.joinToString(",") { (k, b) -> "${JsonMini.quote(k)}:$b" }
                """{"method":${JsonMini.quote(v.method.name)},"passed":${v.passed?.toString() ?: "null"},"detail":${JsonMini.quote(v.detail)},"perEntry":{${per}}}"""
            } ?: "null"
            val conclusion = s.conclusion?.let { c ->
                """{"recommended":${c.recommendedModelKey?.let { JsonMini.quote(it) } ?: "null"},"confidence":${JsonMini.quote(c.confidence)},"basis":${JsonMini.quote(c.basis)}}"""
            } ?: "null"
            """{"sessionId":${JsonMini.quote(s.sessionId)},"prompt":${JsonMini.quote(s.prompt)},"category":${JsonMini.quote(s.category)},"createdAtMs":${JsonMini.quote(s.createdAtMs.toString())},"status":${JsonMini.quote(s.status.name)},"entries":[${entries}],"review":$review,"verification":$verification,"conclusion":$conclusion}"""
        }
        val json = """{"version":1,"sessions":[${items}]}"""
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) { file.writeText(json); tmp.delete() }
    }

    private fun arr(values: List<String>) = values.joinToString(",", "[", "]") { JsonMini.quote(it) }
}
