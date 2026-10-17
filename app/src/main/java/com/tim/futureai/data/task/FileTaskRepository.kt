package com.tim.futureai.data.task

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.domain.task.EvidenceReference
import com.tim.futureai.domain.task.Task
import com.tim.futureai.domain.task.TaskAggregate
import com.tim.futureai.domain.task.TaskCheckpoint
import com.tim.futureai.domain.task.TaskConclusion
import com.tim.futureai.domain.task.TaskEvent
import com.tim.futureai.domain.task.TaskEventType
import com.tim.futureai.domain.task.TaskExecutionStrategy
import com.tim.futureai.domain.task.TaskRepository
import com.tim.futureai.domain.task.TaskResult
import com.tim.futureai.domain.task.TaskStatus
import com.tim.futureai.domain.task.TaskStep
import com.tim.futureai.domain.task.TaskStepResult
import com.tim.futureai.domain.task.TaskStepStatus
import com.tim.futureai.domain.task.ToolInvocation
import com.tim.futureai.domain.task.ToolInvocationStatus
import com.tim.futureai.domain.task.VerificationResult
import com.tim.futureai.domain.task.VerificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Stores each aggregate as one versioned JSON snapshot and commits it through temp+backup rename. */
class FileTaskRepository(private val file: File) : TaskRepository {
    private val mutex = Mutex()
    private val mutableTasks = MutableStateFlow<List<TaskAggregate>>(emptyList())
    private var durableTasks: List<TaskAggregate> = emptyList()
    private val ephemeralTasks = linkedMapOf<String, TaskAggregate>()
    override val tasks: StateFlow<List<TaskAggregate>> = mutableTasks.asStateFlow()

    override suspend fun load(): List<TaskAggregate> = mutex.withLock { withContext(Dispatchers.IO) {
        val backup = File(file.parentFile, "${file.name}.bak")
        val primary = file.takeIf(File::isFile)?.let { runCatching { TaskCodec.decode(it.readText()) }.getOrNull() }
        val recovered = if (primary == null && backup.isFile) {
            runCatching { TaskCodec.decode(backup.readText()) }.getOrNull()?.also { restorePrimaryFromBackup(backup) }
        } else null
        val loaded = primary ?: recovered.orEmpty()
        durableTasks = loaded.sortedByDescending { it.task.createdAtMs }
        mutableTasks.value = (durableTasks + ephemeralTasks.values).sortedByDescending { it.task.createdAtMs }
        mutableTasks.value
    } }

    /** Keep the known-good backup while repairing an invalid/missing primary snapshot. */
    private fun restorePrimaryFromBackup(backup: File) {
        val parent = file.parentFile
        parent?.mkdirs()
        val temp = File(parent, "${file.name}.restore.tmp")
        FileOutputStream(temp).use { output ->
            backup.inputStream().use { input -> input.copyTo(output) }
            output.fd.sync()
        }
        val corrupt = if (file.exists()) File(parent, "${file.name}.corrupt-${System.currentTimeMillis()}") else null
        if (corrupt != null && !file.renameTo(corrupt)) {
            temp.delete()
            error("Unable to preserve invalid task snapshot before recovery")
        }
        if (!temp.renameTo(file)) {
            if (corrupt != null) corrupt.renameTo(file)
            temp.delete()
            error("Unable to restore task snapshot from backup")
        }
    }

    override suspend fun save(aggregate: TaskAggregate) = mutex.withLock { withContext(Dispatchers.IO) {
        ephemeralTasks.remove(aggregate.task.id)
        val next = (durableTasks.filterNot { it.task.id == aggregate.task.id } + aggregate)
            .sortedByDescending { it.task.createdAtMs }
        writeAtomically(next)
        durableTasks = next
        mutableTasks.value = (durableTasks + ephemeralTasks.values).sortedByDescending { it.task.createdAtMs }
    } }

    override suspend fun delete(taskId: String) = mutex.withLock { withContext(Dispatchers.IO) {
        durableTasks = durableTasks.filterNot { it.task.id == taskId }
        ephemeralTasks.remove(taskId)
        writeAtomically(durableTasks)
        mutableTasks.value = (durableTasks + ephemeralTasks.values).sortedByDescending { it.task.createdAtMs }
    } }

    override suspend fun forget(taskId: String) = mutex.withLock { withContext(Dispatchers.IO) {
        val current = mutableTasks.value.firstOrNull { it.task.id == taskId } ?: return@withContext
        durableTasks = durableTasks.filterNot { it.task.id == taskId }
        ephemeralTasks[taskId] = current
        writeAtomically(durableTasks)
        mutableTasks.value = (durableTasks + ephemeralTasks.values).sortedByDescending { it.task.createdAtMs }
    } }

    override suspend fun clearFinished(): Int = mutex.withLock { withContext(Dispatchers.IO) {
        durableTasks = durableTasks.filterNot { it.task.status in setOf(TaskStatus.SUCCEEDED, TaskStatus.FAILED, TaskStatus.CANCELLED) }
        ephemeralTasks.entries.removeAll { (_, task) -> task.task.status in setOf(TaskStatus.SUCCEEDED, TaskStatus.FAILED, TaskStatus.CANCELLED) }
        val next = (durableTasks + ephemeralTasks.values).sortedByDescending { it.task.createdAtMs }
        val removed = mutableTasks.value.size - next.size
        writeAtomically(durableTasks)
        mutableTasks.value = next
        removed
    } }

    private fun writeAtomically(next: List<TaskAggregate>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        val backup = File(file.parentFile, "${file.name}.bak")
        FileOutputStream(temp).use { output ->
            output.write(TaskCodec.encode(next).toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (backup.exists() && !backup.delete()) error("Unable to rotate task snapshot backup")
        val hadOld = file.exists()
        if (hadOld && !file.renameTo(backup)) error("Unable to back up task snapshot")
        if (!temp.renameTo(file)) {
            if (hadOld && !backup.renameTo(file)) error("Task snapshot update failed and backup restore failed")
            error("Unable to commit task snapshot")
        }
        // Keep the previous committed snapshot as a recovery point until the next write.
    }
}

private object TaskCodec {
    fun encode(aggregates: List<TaskAggregate>): String = "{\"schemaVersion\":1,\"tasks\":[${aggregates.joinToString(",") { aggregate(it) }}]}"

    fun decode(json: String): List<TaskAggregate> {
        val root = JsonMini.parse(json).asObject()?.entries ?: error("Invalid task repository")
        val version = (root["schemaVersion"] as? JsonValue.JNumber)?.value?.toInt()
        require(version == 1) { "Unsupported task repository schema" }
        return root["tasks"]?.asArray()?.items.orEmpty().map { decodeAggregate(it) }
    }

    private fun aggregate(a: TaskAggregate): String = "{" + listOf(
        "\"task\":" + task(a.task),
        "\"steps\":" + array(a.steps, ::step),
        "\"invocations\":" + array(a.invocations, ::invocation),
        "\"checkpoints\":" + array(a.checkpoints, ::checkpoint),
        "\"events\":" + array(a.events, ::event),
        "\"evidence\":" + array(a.evidence, ::evidence),
    ).joinToString(",") + "}"

    private fun decodeAggregate(value: JsonValue): TaskAggregate {
        val f = value.asObject()?.entries ?: error("Invalid task aggregate")
        return TaskAggregate(
            task = decodeTask(f.requiredObject("task")),
            steps = f.objects("steps").map(::decodeStep),
            invocations = f.objects("invocations").map(::decodeInvocation),
            checkpoints = f.objects("checkpoints").map(::decodeCheckpoint),
            events = f.objects("events").map(::decodeEvent),
            evidence = f.objects("evidence").map(::decodeEvidence),
        )
    }

    private fun task(t: Task) = "{" + listOf(
        "\"id\":" + q(t.id), "\"goal\":" + q(t.goal), "\"createdAtMs\":" + t.createdAtMs,
        "\"status\":" + q(t.status.name), "\"strategy\":" + q(t.strategy.name),
        "\"result\":" + (t.result?.let(::result) ?: "null"), "\"plannerModel\":" + nullable(t.plannerModel),
        "\"plannerProfileId\":" + nullable(t.plannerProfileId), "\"planVersion\":" + nullable(t.planVersion),
        "\"plannerPromptTokens\":" + (t.plannerPromptTokens?.toString() ?: "null"),
        "\"plannerCompletionTokens\":" + (t.plannerCompletionTokens?.toString() ?: "null"),
        "\"plannerDurationMs\":" + (t.plannerDurationMs?.toString() ?: "null"),
        "\"estimatedCostUsd\":" + (t.estimatedCostUsd?.takeIf(Double::isFinite)?.toString() ?: "null"),
    ).joinToString(",") + "}"

    private fun decodeTask(f: Map<String, JsonValue>) = Task(
        id = f.string("id"), goal = f.string("goal"), createdAtMs = f.long("createdAtMs"),
        status = f.enum("status", TaskStatus.CREATED),
        strategy = f.enum("strategy", TaskExecutionStrategy.DEPENDENCY_ORDERED),
        result = f["result"]?.takeUnless { it == JsonValue.JNull }?.asObject()?.entries?.let(::decodeResult),
        plannerModel = f.stringOrNull("plannerModel"), plannerProfileId = f.stringOrNull("plannerProfileId"),
        planVersion = f.stringOrNull("planVersion"), plannerPromptTokens = f.intOrNull("plannerPromptTokens"),
        plannerCompletionTokens = f.intOrNull("plannerCompletionTokens"), plannerDurationMs = f.longOrNull("plannerDurationMs"),
        estimatedCostUsd = f.doubleOrNull("estimatedCostUsd"),
    )

    private fun result(r: TaskResult) = "{\"conclusion\":${q(r.conclusion.name)},\"summary\":${q(r.summary)},\"finishedAtMs\":${r.finishedAtMs}}"
    private fun decodeResult(f: Map<String, JsonValue>) = TaskResult(f.enum("conclusion", TaskConclusion.INCONCLUSIVE), f.string("summary"), f.long("finishedAtMs"))

    private fun step(s: TaskStep) = "{" + listOf(
        "\"id\":" + q(s.id), "\"taskId\":" + q(s.taskId), "\"title\":" + q(s.title),
        "\"description\":" + q(s.description), "\"dependsOn\":" + strings(s.dependsOn),
        "\"input\":" + stringMap(s.input), "\"executorId\":" + q(s.executorId),
        "\"verificationRuleIds\":" + strings(s.verificationRuleIds), "\"status\":" + q(s.status.name),
        "\"attemptCount\":" + s.attemptCount, "\"result\":" + (s.result?.let(::stepResult) ?: "null"),
        "\"verification\":" + (s.verification?.let(::verification) ?: "null"),
        "\"idempotencyKey\":" + q(s.idempotencyKey), "\"approvalGranted\":" + s.approvalGranted,
        "\"lastErrorType\":" + nullable(s.lastErrorType),
    ).joinToString(",") + "}"

    private fun decodeStep(f: Map<String, JsonValue>) = TaskStep(
        id = f.string("id"), taskId = f.string("taskId"), title = f.string("title"), description = f.string("description"),
        dependsOn = f.strings("dependsOn"), input = f.stringMap("input"), executorId = f.string("executorId"),
        verificationRuleIds = f.strings("verificationRuleIds"), status = f.enum("status", TaskStepStatus.PLANNED),
        attemptCount = f.int("attemptCount"), result = f.objOrNull("result")?.let(::decodeStepResult),
        verification = f.objOrNull("verification")?.let(::decodeVerification), idempotencyKey = f.string("idempotencyKey"),
        approvalGranted = f.bool("approvalGranted"), lastErrorType = f.stringOrNull("lastErrorType"),
    )

    private fun stepResult(r: TaskStepResult) = "{" + listOf(
        "\"summary\":" + q(r.summary), "\"output\":" + q(r.output),
        "\"evidenceReferences\":" + strings(r.evidenceReferences), "\"finishedAtMs\":" + r.finishedAtMs,
        "\"confirmedSuccess\":" + r.confirmedSuccess,
    ).joinToString(",") + "}"
    private fun decodeStepResult(f: Map<String, JsonValue>) = TaskStepResult(
        f.string("summary"), f.string("output"), f.strings("evidenceReferences"), f.long("finishedAtMs"), f.bool("confirmedSuccess"),
    )

    private fun verification(v: VerificationResult) = "{" + listOf(
        "\"taskId\":" + q(v.taskId), "\"stepId\":" + q(v.stepId), "\"status\":" + q(v.status.name),
        "\"ruleId\":" + q(v.ruleId), "\"evidenceReferences\":" + strings(v.evidenceReferences),
        "\"reason\":" + nullable(v.reason), "\"checkedAtMs\":" + v.checkedAtMs,
    ).joinToString(",") + "}"
    private fun decodeVerification(f: Map<String, JsonValue>) = VerificationResult(
        f.string("taskId"), f.string("stepId"), f.enum("status", VerificationStatus.INCONCLUSIVE),
        f.string("ruleId"), f.strings("evidenceReferences"), f.stringOrNull("reason"), f.long("checkedAtMs"),
    )

    private fun invocation(i: ToolInvocation) = "{" + listOf(
        "\"id\":" + q(i.id), "\"taskId\":" + q(i.taskId), "\"stepId\":" + q(i.stepId),
        "\"toolId\":" + q(i.toolId), "\"idempotencyKey\":" + q(i.idempotencyKey),
        "\"parameterSummary\":" + q(i.parameterSummary), "\"status\":" + q(i.status.name),
        "\"resultSummary\":" + nullable(i.resultSummary), "\"elapsedMs\":" + i.elapsedMs,
        "\"errorType\":" + nullable(i.errorType), "\"startedAtMs\":" + i.startedAtMs,
        "\"finishedAtMs\":" + (i.finishedAtMs?.toString() ?: "null"),
    ).joinToString(",") + "}"
    private fun decodeInvocation(f: Map<String, JsonValue>) = ToolInvocation(
        f.string("id"), f.string("taskId"), f.string("stepId"), f.string("toolId"), f.string("idempotencyKey"),
        f.string("parameterSummary"), f.enum("status", ToolInvocationStatus.UNKNOWN), f.stringOrNull("resultSummary"),
        f.long("elapsedMs"), f.stringOrNull("errorType"), f.long("startedAtMs"), f.longOrNull("finishedAtMs"),
    )

    private fun checkpoint(c: TaskCheckpoint) = "{" + listOf(
        "\"taskId\":" + q(c.taskId), "\"resumeAtStepId\":" + nullable(c.resumeAtStepId),
        "\"completedStepIds\":" + strings(c.completedStepIds), "\"contextSummary\":" + q(c.contextSummary),
        "\"createdAtMs\":" + c.createdAtMs,
    ).joinToString(",") + "}"
    private fun decodeCheckpoint(f: Map<String, JsonValue>) = TaskCheckpoint(
        f.string("taskId"), f.stringOrNull("resumeAtStepId"), f.strings("completedStepIds"), f.string("contextSummary"), f.long("createdAtMs"),
    )

    private fun event(e: TaskEvent) = "{" + listOf(
        "\"sequence\":" + e.sequence, "\"taskId\":" + q(e.taskId), "\"stepId\":" + nullable(e.stepId),
        "\"type\":" + q(e.type.name), "\"summary\":" + q(e.summary), "\"createdAtMs\":" + e.createdAtMs,
    ).joinToString(",") + "}"
    private fun decodeEvent(f: Map<String, JsonValue>) = TaskEvent(
        f.long("sequence"), f.string("taskId"), f.stringOrNull("stepId"), f.enum("type", TaskEventType.ERROR),
        f.string("summary"), f.long("createdAtMs"),
    )

    private fun evidence(e: EvidenceReference) = "{" + listOf(
        "\"id\":" + q(e.id), "\"taskId\":" + q(e.taskId), "\"stepId\":" + q(e.stepId),
        "\"ruleId\":" + q(e.ruleId), "\"source\":" + q(e.source), "\"summary\":" + q(e.summary),
        "\"createdAtMs\":" + e.createdAtMs,
    ).joinToString(",") + "}"
    private fun decodeEvidence(f: Map<String, JsonValue>) = EvidenceReference(
        f.string("id"), f.string("taskId"), f.string("stepId"), f.string("ruleId"), f.string("source"), f.string("summary"), f.long("createdAtMs"),
    )

    private fun q(value: String) = JsonMini.quote(value)
    private fun nullable(value: String?): String = value?.let(::q) ?: "null"
    private fun strings(values: List<String>) = values.joinToString(",", prefix = "[", postfix = "]", transform = ::q)
    private fun stringMap(values: Map<String, String>) = values.entries.joinToString(",", prefix = "{", postfix = "}") { q(it.key) + ":" + q(it.value) }
    private fun <T> array(values: List<T>, encode: (T) -> String) = values.joinToString(",", prefix = "[", postfix = "]", transform = encode)
    private fun Map<String, JsonValue>.requiredObject(key: String) = this[key]?.asObject()?.entries ?: error("Missing object $key")
    private fun Map<String, JsonValue>.objects(key: String) = this[key]?.asArray()?.items.orEmpty().map { it.asObject()?.entries ?: error("Invalid $key item") }
    private fun Map<String, JsonValue>.string(key: String) = this[key]?.asString() ?: error("Missing string $key")
    private fun Map<String, JsonValue>.stringOrNull(key: String) = this[key]?.takeUnless { it == JsonValue.JNull }?.asString()
    private fun Map<String, JsonValue>.long(key: String) = number(key).toLong()
    private fun Map<String, JsonValue>.longOrNull(key: String) = this[key]?.takeUnless { it == JsonValue.JNull }?.let { (it as? JsonValue.JNumber)?.value?.toLong() }
    private fun Map<String, JsonValue>.int(key: String) = number(key).toInt()
    private fun Map<String, JsonValue>.intOrNull(key: String) = this[key]?.takeUnless { it == JsonValue.JNull }?.let { (it as? JsonValue.JNumber)?.value?.toInt() }
    private fun Map<String, JsonValue>.doubleOrNull(key: String) = this[key]?.takeUnless { it == JsonValue.JNull }?.let { (it as? JsonValue.JNumber)?.value?.takeIf(Double::isFinite) }
    private fun Map<String, JsonValue>.number(key: String) = (this[key] as? JsonValue.JNumber)?.value ?: error("Missing number $key")
    private fun Map<String, JsonValue>.bool(key: String) = (this[key] as? JsonValue.JBool)?.value ?: false
    private inline fun <reified T : Enum<T>> Map<String, JsonValue>.enum(key: String, fallback: T): T =
        stringOrNull(key)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
    private fun Map<String, JsonValue>.strings(key: String) = this[key]?.asArray()?.items.orEmpty().mapNotNull { it.asString() }
    private fun Map<String, JsonValue>.stringMap(key: String) = this[key]?.asObject()?.entries.orEmpty().mapNotNull { (name, value) -> value.asString()?.let { name to it } }.toMap()
    private fun Map<String, JsonValue>.objOrNull(key: String) = this[key]?.takeUnless { it == JsonValue.JNull }?.asObject()?.entries
}
