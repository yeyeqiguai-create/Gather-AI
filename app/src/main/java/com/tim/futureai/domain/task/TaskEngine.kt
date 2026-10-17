package com.tim.futureai.domain.task

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class TaskEnginePolicy(
    val enabled: Boolean = true,
    val authorization: TaskAuthorizationPolicy = TaskAuthorizationPolicy.CONFIRM_SIDE_EFFECTS,
    val maxSteps: Int = 12,
    val maxRetries: Int = 1,
    val stepTimeoutMs: Long = 30_000,
    val totalTimeoutMs: Long = 300_000,
    val reviewPlanBeforeExecution: Boolean = true,
    val retainHistory: Boolean = true,
)

/** Owns task lifecycle, persistence, scheduling, permission gates, recovery and verified completion. */
class TaskEngine(
    private val repository: TaskRepository,
    private val registry: TaskToolRegistry,
    private val scope: CoroutineScope,
    private val clock: TaskClock = SystemTaskClock,
    private val reviewer: TaskPlanReviewer? = null,
) {
    private val planner = TaskPlanner(registry)
    private val permissions = TaskPermissionChecker()
    private val verification = VerificationEngine()
    private val audit = TaskAuditLogger(clock)
    private val recovery = TaskRecoveryManager(registry, audit, clock)
    private val mutationMutex = Mutex()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val pauseRequests = ConcurrentHashMap.newKeySet<String>()
    private val historyRetention = ConcurrentHashMap<String, Boolean>()

    val tasks: StateFlow<List<TaskAggregate>> get() = repository.tasks

    suspend fun load(policy: TaskEnginePolicy? = null): List<TaskAggregate> {
        val loaded = repository.load()
        loaded.forEach { aggregate ->
            policy?.let { historyRetention[aggregate.task.id] = it.retainHistory }
            val reconciled = recovery.reconcile(aggregate)
            if (reconciled != aggregate) repository.save(reconciled)
            if (policy?.retainHistory == false && reconciled.task.status in setOf(TaskStatus.SUCCEEDED, TaskStatus.FAILED, TaskStatus.CANCELLED)) {
                repository.forget(reconciled.task.id)
            }
        }
        return repository.tasks.value
    }

    suspend fun plan(goal: String, source: TaskPlanSource, policy: TaskEnginePolicy): TaskPlanResult {
        if (!policy.enabled) return TaskPlanResult.Disabled
        val id = UUID.randomUUID().toString()
        historyRetention[id] = policy.retainHistory
        val createdAt = clock.nowMs()
        val task = Task(id, TaskPrivacy.sanitize(goal).take(4_000), createdAt)
        var aggregate = TaskAggregate(task)
        aggregate = audit.append(aggregate, TaskEventType.CREATED, "任务已创建")
        repository.save(aggregate)
        aggregate = aggregate.copy(task = aggregate.task.copy(status = TaskStatus.PLANNING))
        aggregate = audit.append(aggregate, TaskEventType.PLAN_REQUESTED, "开始生成结构化计划；当前工具数 ${registry.all().size}")
        repository.save(aggregate)

        val generation = try {
            source.generate(goal, id, registry.all(), policy.maxSteps)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val reason = TaskPrivacy.sanitize(error.message ?: "规划服务调用失败").take(400)
            val failed = aggregate.copy(
                task = aggregate.task.copy(
                    status = TaskStatus.FAILED,
                    result = TaskResult(TaskConclusion.FAILED, "规划失败：$reason", clock.nowMs()),
                ),
            )
            val audited = audit.append(failed, TaskEventType.PLAN_REJECTED, "规划请求失败：$reason")
            repository.save(audited)
            maybeForgetTerminal(id, audited)
            return TaskPlanResult.Rejected(audited, reason)
        }
        val (parsed, parseError) = planner.parseAndValidate(generation.json, policy.maxSteps)
        val draft = parsed?.let { reviewer?.revise(id, it) ?: it }
        val validation = draft?.let { planner.validate(it, policy.maxSteps) }
        val rejection = parseError ?: (validation as? PlanValidation.Invalid)?.reason
        if (draft == null || rejection != null) {
            val reason = TaskPrivacy.sanitize(rejection ?: "规划未通过本地校验").take(500)
            val failed = aggregate.copy(
                task = aggregate.task.copy(
                    status = TaskStatus.FAILED, plannerModel = generation.usage.model,
                    plannerProfileId = generation.usage.profileId,
                    result = TaskResult(TaskConclusion.FAILED, "计划已拒绝：$reason", clock.nowMs()),
                ),
            )
            val audited = audit.append(failed, TaskEventType.PLAN_REJECTED, "结构化计划被本地规则拒绝：$reason")
            repository.save(audited)
            maybeForgetTerminal(id, audited)
            return TaskPlanResult.Rejected(audited, reason, generation.usage)
        }

        val plannedAggregate = planner.toAggregate(id, goal, createdAt, draft, generation.usage)
        aggregate = plannedAggregate.copy(events = emptyList())
        aggregate = audit.append(aggregate, TaskEventType.CREATED, "任务已创建：${draft.steps.size} 步")
        aggregate = audit.append(aggregate, TaskEventType.PLAN_REQUESTED, "规划模型 ${generation.usage.model}；规则版本 ${TaskPlanner.PLAN_VERSION}")
        aggregate = audit.append(aggregate, TaskEventType.PLAN_ACCEPTED, "依赖、工具参数与验证规则均通过本地校验")
        repository.save(aggregate)
        return TaskPlanResult.Ready(aggregate, generation.usage)
    }

    /** Returns false if the task is already running or not in a runnable state. */
    fun start(taskId: String, policy: TaskEnginePolicy): Boolean {
        synchronized(jobs) {
            if (jobs[taskId]?.isActive == true) return false
            val aggregate = tasks.value.firstOrNull { it.task.id == taskId } ?: return false
            if (aggregate.task.status !in setOf(TaskStatus.READY, TaskStatus.PAUSED, TaskStatus.RUNNING)) return false
            pauseRequests.remove(taskId)
            val job = scope.launch(start = CoroutineStart.LAZY) { executeTask(taskId, policy) }
            historyRetention[taskId] = policy.retainHistory
            jobs[taskId] = job
            job.invokeOnCompletion { jobs.remove(taskId, job) }
            job.start()
            return true
        }
    }

    suspend fun pause(taskId: String) {
        val job = jobs[taskId]
        if (job?.isActive == true) {
            pauseRequests += taskId
            job.join()
        } else {
            mutate(taskId) { current ->
                if (current.task.status !in setOf(TaskStatus.READY, TaskStatus.RUNNING)) current
                else transition(current, TaskStatus.PAUSED, TaskEventType.PAUSED, "用户请求暂停")
            }
        }
    }

    suspend fun resume(taskId: String, policy: TaskEnginePolicy): Boolean {
        val updated = mutate(taskId) { current ->
            if (current.task.status != TaskStatus.PAUSED || current.steps.any { it.status == TaskStepStatus.UNKNOWN }) current
            else transition(current, TaskStatus.READY, TaskEventType.RESUMED, "用户继续任务")
        } ?: return false
        return updated.task.status == TaskStatus.READY && start(taskId, policy)
    }

    suspend fun approveStep(taskId: String, stepId: String, policy: TaskEnginePolicy): Boolean {
        val current = tasks.value.firstOrNull { it.task.id == taskId } ?: return false
        val step = current.steps.firstOrNull { it.id == stepId } ?: return false
        if (current.task.status != TaskStatus.WAITING_FOR_APPROVAL || step.status != TaskStepStatus.WAITING_FOR_APPROVAL) return false

        if (step.verification?.status == VerificationStatus.REQUIRES_APPROVAL && step.result != null) {
            val tool = registry.get(step.executorId) ?: return false
            val references = current.evidence.filter { it.id in step.result.evidenceReferences }
            val result = ToolExecutionResult(step.result.output, step.result.summary,
                references.map { NewEvidence(it.ruleId, it.source, it.summary) }, step.result.confirmedSuccess)
            val checked = verification.verify(taskId, step, tool.definition, result, references, approved = true, nowMs = clock.nowMs())
            val nextStatus = if (checked.status == VerificationStatus.VERIFIED) TaskStepStatus.SUCCEEDED else TaskStepStatus.FAILED
            val saved = mutate(taskId) { aggregate ->
                val updatedStep = aggregate.steps.first { it.id == stepId }.copy(status = nextStatus, approvalGranted = true, verification = checked)
                val next = aggregate.copy(
                    task = aggregate.task.copy(status = TaskStatus.RUNNING),
                    steps = aggregate.steps.map { if (it.id == stepId) updatedStep else it },
                )
                audit.append(next, TaskEventType.APPROVED, "用户确认了人工验证结果：${checked.status}", stepId)
            } ?: return false
            return if (checked.status == VerificationStatus.VERIFIED) start(taskId, policy)
            else {
                failTask(taskId, TaskConclusion.INCONCLUSIVE, "人工确认后验证仍未通过")
                true
            }
        }

        val updated = mutate(taskId) { aggregate ->
            val approved = aggregate.steps.first { it.id == stepId }.copy(status = TaskStepStatus.READY, approvalGranted = true)
            audit.append(aggregate.copy(
                task = aggregate.task.copy(status = TaskStatus.RUNNING),
                steps = aggregate.steps.map { if (it.id == stepId) approved else it },
            ), TaskEventType.APPROVED, "用户批准工具 ${approved.executorId} 的单次调用", stepId)
        } ?: return false
        return updated.task.status == TaskStatus.RUNNING && start(taskId, policy)
    }

    suspend fun retryStep(taskId: String, stepId: String, policy: TaskEnginePolicy): Boolean {
        val updated = mutate(taskId) { aggregate ->
            val step = aggregate.steps.firstOrNull { it.id == stepId } ?: return@mutate aggregate
            val tool = registry.get(step.executorId) ?: return@mutate aggregate
            if (aggregate.task.status != TaskStatus.FAILED || step.status != TaskStepStatus.FAILED ||
                tool.definition.hasSideEffects || !tool.definition.idempotent ||
                step.dependsOn.any { dependency -> aggregate.steps.firstOrNull { it.id == dependency }?.verification?.status != VerificationStatus.VERIFIED }) {
                return@mutate aggregate
            }
            val updatedStep = step.copy(status = TaskStepStatus.READY, result = null, verification = null, lastErrorType = null, approvalGranted = false)
            TaskStateMachine.requireTaskTransition(aggregate.task.status, TaskStatus.READY, manualRetry = true)
            val reset = aggregate.copy(
                task = aggregate.task.copy(status = TaskStatus.READY, result = null),
                steps = aggregate.steps.map { if (it.id == stepId) updatedStep else it },
            )
            audit.append(reset, TaskEventType.RETRY_SCHEDULED, "用户手动重试；工具已确认无副作用且幂等", stepId)
        } ?: return false
        return updated.task.status == TaskStatus.READY && start(taskId, policy)
    }

    suspend fun cancel(taskId: String) {
        jobs[taskId]?.cancelAndJoin()
        withContext(NonCancellable) { finishCancellation(taskId) }
    }

    suspend fun clearFinished(): Int = repository.clearFinished()

    private suspend fun executeTask(taskId: String, policy: TaskEnginePolicy) {
        try {
            val startAt = clock.nowMs()
            var current = tasks.value.firstOrNull { it.task.id == taskId } ?: return
            if (current.task.status == TaskStatus.READY) {
                current = mutate(taskId) { transition(it, TaskStatus.RUNNING, TaskEventType.STATUS_CHANGED, "调度器开始执行") } ?: return
            }
            while (true) {
                if (taskId in pauseRequests) {
                    mutate(taskId) { transition(it, TaskStatus.PAUSED, TaskEventType.PAUSED, "当前步骤完成后按请求暂停") }
                    return
                }
                if (clock.nowMs() - startAt > policy.totalTimeoutMs) {
                    failTask(taskId, TaskConclusion.FAILED, "任务超过总执行时间限制")
                    return
                }
                current = tasks.value.firstOrNull { it.task.id == taskId } ?: return
                if (current.task.status == TaskStatus.WAITING_FOR_APPROVAL || current.task.status in setOf(TaskStatus.PAUSED, TaskStatus.FAILED, TaskStatus.CANCELLED)) return
                if (current.steps.isNotEmpty() && current.steps.all { it.status == TaskStepStatus.SUCCEEDED && it.verification?.status == VerificationStatus.VERIFIED }) {
                    completeTask(taskId)
                    return
                }
                val step = nextRunnable(current)
                if (step == null) {
                    val unresolved = current.steps.firstOrNull { it.status !in setOf(TaskStepStatus.SUCCEEDED, TaskStepStatus.CANCELLED) }
                    if (unresolved == null) {
                        failTask(taskId, TaskConclusion.INCONCLUSIVE, "没有可执行步骤，任务无法得到已验证结论")
                    } else {
                        failTask(taskId, TaskConclusion.FAILED, "步骤 ${unresolved.id} 的依赖未达到已验证成功状态")
                    }
                    return
                }
                val remainingMs = (startAt + policy.totalTimeoutMs - clock.nowMs()).coerceAtLeast(1)
                if (!executeStep(taskId, step.id, policy, remainingMs)) return
            }
        } catch (_: TimeoutCancellationException) {
            failTask(taskId, TaskConclusion.FAILED, "任务超过总执行时间限制")
        } catch (_: CancellationException) {
            withContext(NonCancellable) { finishCancellation(taskId) }
        } catch (error: Throwable) {
            failTask(taskId, TaskConclusion.FAILED, "任务引擎错误：${TaskPrivacy.sanitize(error.message ?: error::class.java.simpleName).take(300)}")
        }
    }

    private suspend fun executeStep(taskId: String, stepId: String, policy: TaskEnginePolicy, remainingTotalMs: Long): Boolean {
        var aggregate = tasks.value.firstOrNull { it.task.id == taskId } ?: return false
        var step = aggregate.steps.firstOrNull { it.id == stepId } ?: return false
        val tool = registry.get(step.executorId) ?: run {
            failTask(taskId, TaskConclusion.FAILED, "工具 ${step.executorId} 未注册")
            return false
        }
        if (aggregate.invocations.any { it.stepId == stepId && it.status == ToolInvocationStatus.STARTED }) {
            val recovered = recovery.reconcile(aggregate)
            repository.save(recovered)
            return false
        }
        if (step.status == TaskStepStatus.PLANNED) {
            aggregate = mutate(taskId) { source ->
                val planned = source.steps.first { it.id == stepId }
                TaskStateMachine.requireStepTransition(planned.status, TaskStepStatus.READY)
                source.copy(steps = source.steps.map { if (it.id == stepId) planned.copy(status = TaskStepStatus.READY) else it })
            } ?: return false
            step = aggregate.steps.first { it.id == stepId }
        }
        val decision = permissions.check(tool.definition, policy.authorization, step.approvalGranted)
        aggregate = mutate(taskId) { audit.append(it, TaskEventType.PERMISSION_CHECKED, "${tool.definition.id}：${decision.reason}", stepId) } ?: return false
        if (decision.requiresApproval) {
            mutate(taskId) { source ->
                val waiting = source.steps.first { it.id == stepId }.copy(status = TaskStepStatus.WAITING_FOR_APPROVAL)
                val next = source.copy(task = source.task.copy(status = TaskStatus.WAITING_FOR_APPROVAL),
                    steps = source.steps.map { if (it.id == stepId) waiting else it })
                audit.append(next, TaskEventType.PERMISSION_CHECKED, "等待对本次工具调用作出确认", stepId)
            }
            return false
        }
        if (!decision.allowed) {
            failStep(taskId, stepId, "PermissionDenied", decision.reason)
            return false
        }

        val now = clock.nowMs()
        val invocation = ToolInvocation(
            id = UUID.randomUUID().toString(), taskId = taskId, stepId = stepId, toolId = tool.definition.id,
            idempotencyKey = step.idempotencyKey,
            parameterSummary = TaskPrivacy.sanitize(step.input.entries.joinToString { "${it.key}=${it.value}" }).take(800),
            status = ToolInvocationStatus.STARTED, startedAtMs = now,
        )
        aggregate = mutate(taskId) { source ->
            val currentStep = source.steps.first { it.id == stepId }
            TaskStateMachine.requireStepTransition(currentStep.status, TaskStepStatus.RUNNING)
            val running = currentStep.copy(status = TaskStepStatus.RUNNING, attemptCount = currentStep.attemptCount + 1)
            val checkpoint = TaskCheckpoint(taskId, stepId,
                source.steps.filter { it.status == TaskStepStatus.SUCCEEDED }.map { it.id },
                "步骤开始前检查点；只保存步骤引用和状态摘要", now)
            val next = source.copy(
                task = if (source.task.status == TaskStatus.VERIFYING) source.task.copy(status = TaskStatus.RUNNING) else source.task,
                steps = source.steps.map { if (it.id == stepId) running else it },
                invocations = source.invocations + invocation,
                checkpoints = source.checkpoints + checkpoint,
            )
            audit.append(next, TaskEventType.TOOL_STARTED, "开始调用 ${tool.definition.name}，第 ${running.attemptCount} 次；幂等键 ${step.idempotencyKey}", stepId)
        } ?: return false
        step = aggregate.steps.first { it.id == stepId }

        val execution = try {
            withTimeout(minOf(policy.stepTimeoutMs, tool.definition.timeoutMs, remainingTotalMs).coerceAtLeast(1)) {
                tool.execute(step.input, ToolExecutionContext(taskId, stepId, step.idempotencyKey, clock::nowMs))
            }
        } catch (timeout: TimeoutCancellationException) {
            return handleExecutionFailure(taskId, stepId, tool.definition, invocation, "StepTimeout", "步骤执行超时", policy)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            return handleExecutionFailure(taskId, stepId, tool.definition, invocation,
                error::class.java.simpleName.ifBlank { "ToolFailure" }, TaskPrivacy.sanitize(error.message ?: "执行失败"), policy)
        }

        val finish = clock.nowMs()
        val evidence = execution.evidence.map { item ->
            EvidenceReference(UUID.randomUUID().toString(), taskId, stepId, item.ruleId,
                TaskPrivacy.sanitize(item.source).take(100), TaskPrivacy.sanitize(item.summary).take(300), finish)
        }
        mutate(taskId) { source ->
            val currentStep = source.steps.first { it.id == stepId }
            val result = TaskStepResult(
                summary = TaskPrivacy.sanitize(execution.summary).take(500),
                output = TaskPrivacy.sanitize(execution.output).take(10_000),
                evidenceReferences = evidence.map { it.id }, finishedAtMs = finish,
                confirmedSuccess = execution.confirmedSuccess,
            )
            val verifying = currentStep.copy(status = TaskStepStatus.VERIFYING, result = result)
            val invocationDone = invocation.copy(
                status = ToolInvocationStatus.SUCCEEDED,
                resultSummary = result.summary.take(500), elapsedMs = (finish - invocation.startedAtMs).coerceAtLeast(0), finishedAtMs = finish,
            )
            val next = source.copy(
                task = source.task.copy(status = TaskStatus.VERIFYING),
                steps = source.steps.map { if (it.id == stepId) verifying else it },
                invocations = source.invocations.map { if (it.id == invocation.id) invocationDone else it },
                evidence = source.evidence + evidence,
            )
            audit.append(next, TaskEventType.TOOL_FINISHED, "工具完成：${result.summary}；耗时 ${invocationDone.elapsedMs} ms", stepId)
        } ?: return false

        val latest = tasks.value.first { it.task.id == taskId }
        val verifiedStep = latest.steps.first { it.id == stepId }
        val verificationResult = verification.verify(taskId, verifiedStep, tool.definition, execution, evidence,
            approved = verifiedStep.approvalGranted, nowMs = clock.nowMs())
        mutate(taskId) { source ->
            val currentStep = source.steps.first { it.id == stepId }
            val nextStepStatus = when (verificationResult.status) {
                VerificationStatus.VERIFIED -> TaskStepStatus.SUCCEEDED
                VerificationStatus.REQUIRES_APPROVAL -> TaskStepStatus.WAITING_FOR_APPROVAL
                VerificationStatus.FAILED, VerificationStatus.INCONCLUSIVE -> TaskStepStatus.FAILED
            }
            val updatedStep = currentStep.copy(status = nextStepStatus, verification = verificationResult,
                lastErrorType = if (nextStepStatus == TaskStepStatus.FAILED) "Verification${verificationResult.status}" else null)
            val nextTaskStatus = when (verificationResult.status) {
                VerificationStatus.VERIFIED -> TaskStatus.RUNNING
                VerificationStatus.REQUIRES_APPROVAL -> TaskStatus.WAITING_FOR_APPROVAL
                VerificationStatus.FAILED, VerificationStatus.INCONCLUSIVE -> TaskStatus.FAILED
            }
            val result = when (verificationResult.status) {
                VerificationStatus.VERIFIED, VerificationStatus.REQUIRES_APPROVAL -> source.task.result
                VerificationStatus.FAILED -> TaskResult(TaskConclusion.FAILED, verificationResult.reason ?: "验证失败", clock.nowMs())
                VerificationStatus.INCONCLUSIVE -> TaskResult(TaskConclusion.INCONCLUSIVE, verificationResult.reason ?: "验证结果不确定", clock.nowMs())
            }
            val updated = source.copy(task = source.task.copy(status = nextTaskStatus, result = result),
                steps = source.steps.map { if (it.id == stepId) updatedStep else it },
                checkpoints = if (verificationResult.status == VerificationStatus.VERIFIED) source.checkpoints + TaskCheckpoint(
                    taskId, source.steps.firstOrNull { it.status != TaskStepStatus.SUCCEEDED && it.id != stepId }?.id,
                    source.steps.filter { it.status == TaskStepStatus.SUCCEEDED || it.id == stepId }.map { it.id },
                    "已验证步骤检查点", clock.nowMs()) else source.checkpoints)
            audit.append(updated, TaskEventType.VERIFICATION_FINISHED,
                "验证 ${verificationResult.status}${verificationResult.reason?.let { "：$it" }.orEmpty()}；证据 ${verificationResult.evidenceReferences.size} 项",
                stepId)
        }
        val after = tasks.value.firstOrNull { it.task.id == taskId } ?: return false
        maybeForgetTerminal(taskId, after)
        return after.task.status == TaskStatus.RUNNING
    }

    private suspend fun handleExecutionFailure(
        taskId: String,
        stepId: String,
        definition: ToolDefinition,
        invocation: ToolInvocation,
        errorType: String,
        reason: String,
        policy: TaskEnginePolicy,
    ): Boolean {
        val safeRetry = !definition.hasSideEffects && definition.idempotent
        val current = tasks.value.firstOrNull { it.task.id == taskId } ?: return false
        val step = current.steps.first { it.id == stepId }
        val retryLimit = minOf(policy.maxRetries, definition.maxRetries)
        val retry = safeRetry && step.attemptCount <= retryLimit
        val now = clock.nowMs()
        if (!safeRetry && invocation.status == ToolInvocationStatus.STARTED) {
            mutate(taskId) { source ->
                val unknown = source.steps.first { it.id == stepId }.copy(status = TaskStepStatus.UNKNOWN, lastErrorType = errorType)
                val next = source.copy(task = source.task.copy(status = TaskStatus.PAUSED,
                    result = TaskResult(TaskConclusion.INCONCLUSIVE, "执行结果不确定：${TaskPrivacy.sanitize(reason).take(240)}", now)),
                    steps = source.steps.map { if (it.id == stepId) unknown else it },
                    invocations = source.invocations.map { if (it.id == invocation.id) it.copy(status = ToolInvocationStatus.UNKNOWN,
                        errorType = errorType, elapsedMs = (now - invocation.startedAtMs).coerceAtLeast(0), finishedAtMs = now) else it })
                audit.append(next, TaskEventType.RECOVERY_CHECKED, "非幂等调用结果不确定，已暂停且禁止重试：$reason", stepId)
            }
            return false
        }
        mutate(taskId) { source ->
            val updatedStep = source.steps.first { it.id == stepId }.copy(
                status = if (retry) TaskStepStatus.READY else TaskStepStatus.FAILED,
                lastErrorType = errorType,
            )
            val failedInvocation = invocation.copy(status = ToolInvocationStatus.FAILED,
                resultSummary = TaskPrivacy.sanitize(reason).take(400), elapsedMs = (now - invocation.startedAtMs).coerceAtLeast(0),
                errorType = errorType, finishedAtMs = now)
            val next = source.copy(
                task = if (retry) source.task.copy(status = TaskStatus.RUNNING) else source.task.copy(status = TaskStatus.FAILED,
                    result = TaskResult(TaskConclusion.FAILED, "步骤 ${stepId} 失败：${TaskPrivacy.sanitize(reason).take(300)}", now)),
                steps = source.steps.map { if (it.id == stepId) updatedStep else it },
                invocations = source.invocations.map { if (it.id == invocation.id) failedInvocation else it },
            )
            audit.append(next, if (retry) TaskEventType.RETRY_SCHEDULED else TaskEventType.ERROR,
                if (retry) "安全幂等步骤将在有限重试内再次执行：$reason" else "步骤执行失败：$reason", stepId)
        }
        if (!retry) maybeForgetTerminal(taskId)
        return retry
    }

    private fun nextRunnable(aggregate: TaskAggregate): TaskStep? {
        val byId = aggregate.steps.associateBy { it.id }
        return aggregate.steps.firstOrNull { step ->
            step.status in setOf(TaskStepStatus.PLANNED, TaskStepStatus.READY) &&
                step.dependsOn.all { dependency -> byId[dependency]?.let {
                    it.status == TaskStepStatus.SUCCEEDED && it.verification?.status == VerificationStatus.VERIFIED
                } == true }
        }
    }

    private suspend fun completeTask(taskId: String) {
        val completed = mutate(taskId) { source ->
            val completed = source.copy(task = source.task.copy(
                status = TaskStatus.SUCCEEDED,
                result = TaskResult(TaskConclusion.VERIFIED_SUCCESS,
                    "${source.steps.size} 个步骤均有 VERIFIED 验证结果", clock.nowMs()),
            ))
            audit.append(completed, TaskEventType.COMPLETED,
                "任务成功：所有步骤均已验证；${source.steps.size}/${source.steps.size} VERIFIED")
        }
        maybeForgetTerminal(taskId, completed)
    }

    private suspend fun failTask(taskId: String, conclusion: TaskConclusion, reason: String) {
        val failed = mutate(taskId) { source ->
            if (source.task.status in setOf(TaskStatus.SUCCEEDED, TaskStatus.CANCELLED)) return@mutate source
            val failed = source.copy(task = source.task.copy(
                status = TaskStatus.FAILED, result = TaskResult(conclusion, TaskPrivacy.sanitize(reason).take(500), clock.nowMs()),
            ))
            audit.append(failed, TaskEventType.ERROR, reason)
        }
        maybeForgetTerminal(taskId, failed)
    }

    private suspend fun failStep(taskId: String, stepId: String, errorType: String, reason: String) {
        val failed = mutate(taskId) { source ->
            val updated = source.copy(task = source.task.copy(status = TaskStatus.FAILED,
                result = TaskResult(TaskConclusion.FAILED, TaskPrivacy.sanitize(reason).take(500), clock.nowMs())),
                steps = source.steps.map { if (it.id == stepId) it.copy(status = TaskStepStatus.FAILED, lastErrorType = errorType) else it })
            audit.append(updated, TaskEventType.ERROR, reason, stepId)
        }
        maybeForgetTerminal(taskId, failed)
    }

    private suspend fun finishCancellation(taskId: String) {
        val cancelled = mutate(taskId) { source ->
            if (source.task.status in setOf(TaskStatus.SUCCEEDED, TaskStatus.FAILED, TaskStatus.CANCELLED)) return@mutate source
            val active = source.steps.firstOrNull { it.status == TaskStepStatus.RUNNING }
            val tool = active?.let { registry.get(it.executorId) }
            val uncertain = active != null && tool?.definition?.hasSideEffects == true
            val next = source.copy(
                task = source.task.copy(
                    status = if (uncertain) TaskStatus.PAUSED else TaskStatus.CANCELLED,
                    result = TaskResult(if (uncertain) TaskConclusion.INCONCLUSIVE else TaskConclusion.CANCELLED,
                        if (uncertain) "取消后非幂等操作结果不确定，已暂停以避免误报" else "用户已取消任务", clock.nowMs()),
                ),
                steps = source.steps.map { step -> if (step.id == active?.id) step.copy(
                    status = if (uncertain) TaskStepStatus.UNKNOWN else TaskStepStatus.CANCELLED,
                    lastErrorType = if (uncertain) "OutcomeUnknown" else step.lastErrorType,
                ) else step },
                invocations = source.invocations.map { invocation ->
                    if (active != null && invocation.stepId == active.id && invocation.status == ToolInvocationStatus.STARTED)
                        invocation.copy(status = if (uncertain) ToolInvocationStatus.UNKNOWN else ToolInvocationStatus.FAILED,
                            errorType = "Cancelled", finishedAtMs = clock.nowMs()) else invocation
                },
            )
            audit.append(next, TaskEventType.CANCELLED, if (uncertain) "收到取消；不确定的副作用已保留并暂停" else "任务已取消")
        }
        maybeForgetTerminal(taskId, cancelled)
    }

    private fun transition(
        source: TaskAggregate,
        status: TaskStatus,
        eventType: TaskEventType,
        summary: String,
        manualRetry: Boolean = false,
    ): TaskAggregate {
        TaskStateMachine.requireTaskTransition(source.task.status, status, manualRetry)
        return audit.append(source.copy(task = source.task.copy(status = status)), eventType, summary)
    }

    private suspend fun mutate(taskId: String, transform: (TaskAggregate) -> TaskAggregate): TaskAggregate? = mutationMutex.withLock {
        val current = repository.tasks.value.firstOrNull { it.task.id == taskId } ?: return@withLock null
        val next = transform(current)
        if (next != current) repository.save(next)
        next
    }

    private suspend fun maybeForgetTerminal(taskId: String, aggregate: TaskAggregate? = tasks.value.firstOrNull { it.task.id == taskId }) {
        if (historyRetention[taskId] == false && aggregate?.task?.status in setOf(TaskStatus.SUCCEEDED, TaskStatus.FAILED, TaskStatus.CANCELLED)) {
            repository.forget(taskId)
        }
    }
}
