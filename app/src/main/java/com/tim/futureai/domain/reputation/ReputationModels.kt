package com.tim.futureai.domain.reputation

import com.tim.futureai.domain.ai.ModelTask

/** Per (model, task category) reputation; never a single global score. */
data class ReputationRecord(
    val modelKey: String,
    val category: ModelTask,
    val success: Int,
    val failure: Int,
    val failureTypes: Map<String, Int>,
    val totalLatencyMs: Long,
    val totalCostUsd: Double,
    val updatedAtMs: Long,
    val history: List<ReputationChange>, // bounded, newest first
)

data class ReputationChange(
    val atMs: Long,
    val success: Boolean,
    val reason: String,
    val source: String, // TASK | ARENA | HEALTH
    val detail: String,
)

data class ReputationView(
    val modelKey: String,
    val category: ModelTask,
    val sampleSize: Int,
    val sufficientSamples: Boolean,
    val successRate: Double?, // null when insufficient — never fabricated
    val failure: Int,
    val failureTypes: Map<String, Int>,
    val averageLatencyMs: Long?,
    val totalCostUsd: Double,
    val history: List<ReputationChange>,
)

interface ReputationRepository {
    suspend fun record(modelKey: String, category: ModelTask): ReputationRecord?
    suspend fun upsert(record: ReputationRecord)
    suspend fun all(): List<ReputationRecord>
    suspend fun clear(modelKey: String?, category: ModelTask?)
}

object ReputationPolicy {
    const val MIN_SAMPLES = 5
    const val MAX_HISTORY = 30

    fun viewOf(record: ReputationRecord?): ReputationView? {
        record ?: return null
        val sample = record.success + record.failure
        return ReputationView(
            modelKey = record.modelKey, category = record.category, sampleSize = sample,
            sufficientSamples = sample >= MIN_SAMPLES,
            successRate = if (sample >= MIN_SAMPLES) record.success.toDouble() / sample else null,
            failure = record.failure,
            failureTypes = record.failureTypes, averageLatencyMs = if (sample > 0) record.totalLatencyMs / sample else null,
            totalCostUsd = record.totalCostUsd, history = record.history,
        )
    }

    /**
     * Documented routing weight in [0,1]: verified success dominates, latency
     * and recorded cost matter, availability is handled before this runs.
     * With insufficient samples this returns a neutral 0.5 — never a fake score.
     */
    fun routingWeight(view: ReputationView, latencyBudgetMs: Long): Double {
        val rate = view.successRate ?: return 0.5
        val latencyScore = view.averageLatencyMs?.let { avg ->
            if (latencyBudgetMs <= 0) 0.5 else (latencyBudgetMs.toDouble() / (latencyBudgetMs + avg)).coerceIn(0.0, 1.0)
        } ?: 0.5
        return (rate * 0.6 + latencyScore * 0.25 + 0.15).coerceIn(0.0, 1.0)
    }
}

class ReputationStore(private val repository: ReputationRepository, private val clock: () -> Long) {
    suspend fun record(
        modelKey: String, category: ModelTask, success: Boolean,
        failureType: String?, latencyMs: Long, costUsd: Double?, reason: String, source: String,
    ) {
        val existing = repository.record(modelKey, category)
        val change = ReputationChange(clock(), success, reason, source, failureType ?: "")
        val updated = ReputationRecord(
            modelKey = modelKey, category = category,
            success = existing?.success?.plus(if (success) 1 else 0) ?: if (success) 1 else 0,
            failure = existing?.failure?.plus(if (success) 0 else 1) ?: if (success) 0 else 1,
            failureTypes = buildMap {
                existing?.failureTypes?.let(::putAll)
                if (!success && !failureType.isNullOrBlank()) {
                    put(failureType, (get(failureType) ?: 0) + 1)
                }
            },
            totalLatencyMs = (existing?.totalLatencyMs ?: 0L) + latencyMs.coerceAtLeast(0),
            totalCostUsd = (existing?.totalCostUsd ?: 0.0) + (costUsd ?: 0.0),
            updatedAtMs = clock(),
            history = (listOf(change) + (existing?.history ?: emptyList())).take(ReputationPolicy.MAX_HISTORY),
        )
        repository.upsert(updated)
    }

    suspend fun view(modelKey: String, category: ModelTask): ReputationView? =
        ReputationPolicy.viewOf(repository.record(modelKey, category))

    suspend fun allViews(): List<ReputationView> = repository.all().mapNotNull { ReputationPolicy.viewOf(it) }

    suspend fun reset(modelKey: String?, category: ModelTask?) = repository.clear(modelKey, category)
}
