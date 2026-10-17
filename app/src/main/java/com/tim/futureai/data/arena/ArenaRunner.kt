package com.tim.futureai.data.arena

import com.tim.futureai.core.common.AppResult
import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.data.ai.AiProviderRegistry
import com.tim.futureai.data.settings.AiSettingsManager
import com.tim.futureai.domain.ai.ModelTask
import com.tim.futureai.domain.arena.ArenaAggregation
import com.tim.futureai.domain.arena.ArenaConclusion
import com.tim.futureai.domain.arena.ArenaEntry
import com.tim.futureai.domain.arena.ArenaRepository
import com.tim.futureai.domain.arena.ArenaReview
import com.tim.futureai.domain.arena.ArenaSession
import com.tim.futureai.domain.arena.ArenaStatus
import com.tim.futureai.domain.arena.ArenaVerification
import com.tim.futureai.domain.arena.ArenaVerificationMethod
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.MessageRole
import com.tim.futureai.domain.reputation.ReputationStore
import com.tim.futureai.domain.task.SafeCalculator
import com.tim.futureai.domain.usage.UsageRecord
import com.tim.futureai.domain.usage.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Runs one arena round for real: parallel independent answers via the actual
 * provider stack, a cross-review pass, objective verification where the prompt
 * allows it, usage attribution, and reputation feedback per model.
 */
class ArenaRunner(
    private val settingsManager: AiSettingsManager,
    private val providerRegistry: AiProviderRegistry,
    private val usageRepository: UsageRepository,
    private val reputationStore: ReputationStore,
    private val arenaRepository: ArenaRepository,
    private val clock: () -> Long,
    private val idSeed: () -> String = { UUID.randomUUID().toString().take(8) },
) {
    data class Selection(val profileId: String)

    suspend fun run(prompt: String, profileIds: List<String>, category: ModelTask): ArenaSession {
        val sessionId = "as-${clock()}-${idSeed()}"
        var session = ArenaSession(
            sessionId = sessionId, prompt = prompt, category = category.name,
            createdAtMs = clock(), status = ArenaStatus.RUNNING, entries = emptyList(),
            review = null, verification = null, conclusion = null,
        )
        arenaRepository.save(session)

        val entries = coroutineScope {
            profileIds.map { profileId ->
                async(Dispatchers.IO) { answerFor(sessionId, profileId, prompt) }
            }.awaitAll()
        }
        session = session.copy(entries = entries)
        arenaRepository.save(session)

        // Cross-review by the first model that produced a valid answer.
        val reviewer = entries.firstOrNull { !it.answer.isNullOrBlank() && it.error == null }
        val review = reviewer?.let { entry ->
            val reviewPrompt = buildReviewPrompt(prompt, entries)
            val startedAt = clock()
            val provider = providerRegistry.create(
                settingsManager.loadProfile(entry.profileId)?.providerProtocol
                    ?: com.tim.futureai.domain.ai.ProviderProtocol.OPENAI_COMPATIBLE,
            ) { settingsManager.configFor(entry.profileId) }
            val result = provider.complete(listOf(ChatMessage(sessionId = sessionId, role = MessageRole.USER, content = reviewPrompt)))
            when (result) {
                is AppResult.Success -> parseReview(entry.modelKey, result.data, clock() - startedAt)
                is AppResult.Failure -> ArenaReview(
                    reviewerModelKey = entry.modelKey, agreements = emptyList(), conflicts = emptyList(),
                    notes = "评审失败：${result.error}", latencyMs = clock() - startedAt,
                )
            }
        }
        session = session.copy(review = review)
        arenaRepository.save(session)

        val verification = verify(prompt, entries)
        session = session.copy(verification = verification)
        val conclusion = ArenaAggregation.conclusion(entries, review, verification)
        session = session.copy(conclusion = conclusion, status = ArenaStatus.DONE)
        arenaRepository.save(session)

        // Reputation feedback per model (arena source), and usage attribution.
        for (entry in entries) {
            val profile = settingsManager.loadProfile(entry.profileId)
            val objectivePassed = verification?.perEntry?.get(entry.modelKey)
            val success = entry.error == null && !entry.answer.isNullOrBlank() && objectivePassed != false
            reputationStore.record(
                modelKey = "${profile?.name ?: entry.profileId} · ${profile?.model.orEmpty()}",
                category = category,
                success = success,
                failureType = when {
                    entry.error != null -> "request_error"
                    objectivePassed == false -> "objective_mismatch"
                    else -> null
                },
                latencyMs = entry.latencyMs,
                costUsd = entry.costUsd,
                reason = if (success) "Arena 客观校验通过或无客观校验" else "Arena 校验未通过或请求失败",
                source = "ARENA",
            )
            usageRepository.append(
                UsageRecord(
                    profileId = entry.profileId, providerName = profile?.name ?: "unknown",
                    model = profile?.model.orEmpty(), durationMs = entry.latencyMs,
                    errorType = entry.error?.let { "arena_error" },
                    promptTokens = entry.promptTokens, completionTokens = entry.completionTokens,
                    sessionId = "arena:$sessionId",
                ),
            )
        }
        return session
    }

    private suspend fun answerFor(sessionId: String, profileId: String, prompt: String): ArenaEntry {
        val startedAt = clock()
        val profile = settingsManager.loadProfile(profileId)
        var promptTokens: Int? = null
        var completionTokens: Int? = null
        if (profile == null || !profile.isAvailableForRouting) {
            return ArenaEntry("${profile?.name ?: profileId} · ${profile?.model.orEmpty()}", profileId, null, 0L, null, null, null, "配置不完整或已停用")
        }
        val provider = providerRegistry.create(profile.providerProtocol) { settingsManager.configFor(profileId) }
        val result = provider.streamWithUsage(
            listOf(ChatMessage(sessionId = sessionId, role = MessageRole.USER, content = prompt)),
            onPhase = {}, onDelta = {},
        ) { usage ->
            promptTokens = usage.promptTokens
            completionTokens = usage.completionTokens
        }
        val latency = clock() - startedAt
        return when (result) {
            is AppResult.Success -> {
                val cost = estimateCostUsd(profile.inputUsdPerMillion, profile.outputUsdPerMillion, promptTokens, completionTokens)
                ArenaEntry(
                    modelKey = "${profile.name} · ${profile.model}", profileId = profileId,
                    answer = result.data, latencyMs = latency, promptTokens = promptTokens,
                    completionTokens = completionTokens, costUsd = cost, error = null,
                )
            }
            is AppResult.Failure -> ArenaEntry(
                modelKey = "${profile.name} · ${profile.model}", profileId = profileId,
                answer = null, latencyMs = latency, promptTokens = promptTokens,
                completionTokens = completionTokens, costUsd = null, error = result.error.toString(),
            )
        }
    }

    private fun estimateCostUsd(inputPerMillion: String, outputPerMillion: String, promptTokens: Int?, completionTokens: Int?): Double? {
        val inPrice = inputPerMillion.toDoubleOrNull() ?: return null
        val outPrice = outputPerMillion.toDoubleOrNull() ?: return null
        val pt = promptTokens ?: return null
        val ct = completionTokens ?: return null
        return pt / 1_000_000.0 * inPrice + ct / 1_000_000.0 * outPrice
    }

    private fun buildReviewPrompt(prompt: String, entries: List<ArenaEntry>): String = buildString {
        append("以下是同一问题的多个模型答案。请客观比较：先列出 agreement（一致的关键结论），再列出 conflict（实质分歧），最后给一句 notes。\n")
        append("严格输出 JSON：{\"agreements\":[\"...\"],\"conflicts\":[\"...\"],\"notes\":\"...\"}\n\n问题：")
        append(prompt.take(1200)).append('\n')
        entries.filter { !it.answer.isNullOrBlank() }.forEachIndexed { index, entry ->
            append("\n[答案${index + 1} · ${entry.modelKey}]\n").append(entry.answer!!.take(1500)).append('\n')
        }
    }

    private fun parseReview(reviewerModelKey: String, text: String, latencyMs: Long): ArenaReview {
        val jsonText = Regex("""\{[\s\S]*\}""").find(text)?.value ?: text
        val parsed = runCatching { JsonMini.parse(jsonText).asObject() }.getOrNull()
        fun list(key: String): List<String> {
            val value = parsed?.entries?.get(key) ?: return emptyList()
            val array = value as? JsonValue.JArray ?: return emptyList()
            return array.items.mapNotNull { item -> item.asString() }
        }
        return if (parsed != null) ArenaReview(
            reviewerModelKey = reviewerModelKey,
            agreements = list("agreements"), conflicts = list("conflicts"),
            notes = parsed.entries["notes"]?.asString().orEmpty(), latencyMs = latencyMs,
        ) else ArenaReview(
            reviewerModelKey = reviewerModelKey, agreements = emptyList(), conflicts = emptyList(),
            notes = text.take(600), latencyMs = latencyMs,
        )
    }

    private fun verify(prompt: String, entries: List<ArenaEntry>): ArenaVerification {
        val method = ArenaAggregation.detectObjective(prompt)
        val perEntry = mutableMapOf<String, Boolean>()
        var passed: Boolean? = null
        var detail = ""
        when (method) {
            ArenaVerificationMethod.OBJECTIVE_CALCULATOR -> {
                val expression = Regex("""[-0-9(][\d\s+\-*/×÷().]+""").findAll(prompt)
                    .map { it.value.trim() }.maxByOrNull { it.length }
                val expected = expression?.let { runCatching { SafeCalculator.evaluate(it) }.getOrNull() }
                if (expected == null) {
                    return ArenaVerification(ArenaVerificationMethod.STRUCTURAL, null, "未能从问题中提取可计算表达式，回退结构校验", ArenaAggregation.structuralPerEntry(entries))
                }
                perEntry.putAll(entries.associate { entry ->
                    val numbers = entry.answer?.let { ArenaAggregation.numbersIn(it) }.orEmpty()
                    entry.modelKey to (entry.error == null && numbers.any { it.toDoubleOrNull() == expected })
                })
                passed = perEntry.values.any { it }
                detail = "期望值 $expected（表达式 $expression）"
            }
            ArenaVerificationMethod.OBJECTIVE_JSON -> {
                perEntry.putAll(entries.associate { entry ->
                    val jsonCandidate = entry.answer?.let { answer ->
                        Regex("""\{[\s\S]*\}|\[[\s\S]*\]""").find(answer)?.value
                    }
                    val valid = jsonCandidate != null && runCatching { JsonMini.parse(jsonCandidate) }.isSuccess
                    entry.modelKey to (entry.error == null && valid)
                })
                passed = perEntry.values.any { it }
                detail = "答案包含可解析的 JSON 结构"
            }
            else -> {
                perEntry.putAll(ArenaAggregation.structuralPerEntry(entries))
                passed = null
                detail = "无客观验证器；仅结构校验"
            }
        }
        return ArenaVerification(method, passed, detail, perEntry)
    }
}
