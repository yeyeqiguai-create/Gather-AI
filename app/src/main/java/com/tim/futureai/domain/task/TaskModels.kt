package com.tim.futureai.domain.task

/** Domain-only task records. Persisted values intentionally contain summaries, not raw credentials. */
enum class TaskStatus {
    CREATED, PLANNING, READY, RUNNING, WAITING_FOR_APPROVAL, VERIFYING, SUCCEEDED, FAILED, CANCELLED, PAUSED,
}

enum class TaskStepStatus {
    PLANNED, READY, RUNNING, WAITING_FOR_APPROVAL, VERIFYING, SUCCEEDED, FAILED, CANCELLED, UNKNOWN,
}

enum class VerificationStatus { VERIFIED, FAILED, INCONCLUSIVE, REQUIRES_APPROVAL }
enum class TaskConclusion { VERIFIED_SUCCESS, FAILED, INCONCLUSIVE, CANCELLED }
enum class TaskExecutionStrategy { DEPENDENCY_ORDERED }
enum class ToolPermission { NONE, LOCAL_READ, LOCAL_WRITE, EXTERNAL_WRITE, SEND_DATA, DELETE_DATA }
enum class ToolInvocationStatus { STARTED, SUCCEEDED, FAILED, UNKNOWN }
enum class TaskEventType {
    CREATED, PLAN_REQUESTED, PLAN_ACCEPTED, PLAN_REJECTED, STATUS_CHANGED, PERMISSION_CHECKED,
    APPROVED, DENIED, TOOL_STARTED, TOOL_FINISHED, VERIFICATION_FINISHED, RETRY_SCHEDULED,
    PAUSED, RESUMED, RECOVERY_CHECKED, CANCELLED, COMPLETED, ERROR,
}

data class TaskResult(
    val conclusion: TaskConclusion,
    val summary: String,
    val finishedAtMs: Long,
)

data class Task(
    val id: String,
    val goal: String,
    val createdAtMs: Long,
    val status: TaskStatus = TaskStatus.CREATED,
    val strategy: TaskExecutionStrategy = TaskExecutionStrategy.DEPENDENCY_ORDERED,
    val result: TaskResult? = null,
    val plannerModel: String? = null,
    val plannerProfileId: String? = null,
    val planVersion: String? = null,
    val plannerPromptTokens: Int? = null,
    val plannerCompletionTokens: Int? = null,
    val plannerDurationMs: Long? = null,
    val estimatedCostUsd: Double? = null,
)

data class TaskStepResult(
    val summary: String,
    val output: String,
    val evidenceReferences: List<String> = emptyList(),
    val finishedAtMs: Long,
    val confirmedSuccess: Boolean = false,
)

data class TaskStep(
    val id: String,
    val taskId: String,
    val title: String,
    val description: String,
    val dependsOn: List<String>,
    val input: Map<String, String>,
    val executorId: String,
    val verificationRuleIds: List<String>,
    val status: TaskStepStatus = TaskStepStatus.PLANNED,
    val attemptCount: Int = 0,
    val result: TaskStepResult? = null,
    val verification: VerificationResult? = null,
    val idempotencyKey: String,
    val approvalGranted: Boolean = false,
    val lastErrorType: String? = null,
)

data class EvidenceReference(
    val id: String,
    val taskId: String,
    val stepId: String,
    val ruleId: String,
    val source: String,
    val summary: String,
    val createdAtMs: Long,
)

data class VerificationResult(
    val taskId: String,
    val stepId: String,
    val status: VerificationStatus,
    val ruleId: String,
    val evidenceReferences: List<String>,
    val reason: String?,
    val checkedAtMs: Long,
)

data class ToolParameter(
    val type: ParameterType,
    val required: Boolean = true,
    val description: String = "",
)

enum class ParameterType { STRING, NUMBER, BOOLEAN }
enum class TaskOutputType { STRING, NUMBER, BOOLEAN, OBJECT, ARRAY }

data class TaskOutputField(val type: TaskOutputType, val required: Boolean = true)

data class ToolDefinition(
    val id: String,
    val name: String,
    val purpose: String,
    val parameters: Map<String, ToolParameter>,
    val capabilities: Set<String>,
    val permissions: Set<ToolPermission>,
    val hasSideEffects: Boolean,
    val requiresApproval: Boolean,
    val idempotent: Boolean,
    val timeoutMs: Long,
    val maxRetries: Int,
    val verificationRuleIds: List<String>,
    val outputSchema: Map<String, TaskOutputField> = emptyMap(),
)

data class ToolInvocation(
    val id: String,
    val taskId: String,
    val stepId: String,
    val toolId: String,
    val idempotencyKey: String,
    val parameterSummary: String,
    val status: ToolInvocationStatus,
    val resultSummary: String? = null,
    val elapsedMs: Long = 0,
    val errorType: String? = null,
    val startedAtMs: Long,
    val finishedAtMs: Long? = null,
)

data class TaskCheckpoint(
    val taskId: String,
    val resumeAtStepId: String?,
    val completedStepIds: List<String>,
    val contextSummary: String,
    val createdAtMs: Long,
)

data class TaskEvent(
    val sequence: Long,
    val taskId: String,
    val stepId: String? = null,
    val type: TaskEventType,
    val summary: String,
    val createdAtMs: Long,
)

data class TaskAggregate(
    val task: Task,
    val steps: List<TaskStep> = emptyList(),
    val invocations: List<ToolInvocation> = emptyList(),
    val checkpoints: List<TaskCheckpoint> = emptyList(),
    val events: List<TaskEvent> = emptyList(),
    val evidence: List<EvidenceReference> = emptyList(),
) {
    val verifiedStepCount: Int get() = steps.count { it.status == TaskStepStatus.SUCCEEDED && it.verification?.status == VerificationStatus.VERIFIED }
    val failedStepCount: Int get() = steps.count { it.status == TaskStepStatus.FAILED }
    val completedStepCount: Int get() = steps.count { it.status in setOf(TaskStepStatus.SUCCEEDED, TaskStepStatus.FAILED, TaskStepStatus.CANCELLED) }
    val pendingApproval: TaskStep? get() = steps.firstOrNull { it.status == TaskStepStatus.WAITING_FOR_APPROVAL }
}

data class TaskPlanStep(
    val id: String,
    val title: String,
    val description: String,
    val dependsOn: List<String>,
    val executorId: String,
    val input: Map<String, String>,
    val verificationRuleIds: List<String>,
)

data class TaskPlanDraft(val title: String, val steps: List<TaskPlanStep>)

data class TaskPlannerUsage(
    val profileId: String?,
    val providerName: String,
    val model: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val durationMs: Long,
    val estimatedCostUsd: Double? = null,
)

data class PlanSourceResult(val json: String, val usage: TaskPlannerUsage)

/** A planner source may use an AI provider, but its output is never executable before local validation. */
fun interface TaskPlanSource {
    suspend fun generate(goal: String, taskId: String, tools: List<ToolDefinition>, maxSteps: Int): PlanSourceResult
}

sealed interface TaskPlanResult {
    data class Ready(val aggregate: TaskAggregate, val usage: TaskPlannerUsage) : TaskPlanResult
    data class Rejected(val aggregate: TaskAggregate, val reason: String, val usage: TaskPlannerUsage? = null) : TaskPlanResult
    data object Disabled : TaskPlanResult
}

data class ToolExecutionResult(
    val output: String,
    val summary: String,
    val evidence: List<NewEvidence> = emptyList(),
    val confirmedSuccess: Boolean,
)

data class NewEvidence(val ruleId: String, val source: String, val summary: String)

data class ToolExecutionContext(
    val taskId: String,
    val stepId: String,
    val idempotencyKey: String,
    val nowMs: () -> Long,
)

data class PermissionDecision(val allowed: Boolean, val requiresApproval: Boolean, val reason: String)

enum class TaskAuthorizationPolicy { SAFE_ONLY, CONFIRM_SIDE_EFFECTS, CONFIRM_EACH_TOOL }
