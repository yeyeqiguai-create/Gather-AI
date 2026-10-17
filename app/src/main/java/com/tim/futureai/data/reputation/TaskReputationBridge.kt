package com.tim.futureai.data.reputation

import com.tim.futureai.domain.ai.ModelTask
import com.tim.futureai.domain.reputation.ReputationStore
import com.tim.futureai.domain.task.TaskAggregate
import com.tim.futureai.domain.task.TaskConclusion
import com.tim.futureai.domain.task.VerificationStatus

/**
 * Feeds real task outcomes back into reputation: planner self-report and the
 * independent local verification stay distinct (only VERIFIED counts as success).
 */
class TaskReputationBridge(private val reputationStore: ReputationStore) {
    suspend fun onTaskFinished(aggregate: TaskAggregate, costUsd: Double?) {
        val conclusion = aggregate.task.result?.conclusion ?: return
        val modelKey = aggregate.task.plannerModel ?: return
        val category = classify(aggregate.task.goal)
        val verified = conclusion == TaskConclusion.VERIFIED_SUCCESS
        val verificationFailed = aggregate.steps.any { it.verification?.status == VerificationStatus.FAILED }
        val failureType = when {
            verified -> null
            aggregate.task.status == com.tim.futureai.domain.task.TaskStatus.CANCELLED -> "cancelled"
            verificationFailed -> "verification_failed"
            aggregate.task.result?.conclusion == TaskConclusion.INCONCLUSIVE -> "inconclusive"
            else -> "planning_or_execution_failed"
        }
        reputationStore.record(
            modelKey = modelKey, category = category, success = verified,
            failureType = failureType, latencyMs = aggregate.task.plannerDurationMs ?: 0L,
            costUsd = costUsd, reason = "任务结论：$conclusion", source = "TASK",
        )
    }

    private fun classify(goal: String): ModelTask = when {
        Regex("""代码|函数|编程|code|bug|api""", RegexOption.IGNORE_CASE).containsMatchIn(goal) -> ModelTask.CODING
        Regex("""推理|为什么|分析原因|derive""", RegexOption.IGNORE_CASE).containsMatchIn(goal) -> ModelTask.REASONING
        Regex("""长文|总结全文|summarize""", RegexOption.IGNORE_CASE).containsMatchIn(goal) -> ModelTask.LONG_CONTEXT
        Regex("""分析|对比|评估""", RegexOption.IGNORE_CASE).containsMatchIn(goal) -> ModelTask.ANALYSIS
        else -> ModelTask.GENERAL
    }
}
