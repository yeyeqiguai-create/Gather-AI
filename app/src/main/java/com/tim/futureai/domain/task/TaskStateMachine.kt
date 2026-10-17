package com.tim.futureai.domain.task

/** Centralized transition policy; terminal states never jump directly to success. */
object TaskStateMachine {
    fun canTransition(from: TaskStatus, to: TaskStatus, manualRetry: Boolean = false): Boolean {
        if (from == to) return false
        return when (from) {
            TaskStatus.CREATED -> to in setOf(TaskStatus.PLANNING, TaskStatus.CANCELLED)
            TaskStatus.PLANNING -> to in setOf(TaskStatus.READY, TaskStatus.FAILED, TaskStatus.CANCELLED, TaskStatus.PAUSED)
            TaskStatus.READY -> to in setOf(TaskStatus.RUNNING, TaskStatus.CANCELLED, TaskStatus.PAUSED, TaskStatus.FAILED)
            TaskStatus.RUNNING -> to in setOf(
                TaskStatus.WAITING_FOR_APPROVAL, TaskStatus.VERIFYING, TaskStatus.SUCCEEDED,
                TaskStatus.FAILED, TaskStatus.CANCELLED, TaskStatus.PAUSED,
            )
            TaskStatus.WAITING_FOR_APPROVAL -> to in setOf(TaskStatus.RUNNING, TaskStatus.FAILED, TaskStatus.CANCELLED, TaskStatus.PAUSED)
            TaskStatus.VERIFYING -> to in setOf(TaskStatus.RUNNING, TaskStatus.SUCCEEDED, TaskStatus.FAILED, TaskStatus.CANCELLED, TaskStatus.PAUSED)
            TaskStatus.PAUSED -> to in setOf(TaskStatus.READY, TaskStatus.RUNNING, TaskStatus.CANCELLED, TaskStatus.FAILED)
            TaskStatus.FAILED -> manualRetry && to == TaskStatus.READY
            TaskStatus.SUCCEEDED, TaskStatus.CANCELLED -> false
        }
    }

    fun canTransition(from: TaskStepStatus, to: TaskStepStatus): Boolean {
        if (from == to) return false
        return when (from) {
            TaskStepStatus.PLANNED -> to in setOf(TaskStepStatus.READY, TaskStepStatus.CANCELLED)
            TaskStepStatus.READY -> to in setOf(TaskStepStatus.RUNNING, TaskStepStatus.WAITING_FOR_APPROVAL, TaskStepStatus.CANCELLED)
            TaskStepStatus.RUNNING -> to in setOf(TaskStepStatus.VERIFYING, TaskStepStatus.FAILED, TaskStepStatus.CANCELLED, TaskStepStatus.UNKNOWN)
            TaskStepStatus.WAITING_FOR_APPROVAL -> to in setOf(TaskStepStatus.READY, TaskStepStatus.RUNNING, TaskStepStatus.FAILED, TaskStepStatus.CANCELLED)
            TaskStepStatus.VERIFYING -> to in setOf(TaskStepStatus.SUCCEEDED, TaskStepStatus.WAITING_FOR_APPROVAL, TaskStepStatus.FAILED, TaskStepStatus.UNKNOWN)
            TaskStepStatus.FAILED -> to == TaskStepStatus.READY
            TaskStepStatus.UNKNOWN -> to in setOf(TaskStepStatus.READY, TaskStepStatus.FAILED, TaskStepStatus.CANCELLED)
            TaskStepStatus.SUCCEEDED, TaskStepStatus.CANCELLED -> false
        }
    }

    fun requireTaskTransition(from: TaskStatus, to: TaskStatus, manualRetry: Boolean = false) {
        require(canTransition(from, to, manualRetry)) { "Illegal task transition: $from -> $to" }
    }

    fun requireStepTransition(from: TaskStepStatus, to: TaskStepStatus) {
        require(canTransition(from, to)) { "Illegal task step transition: $from -> $to" }
    }
}
