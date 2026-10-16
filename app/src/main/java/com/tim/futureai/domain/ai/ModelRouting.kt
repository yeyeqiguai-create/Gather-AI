package com.tim.futureai.domain.ai

/** Tasks are descriptive capabilities, never benchmark scores. */
enum class ModelTask { GENERAL, CODING, REASONING, LONG_CONTEXT, ANALYSIS }

enum class CapabilityEvidence { VERIFIED, PROVIDER_DECLARED, UNKNOWN }

enum class ProviderAvailability { READY, UNKNOWN, RATE_LIMITED, UNAVAILABLE }

data class ModelCandidate(
    /** Unique route id (one provider may expose several models). */
    val id: String,
    val profileId: String,
    val providerName: String,
    val modelId: String,
    val enabled: Boolean = true,
    val availability: ProviderAvailability = ProviderAvailability.UNKNOWN,
    val contextWindowTokens: Int? = null,
    val tasks: Set<ModelTask> = emptySet(),
    val supportsImages: Boolean = false,
    val capabilityEvidence: CapabilityEvidence = CapabilityEvidence.UNKNOWN,
    /** User preference only; clamped by the selector to avoid dominating hard requirements. */
    val preference: Int = 0,
)

data class ModelRoutingRequest(
    val prompt: String,
    val estimatedContextTokens: Int,
    val requiresImages: Boolean = false,
    /** Non-null means the user explicitly selected a model: never silently substitute it. */
    val manualProfileId: String? = null,
    val preferredProfileId: String? = null,
)

sealed interface ModelSelection {
    data class Selected(
        val candidate: ModelCandidate,
        val task: ModelTask,
        val explanation: String,
        val rejectedCandidates: Map<String, String>,
    ) : ModelSelection

    data class Unavailable(val task: ModelTask, val reasons: Map<String, String>) : ModelSelection
}

object TaskClassifier {
    fun classify(prompt: String, requiresImages: Boolean = false): ModelTask {
        if (prompt.length >= LONG_TEXT_CHARS) return ModelTask.LONG_CONTEXT
        val text = prompt.lowercase()
        if (CODE_MARKERS.any(text::contains) || "```" in prompt) return ModelTask.CODING
        if (REASONING_MARKERS.any(text::contains)) return ModelTask.REASONING
        if (ANALYSIS_MARKERS.any(text::contains)) return ModelTask.ANALYSIS
        if (requiresImages) return ModelTask.ANALYSIS
        return ModelTask.GENERAL
    }

    fun estimateTokens(text: String): Int = ((text.length + 3) / 4).coerceAtLeast(1)

    private const val LONG_TEXT_CHARS = 12_000
    private val CODE_MARKERS = listOf("```", "代码", "编程", "debug", "stack trace", "refactor", "implement", "function", "class ")
    private val REASONING_MARKERS = listOf("证明", "推导", "逐步分析", "why does", "prove ", "derive ", "reason through")
    private val ANALYSIS_MARKERS = listOf("比较", "分析", "评估", "总结", "analyze", "compare", "evaluate", "summarize")
}

object AutomaticModelSelector {
    fun select(request: ModelRoutingRequest, candidates: List<ModelCandidate>): ModelSelection {
        val task = TaskClassifier.classify(request.prompt, request.requiresImages)
        val rejected = linkedMapOf<String, String>()
        val viable = candidates.filter { candidate ->
            val reason = when {
                !candidate.enabled -> "服务商已停用"
                request.manualProfileId == null && candidate.availability == ProviderAvailability.UNAVAILABLE -> "服务商当前不可用"
                request.manualProfileId == null && candidate.availability == ProviderAvailability.RATE_LIMITED -> "服务商当前受限"
                request.requiresImages && !candidate.supportsImages -> "不支持图片输入"
                candidate.contextWindowTokens != null && request.estimatedContextTokens > candidate.contextWindowTokens -> "上下文需求超过登记容量"
                else -> null
            }
            if (reason != null) rejected[candidate.id] = reason
            reason == null
        }
        val chosen = request.manualProfileId?.let { id ->
            viable.singleOrNull { it.id == id }
        } ?: if (request.manualProfileId == null) {
            viable.maxWithOrNull(
                compareBy<ModelCandidate> { score(it, task, request) }
                    .thenBy { it.id }
                    .thenBy { it.modelId },
            )
        } else null

        if (chosen == null) {
            if (request.manualProfileId != null && request.manualProfileId !in rejected) {
                rejected[request.manualProfileId] = "手动指定的模型不存在或未启用；不会自动切换"
            }
            return ModelSelection.Unavailable(task, rejected)
        }
        val explanation = if (request.manualProfileId != null) {
            "按会话手动指定 · ${chosen.providerName} / ${chosen.modelId}"
        } else {
            buildString {
                append("自动选择：适配${taskLabel(task)}")
                if (chosen.contextWindowTokens == null) append("；上下文容量未知")
                else append("；登记容量 ${chosen.contextWindowTokens} tokens")
                append("；能力来源 ${evidenceLabel(chosen.capabilityEvidence)}")
                append("；服务商 ${chosen.providerName}")
                val eliminated = rejected.mapNotNull { (id, reason) ->
                    candidates.firstOrNull { it.id == id }?.let { "${it.providerName}/${it.modelId}：$reason" }
                }
                if (eliminated.isNotEmpty()) append("；淘汰候选：${eliminated.joinToString("；")}")
            }
        }
        return ModelSelection.Selected(chosen, task, explanation, rejected)
    }

    private fun score(candidate: ModelCandidate, task: ModelTask, request: ModelRoutingRequest): Int =
        (if (task in candidate.tasks) 100 else if (candidate.tasks.isEmpty() || ModelTask.GENERAL in candidate.tasks) 10 else 0) +
            (candidate.preference.coerceIn(-20, 20)) +
            (if (candidate.id == request.preferredProfileId || candidate.profileId == request.preferredProfileId) 25 else 0) +
            (if (candidate.availability == ProviderAvailability.READY) 10 else 0) +
            (candidate.contextWindowTokens?.let { (it / 4096).coerceAtMost(20) } ?: 0) +
            (if (candidate.capabilityEvidence == CapabilityEvidence.UNKNOWN && task in candidate.tasks) -20 else 0)

    private fun taskLabel(task: ModelTask): String = when (task) {
        ModelTask.GENERAL -> "普通对话"
        ModelTask.CODING -> "编程"
        ModelTask.REASONING -> "逻辑推理"
        ModelTask.LONG_CONTEXT -> "长文本"
        ModelTask.ANALYSIS -> "复杂分析"
    }

    private fun evidenceLabel(evidence: CapabilityEvidence): String = when (evidence) {
        CapabilityEvidence.VERIFIED -> "已验证"
        CapabilityEvidence.PROVIDER_DECLARED -> "服务商声明"
        CapabilityEvidence.UNKNOWN -> "未知"
    }
}
