package com.tim.futureai.feature.chat

import com.tim.futureai.domain.ai.CapabilityEvidence
import com.tim.futureai.domain.ai.ModelCandidate
import com.tim.futureai.domain.ai.ModelTask
import com.tim.futureai.domain.ai.ProviderAvailability

/** Non-secret display metadata passed from settings to the chat model selector. */
data class ChatModelChoice(
    val id: String,
    val displayName: String,
    val supportsImages: Boolean = false,
    val profileId: String = id,
    val providerName: String = displayName,
    val modelId: String = displayName,
    val contextWindowTokens: Int? = null,
    val tasks: Set<ModelTask> = emptySet(),
    val capabilityEvidence: CapabilityEvidence = CapabilityEvidence.UNKNOWN,
    val preference: Int = 0,
    val isAutomatic: Boolean = false,
) {
    fun candidate(availability: ProviderAvailability = ProviderAvailability.UNKNOWN) = ModelCandidate(
        id = id, profileId = profileId, providerName = providerName, modelId = modelId,
        availability = availability, contextWindowTokens = contextWindowTokens, tasks = tasks,
        supportsImages = supportsImages, capabilityEvidence = capabilityEvidence, preference = preference,
    )

    companion object {
        const val AUTO_ID = "__gather_auto_model__"
        const val DEFAULT_PROVIDER_ID = "__gather_default_provider__"
        fun automatic(canHandleImages: Boolean) = ChatModelChoice(
            id = AUTO_ID,
            displayName = "自动选模",
            supportsImages = canHandleImages,
            isAutomatic = true,
        )

        fun legacyDefault() = ChatModelChoice(
            id = DEFAULT_PROVIDER_ID,
            displayName = "默认配置",
            profileId = DEFAULT_PROVIDER_ID,
            providerName = "默认配置",
            modelId = "未知",
        )
    }
}
