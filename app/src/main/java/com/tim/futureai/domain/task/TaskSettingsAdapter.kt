package com.tim.futureai.domain.task

import com.tim.futureai.domain.settings.AppPreferences

fun AppPreferences.toTaskEnginePolicy() = TaskEnginePolicy(
    enabled = taskModeEnabled,
    authorization = taskAuthorizationPolicy,
    maxSteps = taskMaxSteps,
    maxRetries = taskMaxRetries,
    stepTimeoutMs = taskStepTimeoutMs,
    totalTimeoutMs = taskTotalTimeoutMs,
    reviewPlanBeforeExecution = requireTaskPlanReview,
    retainHistory = retainTaskHistory,
)
