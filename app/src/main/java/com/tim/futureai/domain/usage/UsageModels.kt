package com.tim.futureai.domain.usage

import com.tim.futureai.domain.ai.AiTokenUsage
import java.time.Instant
import java.time.Duration

enum class UsageRange { LAST_7_DAYS, LAST_30_DAYS, ALL }

data class UsageRecord(
    val profileId: String?,
    val providerName: String,
    val model: String,
    val durationMs: Long,
    val errorType: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val recordedAt: String = Instant.now().toString(),
    /** Null for legacy records written before session attribution was added. */
    val sessionId: String? = null,
    /** Task planning/execution attribution; null for ordinary chat. */
    val taskId: String? = null,
    val taskStepId: String? = null,
) {
    val tokenUsage: AiTokenUsage? get() = if (promptTokens != null && completionTokens != null) AiTokenUsage(promptTokens, completionTokens) else null

    fun estimateUsd(inputUsdPerMillion: Double?, outputUsdPerMillion: Double?): Double? {
        val usage = tokenUsage ?: return null
        val inputPrice = inputUsdPerMillion ?: return null
        val outputPrice = outputUsdPerMillion ?: return null
        if (inputPrice < 0 || outputPrice < 0) return null
        return (usage.promptTokens * inputPrice + usage.completionTokens * outputPrice) / 1_000_000.0
    }
}

data class UsageSnapshot(val records: List<UsageRecord> = emptyList()) {
    fun within(range: UsageRange, now: Instant = Instant.now()): UsageSnapshot {
        val duration = when (range) {
            UsageRange.LAST_7_DAYS -> Duration.ofDays(7)
            UsageRange.LAST_30_DAYS -> Duration.ofDays(30)
            UsageRange.ALL -> return this
        }
        val cutoff = now.minus(duration)
        return UsageSnapshot(records.filter { record ->
            runCatching { !Instant.parse(record.recordedAt).isBefore(cutoff) }.getOrDefault(false)
        })
    }

    val requestCount: Int get() = records.size
    val errorCount: Int get() = records.count { it.errorType != null }
    val knownTokenCount: Int get() = records.count { it.tokenUsage != null }
    val unknownTokenCount: Int get() = records.size - knownTokenCount
    val promptTokens: Long get() = records.sumOf { it.promptTokens?.toLong() ?: 0L }
    val completionTokens: Long get() = records.sumOf { it.completionTokens?.toLong() ?: 0L }
    val totalDurationMs: Long get() = records.sumOf { it.durationMs.coerceAtLeast(0) }

    fun bySession(): List<SessionUsage> = records.groupBy { it.sessionId }
        .map { (sessionId, grouped) ->
            SessionUsage(
                sessionId = sessionId,
                requestCount = grouped.size,
                errorCount = grouped.count { it.errorType != null },
                knownUsageCount = grouped.count { it.tokenUsage != null },
                promptTokens = grouped.sumOf { it.promptTokens?.toLong() ?: 0L },
                completionTokens = grouped.sumOf { it.completionTokens?.toLong() ?: 0L },
                totalDurationMs = grouped.sumOf { it.durationMs.coerceAtLeast(0) },
                lastRequestAt = grouped.maxOfOrNull { it.recordedAt }.orEmpty(),
            )
        }
        .sortedByDescending { it.lastRequestAt }

    fun byProviderAndModel(prices: Map<UsagePriceKey, ModelUsagePrice> = emptyMap()): List<ProviderModelUsage> = records.groupBy { it.providerName to it.model }
        .map { (key, grouped) ->
            val estimates = grouped.map { record ->
                val profileId = record.profileId
                val price = profileId?.let { prices[UsagePriceKey(it, record.model)] }
                price?.let { record.estimateUsd(it.inputUsdPerMillion, it.outputUsdPerMillion) }
            }
            ProviderModelUsage.from(key, grouped, estimates.takeIf { it.all { value -> value != null } }?.sumOf { it!! })
        }
        .sortedWith(compareByDescending<ProviderModelUsage> { it.requestCount }.thenBy { it.providerName }.thenBy { it.model })

    fun estimatedUsdByModel(prices: Map<UsagePriceKey, ModelUsagePrice>): Double? =
        byProviderAndModel(prices).takeIf { groups -> groups.sumOf { it.requestCount } == requestCount && groups.all { it.estimatedUsd != null } }
            ?.sumOf { it.estimatedUsd!! }
}

data class SessionUsage(
    val sessionId: String?,
    val requestCount: Int,
    val errorCount: Int,
    val knownUsageCount: Int,
    val promptTokens: Long,
    val completionTokens: Long,
    val totalDurationMs: Long,
    val lastRequestAt: String,
)

data class UsagePriceKey(val profileId: String, val model: String)

data class ModelUsagePrice(val inputUsdPerMillion: Double?, val outputUsdPerMillion: Double?)

data class ProviderModelUsage(
    val providerName: String,
    val model: String,
    val requestCount: Int,
    val errorCount: Int,
    val knownUsageCount: Int,
    val unknownUsageCount: Int,
    val promptTokens: Long,
    val completionTokens: Long,
    val totalDurationMs: Long,
    /** Null means one or more calls lacked real usage or configured prices. */
    val estimatedUsd: Double?,
) {
    companion object {
        internal fun from(key: Pair<String, String>, records: List<UsageRecord>, estimatedUsd: Double?) = ProviderModelUsage(
            providerName = key.first,
            model = key.second,
            requestCount = records.size,
            errorCount = records.count { it.errorType != null },
            knownUsageCount = records.count { it.tokenUsage != null },
            unknownUsageCount = records.count { it.tokenUsage == null },
            promptTokens = records.sumOf { it.promptTokens?.toLong() ?: 0L },
            completionTokens = records.sumOf { it.completionTokens?.toLong() ?: 0L },
            totalDurationMs = records.sumOf { it.durationMs.coerceAtLeast(0) },
            estimatedUsd = estimatedUsd,
        )
    }
}

interface UsageRepository {
    suspend fun load(): UsageSnapshot
    suspend fun append(record: UsageRecord)
    suspend fun clear() {}
}
