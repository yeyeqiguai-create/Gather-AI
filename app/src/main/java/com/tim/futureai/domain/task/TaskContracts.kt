package com.tim.futureai.domain.task

import kotlinx.coroutines.flow.StateFlow

interface TaskRepository {
    val tasks: StateFlow<List<TaskAggregate>>
    suspend fun load(): List<TaskAggregate>
    suspend fun save(aggregate: TaskAggregate)
    suspend fun delete(taskId: String)
    /** Removes durable history while keeping the result visible in the current process. */
    suspend fun forget(taskId: String) { delete(taskId) }
    /** Only terminal tasks may be removed by the confirmed settings action. */
    suspend fun clearFinished(): Int
}

interface TaskTool {
    val definition: ToolDefinition
    suspend fun execute(input: Map<String, String>, context: ToolExecutionContext): ToolExecutionResult
    /** Used after restart to reconcile an invocation left in STARTED state. */
    suspend fun inspect(invocation: ToolInvocation): InvocationInspection = InvocationInspection.UNKNOWN
}

enum class InvocationInspection { SUCCEEDED, FAILED, UNKNOWN }

interface TaskToolRegistry {
    fun all(): List<ToolDefinition>
    fun get(id: String): TaskTool?
}

interface TaskPlanReviewer {
    /** Future UI/editor seam; returned plans are always revalidated before execution. */
    fun revise(taskId: String, draft: TaskPlanDraft): TaskPlanDraft
}

interface TaskClock { fun nowMs(): Long }

object SystemTaskClock : TaskClock { override fun nowMs(): Long = System.currentTimeMillis() }
