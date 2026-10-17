package com.tim.futureai.domain.task

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import kotlin.math.abs

/** The first registry is intentionally local-only and contains no arbitrary code or network executor. */
class BuiltInTaskToolRegistry(private val repository: TaskRepository) : TaskToolRegistry {
    private val tools: List<TaskTool> = listOf(
        CalculatorTaskTool(), JsonValidationTaskTool(), TextRulesTaskTool(), TaskEvidenceQueryTool(repository),
    )
    override fun all(): List<ToolDefinition> = tools.map { it.definition }
    override fun get(id: String): TaskTool? = tools.firstOrNull { it.definition.id == id }
}

object TaskToolIds {
    const val CALCULATOR = "local.calculator"
    const val JSON_VALIDATE = "local.json.validate"
    const val TEXT_RULES = "local.text.rules"
    const val TASK_QUERY = "local.task.evidence.query"
}

class TaskPermissionChecker {
    fun check(definition: ToolDefinition, policy: TaskAuthorizationPolicy, userApproved: Boolean): PermissionDecision {
        if (definition.hasSideEffects && definition.permissions.isEmpty()) {
            return PermissionDecision(false, false, "副作用工具未声明权限，已拒绝")
        }
        if (policy == TaskAuthorizationPolicy.SAFE_ONLY && definition.hasSideEffects) {
            return PermissionDecision(false, false, "当前授权策略仅允许无副作用工具")
        }
        val approvalIsRequired = definition.requiresApproval ||
            (definition.hasSideEffects && policy == TaskAuthorizationPolicy.CONFIRM_SIDE_EFFECTS) ||
            policy == TaskAuthorizationPolicy.CONFIRM_EACH_TOOL
        if (approvalIsRequired && !userApproved) {
            return PermissionDecision(false, true, "执行前需要对该工具调用进行明确确认")
        }
        if (definition.permissions.any { it != ToolPermission.NONE } && definition.hasSideEffects && !userApproved) {
            return PermissionDecision(false, true, "权限操作必须逐次确认")
        }
        return PermissionDecision(true, false, "本地权限策略允许执行")
    }
}

class CalculatorTaskTool : TaskTool {
    override val definition = ToolDefinition(
        id = TaskToolIds.CALCULATOR, name = "本地计算", purpose = "使用受限算术解析器进行确定性计算",
        parameters = mapOf("expression" to ToolParameter(ParameterType.STRING), "expected" to ToolParameter(ParameterType.NUMBER, false)),
        capabilities = setOf("deterministic", "local-compute"), permissions = emptySet(), hasSideEffects = false,
        requiresApproval = false, idempotent = true, timeoutMs = 3_000, maxRetries = 0,
        verificationRuleIds = listOf("structure.non-empty-output", "status.confirmed-success", "result.calculation-matches-expression", "evidence.current-step"),
        outputSchema = mapOf("value" to TaskOutputField(TaskOutputType.NUMBER)),
    )

    override suspend fun execute(input: Map<String, String>, context: ToolExecutionContext): ToolExecutionResult {
        val expression = input["expression"] ?: error("缺少 expression")
        val value = SafeCalculator.evaluate(expression)
        val expected = input["expected"]?.toDoubleOrNull()
        val output = "{\"value\":${value.toString()}}"
        return ToolExecutionResult(
            output = output, summary = "已计算表达式，结果 ${value.toString().take(80)}",
            evidence = listOf(
                NewEvidence("result.calculation-matches-expression", definition.id, "本地算术表达式与输出数值可重复核验"),
                NewEvidence("evidence.current-step", definition.id, "证据由当前任务步骤的本地计算器生成"),
            ),
            confirmedSuccess = expected == null || abs(value - expected) <= 1e-9,
        )
    }
}

class JsonValidationTaskTool : TaskTool {
    override val definition = ToolDefinition(
        id = TaskToolIds.JSON_VALIDATE, name = "JSON 结构校验", purpose = "解析 JSON 并检查声明的必需键",
        parameters = mapOf("json" to ToolParameter(ParameterType.STRING), "requiredKeys" to ToolParameter(ParameterType.STRING, false)),
        capabilities = setOf("deterministic", "structured-validation", "local-read"), permissions = setOf(ToolPermission.LOCAL_READ),
        hasSideEffects = false, requiresApproval = false, idempotent = true, timeoutMs = 3_000, maxRetries = 0,
        verificationRuleIds = listOf("structure.non-empty-output", "status.confirmed-success", "business.json-valid", "evidence.current-step"),
        outputSchema = mapOf("valid" to TaskOutputField(TaskOutputType.BOOLEAN), "missingKeys" to TaskOutputField(TaskOutputType.ARRAY)),
    )

    override suspend fun execute(input: Map<String, String>, context: ToolExecutionContext): ToolExecutionResult {
        val raw = input["json"] ?: error("缺少 json")
        val parsed = runCatching { JsonMini.parse(raw) }.getOrNull()
        val required = input["requiredKeys"].orEmpty().split(',').map(String::trim).filter(String::isNotBlank)
        val objectFields = parsed?.asObject()?.entries
        val missing = required.filter { it !in objectFields.orEmpty() }
        val valid = parsed != null && (objectFields != null || required.isEmpty()) && missing.isEmpty()
        val output = "{\"valid\":$valid,\"missingKeys\":[${missing.joinToString(",") { JsonMini.quote(it) }}]}"
        return ToolExecutionResult(
            output = output, summary = if (valid) "JSON 解析成功，必需字段齐全" else "JSON 无效或缺少必需字段",
            evidence = listOf(
                NewEvidence("business.json-valid", definition.id, "JSON 解析状态及必需字段检查结果"),
                NewEvidence("evidence.current-step", definition.id, "证据由当前任务步骤的本地 JSON 校验器生成"),
            ),
            confirmedSuccess = valid,
        )
    }
}

class TextRulesTaskTool : TaskTool {
    override val definition = ToolDefinition(
        id = TaskToolIds.TEXT_RULES, name = "文本规则检查", purpose = "按用户给定的包含/排除字面规则检查文本",
        parameters = mapOf(
            "text" to ToolParameter(ParameterType.STRING),
            "contains" to ToolParameter(ParameterType.STRING, false),
            "excludes" to ToolParameter(ParameterType.STRING, false),
        ),
        capabilities = setOf("deterministic", "text-validation", "local-read"), permissions = setOf(ToolPermission.LOCAL_READ),
        hasSideEffects = false, requiresApproval = false, idempotent = true, timeoutMs = 3_000, maxRetries = 0,
        verificationRuleIds = listOf("structure.non-empty-output", "status.confirmed-success", "business.text-rules", "evidence.current-step"),
        outputSchema = mapOf(
            "passed" to TaskOutputField(TaskOutputType.BOOLEAN),
            "missing" to TaskOutputField(TaskOutputType.ARRAY),
            "excludedPresent" to TaskOutputField(TaskOutputType.ARRAY),
        ),
    )

    override suspend fun execute(input: Map<String, String>, context: ToolExecutionContext): ToolExecutionResult {
        val text = input["text"] ?: error("缺少 text")
        val contains = input["contains"].orEmpty().split('|').map(String::trim).filter(String::isNotBlank)
        val excludes = input["excludes"].orEmpty().split('|').map(String::trim).filter(String::isNotBlank)
        require(contains.isNotEmpty() || excludes.isNotEmpty()) { "至少需要一条包含或排除规则" }
        val missing = contains.filterNot(text::contains)
        val present = excludes.filter(text::contains)
        val passed = missing.isEmpty() && present.isEmpty()
        val output = "{\"passed\":$passed,\"missing\":[${missing.joinToString(",") { JsonMini.quote(it) }}],\"excludedPresent\":[${present.joinToString(",") { JsonMini.quote(it) }}]}"
        return ToolExecutionResult(
            output = output, summary = if (passed) "所有文本规则均通过" else "文本规则未全部满足",
            evidence = listOf(
                NewEvidence("business.text-rules", definition.id, "字面包含与排除条件检查结果"),
                NewEvidence("evidence.current-step", definition.id, "证据由当前任务步骤的本地文本规则检查器生成"),
            ),
            confirmedSuccess = passed,
        )
    }
}

class TaskEvidenceQueryTool(private val repository: TaskRepository) : TaskTool {
    override val definition = ToolDefinition(
        id = TaskToolIds.TASK_QUERY, name = "任务与证据查询", purpose = "只读查询本地任务状态和验证证据摘要",
        parameters = mapOf("taskId" to ToolParameter(ParameterType.STRING), "stepId" to ToolParameter(ParameterType.STRING, false)),
        capabilities = setOf("deterministic", "local-read", "evidence-query"), permissions = setOf(ToolPermission.LOCAL_READ),
        hasSideEffects = false, requiresApproval = false, idempotent = true, timeoutMs = 5_000, maxRetries = 1,
        verificationRuleIds = listOf("structure.non-empty-output", "status.confirmed-success", "evidence.current-step"),
        outputSchema = mapOf(
            "found" to TaskOutputField(TaskOutputType.BOOLEAN),
            "status" to TaskOutputField(TaskOutputType.STRING),
            "steps" to TaskOutputField(TaskOutputType.NUMBER),
            "verified" to TaskOutputField(TaskOutputType.NUMBER),
        ),
    )

    override suspend fun execute(input: Map<String, String>, context: ToolExecutionContext): ToolExecutionResult {
        val taskId = input["taskId"] ?: error("缺少 taskId")
        val stepId = input["stepId"]
        val task = repository.load().firstOrNull { it.task.id == taskId }
        val matchingSteps = task?.steps.orEmpty().filter { stepId == null || it.id == stepId }
        val output = "{\"found\":${task != null},\"status\":${JsonMini.quote(task?.task?.status?.name.orEmpty())},\"steps\":${matchingSteps.size},\"verified\":${matchingSteps.count { it.verification?.status == VerificationStatus.VERIFIED }}}"
        return ToolExecutionResult(
            output = output, summary = if (task == null) "本地不存在该任务" else "查询到任务状态 ${task.task.status} 及 ${matchingSteps.size} 个步骤",
            evidence = listOf(NewEvidence("evidence.current-step", definition.id, "从任务存储读取状态和验证摘要")), confirmedSuccess = true,
        )
    }
}

/** Small recursive-descent arithmetic evaluator; never evaluates generated source code. */
object SafeCalculator {
    fun evaluate(expression: String): Double {
        require(expression.length in 1..200) { "表达式长度无效" }
        val parser = Parser(expression)
        val result = parser.expression()
        parser.whitespace()
        require(parser.end()) { "表达式包含不支持的内容" }
        require(result.isFinite()) { "结果不是有限数值" }
        return result
    }

    private class Parser(private val source: String) {
        private var position = 0
        fun end() = position >= source.length
        fun whitespace() { while (!end() && source[position].isWhitespace()) position++ }
        fun expression(): Double {
            var value = term()
            while (true) {
                whitespace()
                value = when {
                    take('+') -> value + term()
                    take('-') -> value - term()
                    else -> return value
                }
            }
        }
        private fun term(): Double {
            var value = factor()
            while (true) {
                whitespace()
                value = when {
                    take('*') -> value * factor()
                    take('/') -> value / factor().also { require(it != 0.0) { "不能除以零" } }
                    else -> return value
                }
            }
        }
        private fun factor(): Double {
            whitespace()
            if (take('+')) return factor()
            if (take('-')) return -factor()
            if (take('(')) return expression().also { whitespace(); require(take(')')) { "缺少右括号" } }
            val start = position
            var dot = false
            while (!end()) {
                val c = source[position]
                if (c.isDigit()) position++
                else if (c == '.' && !dot) { dot = true; position++ }
                else break
            }
            require(position > start) { "需要数值" }
            return source.substring(start, position).toDoubleOrNull() ?: error("数值格式无效")
        }
        private fun take(char: Char): Boolean = if (!end() && source[position] == char) { position++; true } else false
    }
}

class VerificationEngine {
    fun verify(
        taskId: String,
        step: TaskStep,
        tool: ToolDefinition,
        execution: ToolExecutionResult,
        evidence: List<EvidenceReference>,
        approved: Boolean,
        nowMs: Long,
    ): VerificationResult {
        val outcomes = step.verificationRuleIds.map { ruleId ->
            val valid = when (ruleId) {
                "structure.non-empty-output" -> hasValidOutputShape(execution.output, tool.outputSchema)
                "status.confirmed-success" -> execution.confirmedSuccess
                "result.calculation-matches-expression" -> {
                    val expected = step.input["expected"]?.toDoubleOrNull()
                    val result = runCatching {
                        (JsonMini.parse(execution.output).asObject()?.entries?.get("value") as? JsonValue.JNumber)?.value
                    }.getOrNull()
                    val recalculated = runCatching { SafeCalculator.evaluate(step.input.getValue("expression")) }.getOrNull()
                    result != null && recalculated != null && abs(result - recalculated) <= 1e-9 &&
                        (expected == null || abs(result - expected) <= 1e-9)
                }
                "business.json-valid" -> jsonBoolean(execution.output, "valid") == true
                "business.text-rules" -> jsonBoolean(execution.output, "passed") == true
                "evidence.current-step" -> evidence.any {
                    it.taskId == taskId && it.stepId == step.id && it.source == tool.id && it.ruleId == ruleId
                }
                "manual.user-confirmation" -> approved
                else -> null
            }
            when {
                ruleId == "manual.user-confirmation" && !approved -> VerificationStatus.REQUIRES_APPROVAL
                valid == true -> VerificationStatus.VERIFIED
                valid == false -> VerificationStatus.FAILED
                else -> VerificationStatus.INCONCLUSIVE
            } to ruleId
        }
        val status = when {
            outcomes.any { it.first == VerificationStatus.FAILED } -> VerificationStatus.FAILED
            outcomes.any { it.first == VerificationStatus.REQUIRES_APPROVAL } -> VerificationStatus.REQUIRES_APPROVAL
            outcomes.any { it.first == VerificationStatus.INCONCLUSIVE } -> VerificationStatus.INCONCLUSIVE
            outcomes.isNotEmpty() -> VerificationStatus.VERIFIED
            else -> VerificationStatus.INCONCLUSIVE
        }
        val reason = when (status) {
            VerificationStatus.VERIFIED -> null
            VerificationStatus.FAILED -> "至少一项本地验证规则未满足：${outcomes.filter { it.first == VerificationStatus.FAILED }.joinToString { it.second }}"
            VerificationStatus.INCONCLUSIVE -> "证据不足或存在未注册的验证规则"
            VerificationStatus.REQUIRES_APPROVAL -> "该步骤需要用户确认"
        }
        val references = evidence.filter { it.taskId == taskId && it.stepId == step.id }.map { it.id }
        return VerificationResult(taskId, step.id, status, step.verificationRuleIds.joinToString(","), references, reason, nowMs)
    }

    private fun jsonBoolean(json: String, key: String): Boolean? = runCatching {
        (JsonMini.parse(json).asObject()?.entries?.get(key) as? JsonValue.JBool)?.value
    }.getOrNull()

    private fun hasValidOutputShape(json: String, schema: Map<String, TaskOutputField>): Boolean {
        if (json.isBlank()) return false
        if (schema.isEmpty()) return true
        val fields = runCatching { JsonMini.parse(json).asObject()?.entries }.getOrNull() ?: return false
        return schema.all { (name, field) ->
            val value = fields[name]
            if (value == null) !field.required
            else when (field.type) {
                TaskOutputType.STRING -> value is JsonValue.JString
                TaskOutputType.NUMBER -> value is JsonValue.JNumber && value.value.isFinite()
                TaskOutputType.BOOLEAN -> value is JsonValue.JBool
                TaskOutputType.OBJECT -> value is JsonValue.JObject
                TaskOutputType.ARRAY -> value is JsonValue.JArray
            }
        }
    }
}
