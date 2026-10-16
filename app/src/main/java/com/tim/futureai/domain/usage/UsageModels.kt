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

    fun estimatedUsd(inputRateByProfile: Map<String, Double?>, outputRateByProfile: Map<String, Double?>): Double? {
        val estimated = records.map { record ->
            val id = record.profileId ?: return null
            record.estimateUsd(inputRateByProfile[id], outputRateByProfile[id]) ?: return null
        }
        return estimated.sum()
    }
}

interface UsageRepository {
    suspend fun load(): UsageSnapshot
    suspend fun append(record: UsageRecord)
}
