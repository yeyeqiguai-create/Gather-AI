package com.tim.futureai.domain.arena

enum class ArenaStatus { RUNNING, DONE, FAILED }

enum class ArenaVerificationMethod { OBJECTIVE_CALCULATOR, OBJECTIVE_JSON, STRUCTURAL, NONE }

/** One model's independent answer in an arena round. */
data class ArenaEntry(
    val modelKey: String,
    val profileId: String,
    val answer: String?,
    val latencyMs: Long,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val costUsd: Double?,
    val error: String?,
)

/** Cross-review pass: agreements, conflicts and notes from the reviewer model. */
data class ArenaReview(
    val reviewerModelKey: String,
    val agreements: List<String>,
    val conflicts: List<String>,
    val notes: String,
    val latencyMs: Long,
)

data class ArenaVerification(
    val method: ArenaVerificationMethod,
    val passed: Boolean?,
    val detail: String,
    val perEntry: Map<String, Boolean>, // modelKey -> objectively passed
)

data class ArenaConclusion(
    val recommendedModelKey: String?,
    val confidence: String, // LOW | MEDIUM | HIGH — justified by basis text only
    val basis: String,
)

data class ArenaSession(
    val sessionId: String,
    val prompt: String,
    val category: String, // ModelTask name
    val createdAtMs: Long,
    val status: ArenaStatus,
    val entries: List<ArenaEntry>,
    val review: ArenaReview?,
    val verification: ArenaVerification?,
    val conclusion: ArenaConclusion?,
)

interface ArenaRepository {
    suspend fun save(session: ArenaSession)
    suspend fun byId(sessionId: String): ArenaSession?
    suspend fun all(): List<ArenaSession>
}

/**
 * Pure aggregation: numeric agreement across answers, objective checks, and a
 * confidence basis that only cites real evidence (never invented numbers).
 */
object ArenaAggregation {
    private val numberRegex = Regex("""(?<![0-9.])-?\d+(?:\.\d+)?""")

    fun detectObjective(prompt: String): ArenaVerificationMethod = when {
        Regex("""(?:计算|calculate|求值|evaluate)""", RegexOption.IGNORE_CASE).containsMatchIn(prompt) &&
            numberRegex.containsMatchIn(prompt) && Regex("""[+\-*/×÷]""").containsMatchIn(prompt) -> ArenaVerificationMethod.OBJECTIVE_CALCULATOR
        Regex("""JSON|json""").containsMatchIn(prompt) -> ArenaVerificationMethod.OBJECTIVE_JSON
        else -> ArenaVerificationMethod.STRUCTURAL
    }

    /** All distinct numbers appearing in an answer (for arithmetic agreement checks). */
    fun numbersIn(text: String): List<String> = numberRegex.findAll(text).map { it.value.trimStart('+') }.toList()

    fun structuralPerEntry(entries: List<ArenaEntry>): Map<String, Boolean> = entries.associate {
        it.modelKey to (!it.answer.isNullOrBlank() && it.error == null)
    }

    fun conclusion(
        entries: List<ArenaEntry>, review: ArenaReview?, verification: ArenaVerification?,
    ): ArenaConclusion {
        val okEntries = entries.filter { !it.answer.isNullOrBlank() && it.error == null }
        val recommended = when {
            verification?.passed == true && okEntries.isNotEmpty() ->
                okEntries.firstOrNull { verification.perEntry[it.modelKey] == true }?.modelKey
                    ?: review?.let { okEntries.firstOrNull { e -> e.modelKey == it.reviewerModelKey }?.modelKey }
                    ?: okEntries.minByOrNull { it.latencyMs }?.modelKey
            okEntries.isNotEmpty() -> okEntries.minByOrNull { it.latencyMs }?.modelKey
            else -> null
        }
        val conflictCount = review?.conflicts?.size ?: 0
        val (confidence, basis) = when {
            okEntries.isEmpty() -> "LOW" to "没有模型返回有效答案；仅记录错误信息。"
            verification != null && verification.method == ArenaVerificationMethod.OBJECTIVE_CALCULATOR && verification.passed == true ->
                "HIGH" to "客观计算器校验通过（${verification.detail}）；评审分歧 $conflictCount 项；有效答案 ${okEntries.size} 份。"
            verification != null && verification.method == ArenaVerificationMethod.OBJECTIVE_JSON && verification.passed == true ->
                "MEDIUM" to "JSON 结构校验通过；评审分歧 $conflictCount 项；有效答案 ${okEntries.size} 份。"
            verification?.method == ArenaVerificationMethod.STRUCTURAL ->
                "LOW" to "仅完成结构校验（非空且无错误），无客观验证器可用；评审分歧 $conflictCount 项。"
            else -> "LOW" to "缺少可引用的验证证据。"
        }
        return ArenaConclusion(recommended, confidence, basis)
    }
}
