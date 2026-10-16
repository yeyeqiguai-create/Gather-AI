package com.tim.futureai.domain.usage

import com.tim.futureai.domain.ai.AiTokenUsage
import java.time.Instant

data class UsageRecord(
    val profileId: String?,
    val providerName: String,
    val model: String,
    val durationMs: Long,
    val errorType: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val recordedAt: String = Instant.now().toString(),
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
    val requestCount: Int get() = records.size
    val errorCount: Int get() = records.count { it.errorType != null }
    val knownTokenCount: Int get() = records.count { it.tokenUsage != null }
    val unknownTokenCount: Int get() = records.size - knownTokenCount
    val promptTokens: Long get() = records.sumOf { it.promptTokens?.toLong() ?: 0L }
    val completionTokens: Long get() = records.sumOf { it.completionTokens?.toLong() ?: 0L }
    val totalDurationMs: Long get() = records.sumOf { it.durationMs.coerceAtLeast(0) }

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
}
