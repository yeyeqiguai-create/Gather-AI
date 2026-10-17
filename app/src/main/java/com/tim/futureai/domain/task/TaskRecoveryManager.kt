package com.tim.futureai.domain.task

/** Reconciles persisted in-flight work before a task can resume. Unknown side effects are never replayed. */
class TaskRecoveryManager(
    private val tools: TaskToolRegistry,
    private val audit: TaskAuditLogger,
    private val clock: TaskClock = SystemTaskClock,
) {
    suspend fun reconcile(aggregate: TaskAggregate): TaskAggregate {
        var current = aggregate
        val active = current.steps.firstOrNull { it.status in setOf(TaskStepStatus.RUNNING, TaskStepStatus.VERIFYING) }
            ?: return if (current.task.status in setOf(TaskStatus.RUNNING, TaskStatus.VERIFYING)) {
                setPaused(current, "进程恢复时没有可确认的活动步骤")
            } else if (current.task.status == TaskStatus.PLANNING) {
                val failed = current.copy(task = current.task.copy(status = TaskStatus.FAILED,
                    result = TaskResult(TaskConclusion.INCONCLUSIVE, "进程在计划生成期间中断；请重新创建任务", clock.nowMs())))
                audit.append(failed, TaskEventType.RECOVERY_CHECKED, "规划阶段中断；未执行任何计划步骤")
            } else current
        if (active.status == TaskStepStatus.VERIFYING && active.result != null) {
            val definition = tools.get(active.executorId)?.definition
            val lastInvocation = current.invocations.lastOrNull { it.stepId == active.id }
            if (definition != null && lastInvocation?.status == ToolInvocationStatus.SUCCEEDED) {
                val references = current.evidence.filter { it.id in active.result.evidenceReferences }
                val execution = ToolExecutionResult(active.result.output, active.result.summary,
                    references.map { NewEvidence(it.ruleId, it.source, it.summary) }, active.result.confirmedSuccess)
                val verification = VerificationEngine().verify(current.task.id, active, definition, execution,
                    references, active.approvalGranted, clock.nowMs())
                val nextStepStatus = when (verification.status) {
                    VerificationStatus.VERIFIED -> TaskStepStatus.SUCCEEDED
                    VerificationStatus.REQUIRES_APPROVAL -> TaskStepStatus.WAITING_FOR_APPROVAL
                    VerificationStatus.FAILED, VerificationStatus.INCONCLUSIVE -> TaskStepStatus.FAILED
                }
                val nextTaskStatus = when (verification.status) {
                    VerificationStatus.VERIFIED -> TaskStatus.PAUSED
                    VerificationStatus.REQUIRES_APPROVAL -> TaskStatus.WAITING_FOR_APPROVAL
                    VerificationStatus.FAILED, VerificationStatus.INCONCLUSIVE -> TaskStatus.FAILED
                }
                val reconciled = current.copy(
                    task = current.task.copy(status = nextTaskStatus,
                        result = if (nextTaskStatus == TaskStatus.FAILED) TaskResult(
                            if (verification.status == VerificationStatus.INCONCLUSIVE) TaskConclusion.INCONCLUSIVE else TaskConclusion.FAILED,
                            verification.reason ?: "恢复后验证未通过", clock.nowMs()) else current.task.result),
                    steps = current.steps.map { if (it.id == active.id) it.copy(status = nextStepStatus, verification = verification) else it },
                )
                return audit.append(reconciled, TaskEventType.RECOVERY_CHECKED,
                    "使用持久化工具输出重新运行验证：${verification.status}", active.id)
            }
        }
        val invocation = current.invocations.lastOrNull { it.stepId == active.id && it.status == ToolInvocationStatus.STARTED }
        val tool = tools.get(active.executorId)
        val inspection = if (invocation != null && tool != null) {
            runCatching { tool.inspect(invocation) }.getOrDefault(InvocationInspection.UNKNOWN)
        } else InvocationInspection.UNKNOWN

        current = when {
            inspection == InvocationInspection.SUCCEEDED -> {
                // The tool can confirm an effect, but without its original output we cannot verify the step.
                markUnknown(current, active, "执行器报告已执行，但缺少可验证的完整结果")
            }
            inspection == InvocationInspection.FAILED -> markFailed(current, active, "恢复检查确认执行失败")
            tool != null && !tool.definition.hasSideEffects && tool.definition.idempotent -> {
                val ready = active.copy(status = TaskStepStatus.READY, lastErrorType = null)
                val updated = current.copy(
                    task = current.task.copy(status = TaskStatus.PAUSED),
                    steps = current.steps.map { if (it.id == ready.id) ready else it },
                    invocations = current.invocations.map {
                        if (it.id == invocation?.id) it.copy(status = ToolInvocationStatus.UNKNOWN, errorType = "ProcessInterrupted") else it
                    },
                )
                audit.append(updated, TaskEventType.RECOVERY_CHECKED,
                    "已查询执行器结果；工具确认无副作用且幂等，等待用户继续后才可重试", active.id)
            }
            else -> markUnknown(current, active, "执行结果不确定；为防止重复副作用已暂停，不会自动重试")
        }
        return current
    }

    private fun setPaused(source: TaskAggregate, reason: String): TaskAggregate {
        val updated = source.copy(task = source.task.copy(status = TaskStatus.PAUSED))
        return audit.append(updated, TaskEventType.RECOVERY_CHECKED, reason)
    }

    private fun markUnknown(source: TaskAggregate, step: TaskStep, reason: String): TaskAggregate {
        val updated = source.copy(
            task = source.task.copy(status = TaskStatus.PAUSED),
            steps = source.steps.map { if (it.id == step.id) it.copy(status = TaskStepStatus.UNKNOWN, lastErrorType = "OutcomeUnknown") else it },
            checkpoints = source.checkpoints + TaskCheckpoint(source.task.id, step.id,
                source.steps.filter { it.status == TaskStepStatus.SUCCEEDED }.map { it.id }, "恢复检查：执行结果不确定", clock.nowMs()),
        )
        return audit.append(updated, TaskEventType.RECOVERY_CHECKED, reason, step.id)
    }

    private fun markFailed(source: TaskAggregate, step: TaskStep, reason: String): TaskAggregate {
        val updated = source.copy(
            task = source.task.copy(status = TaskStatus.FAILED, result = TaskResult(TaskConclusion.FAILED, reason, clock.nowMs())),
            steps = source.steps.map { if (it.id == step.id) it.copy(status = TaskStepStatus.FAILED, lastErrorType = "RecoveredFailure") else it },
        )
        return audit.append(updated, TaskEventType.RECOVERY_CHECKED, reason, step.id)
    }
}
