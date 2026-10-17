package com.tim.futureai.domain.task

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue

sealed interface PlanValidation {
    data class Valid(val topologicalOrder: List<String>) : PlanValidation
    data class Invalid(val reason: String) : PlanValidation
}

/** Parses untrusted planner JSON, enforces tool schemas and rejects missing or cyclic dependencies. */
class TaskPlanner(private val tools: TaskToolRegistry) {
    fun parseAndValidate(json: String, maxSteps: Int): Pair<TaskPlanDraft?, String?> {
        val root = runCatching { JsonMini.parse(json).asObject()?.entries }.getOrNull()
            ?: return null to "规划输出不是有效 JSON 对象"
        val title = root["title"]?.asString()?.trim().orEmpty()
        val rawSteps = root["steps"]?.asArray()?.items
            ?: return null to (root["clarifyingQuestion"]?.asString()?.let { "需要补充信息：${it.take(500)}" } ?: "规划输出缺少 steps 数组")
        if (title.isBlank()) return null to "规划缺少任务名称"
        if (rawSteps.isEmpty()) return null to (root["clarifyingQuestion"]?.asString()?.let { "需要补充信息：${it.take(500)}" } ?: "当前目标无法规划为可验证步骤")
        if (rawSteps.size > maxSteps) return null to "计划包含 ${rawSteps.size} 步，超过当前上限 $maxSteps"

        val parsed = mutableListOf<TaskPlanStep>()
        for ((index, raw) in rawSteps.withIndex()) {
            val fields = raw.asObject()?.entries ?: return null to "第 ${index + 1} 步不是对象"
            val id = fields["id"]?.asString()?.trim().orEmpty()
            val titleText = fields["title"]?.asString()?.trim().orEmpty()
            val description = fields["description"]?.asString()?.trim().orEmpty()
            val executorId = fields["executorId"]?.asString()?.trim().orEmpty()
            val depends = fields["dependsOn"]?.asArray()?.items?.map { it.asString()?.trim().orEmpty() }
                ?: return null to "步骤 ${id.ifBlank { index.toString() }} 缺少 dependsOn 数组"
            val inputFields = fields["input"]?.asObject()?.entries
                ?: return null to "步骤 ${id.ifBlank { index.toString() }} 缺少 input 对象"
            val input = linkedMapOf<String, String>()
            for ((key, value) in inputFields) {
                val scalar = when (value) {
                    is JsonValue.JString -> value.value
                    is JsonValue.JNumber -> value.value.toString()
                    is JsonValue.JBool -> value.value.toString()
                    else -> return null to "步骤 ${id.ifBlank { index.toString() }} 的参数 $key 必须是标量"
                }
                input[key] = TaskPrivacy.sanitize(scalar)
            }
            val rules = fields["verificationRuleIds"]?.asArray()?.items?.map { it.asString()?.trim().orEmpty() }
                ?: return null to "步骤 ${id.ifBlank { index.toString() }} 缺少 verificationRuleIds"
            if (id.isBlank() || titleText.isBlank() || description.isBlank() || executorId.isBlank()) {
                return null to "第 ${index + 1} 步缺少 ID、标题、说明或执行器"
            }
            parsed += TaskPlanStep(id, titleText, description, depends, executorId, input, rules)
        }
        val draft = TaskPlanDraft(title, parsed)
        val validation = validate(draft, maxSteps)
        return when (validation) {
            is PlanValidation.Valid -> draft to null
            is PlanValidation.Invalid -> null to validation.reason
        }
    }

    fun validate(draft: TaskPlanDraft, maxSteps: Int): PlanValidation {
        if (draft.steps.isEmpty()) return PlanValidation.Invalid("执行计划不能为空")
        if (draft.steps.size > maxSteps) return PlanValidation.Invalid("步骤数超过上限 $maxSteps")
        if (draft.steps.map { it.id }.distinct().size != draft.steps.size) return PlanValidation.Invalid("步骤 ID 重复")
        val byId = draft.steps.associateBy { it.id }
        for (step in draft.steps) {
            if (step.dependsOn.distinct().size != step.dependsOn.size) return PlanValidation.Invalid("步骤 ${step.id} 重复声明依赖")
            val missing = step.dependsOn.firstOrNull { it !in byId }
            if (missing != null) return PlanValidation.Invalid("步骤 ${step.id} 依赖不存在的步骤 $missing")
            if (step.id in step.dependsOn) return PlanValidation.Invalid("步骤 ${step.id} 不能依赖自身")
            val tool = tools.get(step.executorId) ?: return PlanValidation.Invalid("步骤 ${step.id} 使用未注册工具 ${step.executorId}")
            val definition = tool.definition
            val unknown = step.input.keys.firstOrNull { it !in definition.parameters }
            if (unknown != null) return PlanValidation.Invalid("工具 ${definition.name} 不接受参数 $unknown")
            val missingParameter = definition.parameters.entries.firstOrNull { it.value.required && it.key !in step.input }
            if (missingParameter != null) return PlanValidation.Invalid("步骤 ${step.id} 缺少参数 ${missingParameter.key}")
            for ((name, value) in step.input) {
                val type = definition.parameters.getValue(name).type
                val valid = when (type) {
                    ParameterType.STRING -> true
                    ParameterType.NUMBER -> value.toDoubleOrNull()?.isFinite() == true
                    ParameterType.BOOLEAN -> value.equals("true", true) || value.equals("false", true)
                }
                if (!valid) return PlanValidation.Invalid("步骤 ${step.id} 的参数 $name 类型无效")
                if (value.length > 20_000) return PlanValidation.Invalid("步骤 ${step.id} 的参数 $name 超过长度限制")
            }
            if (step.verificationRuleIds.isEmpty()) return PlanValidation.Invalid("步骤 ${step.id} 未声明验证规则")
            if (step.verificationRuleIds.distinct().size != step.verificationRuleIds.size) return PlanValidation.Invalid("步骤 ${step.id} 的验证规则重复")
            val unsupportedRule = step.verificationRuleIds.firstOrNull { it !in definition.verificationRuleIds }
            if (unsupportedRule != null) return PlanValidation.Invalid("工具 ${definition.name} 不支持验证规则 $unsupportedRule")
        }
        val order = topologicalOrder(draft.steps) ?: return PlanValidation.Invalid("计划存在循环依赖")
        return PlanValidation.Valid(order)
    }

    fun toAggregate(taskId: String, goal: String, createdAtMs: Long, draft: TaskPlanDraft, usage: TaskPlannerUsage): TaskAggregate {
        val steps = draft.steps.map { step ->
            TaskStep(
                id = step.id, taskId = taskId, title = TaskPrivacy.sanitize(step.title).take(120),
                description = TaskPrivacy.sanitize(step.description).take(800), dependsOn = step.dependsOn,
                input = step.input, executorId = step.executorId, verificationRuleIds = step.verificationRuleIds,
                idempotencyKey = "$taskId:${step.id}",
            )
        }
        val task = Task(
            id = taskId, goal = TaskPrivacy.sanitize(goal).take(4_000), createdAtMs = createdAtMs,
            status = TaskStatus.READY, plannerModel = usage.model, plannerProfileId = usage.profileId,
            planVersion = PLAN_VERSION, plannerPromptTokens = usage.promptTokens,
            plannerCompletionTokens = usage.completionTokens, plannerDurationMs = usage.durationMs,
            estimatedCostUsd = usage.estimatedCostUsd,
        )
        val event = TaskEvent(1, taskId, type = TaskEventType.PLAN_ACCEPTED,
            summary = TaskPrivacy.sanitize("已生成 ${steps.size} 步计划；规划模型 ${usage.model}；版本 $PLAN_VERSION"), createdAtMs = createdAtMs)
        return TaskAggregate(task, steps, events = listOf(event), checkpoints = listOf(
            TaskCheckpoint(taskId, steps.firstOrNull()?.id, emptyList(), "已完成计划校验", createdAtMs),
        ))
    }

    private fun topologicalOrder(steps: List<TaskPlanStep>): List<String>? {
        val byId = steps.associateBy { it.id }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val ordered = mutableListOf<String>()
        fun visit(id: String): Boolean {
            if (id in visiting) return false
            if (id in visited) return true
            visiting += id
            for (dependency in byId.getValue(id).dependsOn) if (!visit(dependency)) return false
            visiting -= id
            visited += id
            ordered += id
            return true
        }
        for (step in steps) if (!visit(step.id)) return null
        return ordered
    }

    companion object { const val PLAN_VERSION = "verified-plan-v1" }
}

object TaskPrivacy {
    private val credentialAssignment = Regex("(?i)(api[_-]?key|authorization|access[_-]?token|password|secret)\\s*[:=]\\s*([^\\s,;]+)")
    private val bearer = Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*")
    private val likelyApiKey = Regex("\\bsk-[A-Za-z0-9_-]{12,}\\b")
    fun sanitize(text: String): String = text
        .replace(credentialAssignment, "\$1=[REDACTED]")
        .replace(bearer, "Bearer [REDACTED]")
        .replace(likelyApiKey, "[REDACTED_KEY]")
        .filter { it == '\n' || it == '\t' || it >= ' ' }
}

internal fun JsonValue?.asString(): String? = this?.asString()
