package com.tim.futureai.data.task

import com.tim.futureai.data.ai.AiProviderRegistry
import com.tim.futureai.data.settings.AiSettingsManager
import com.tim.futureai.domain.ai.AutomaticModelSelector
import com.tim.futureai.domain.ai.ModelCapabilityRegistry
import com.tim.futureai.domain.ai.ModelRoutingRequest
import com.tim.futureai.domain.ai.TaskClassifier
import com.tim.futureai.domain.usage.UsageRepository

/** Resolves task planning through the same configured provider catalog and deterministic Auto router as chat. */
class AiTaskPlanSourceFactory(
    private val settingsManager: AiSettingsManager,
    private val providerRegistry: AiProviderRegistry,
    private val usageRepository: UsageRepository,
) {
    fun create(goal: String, requestedModelId: String?, automaticByDefault: Boolean): AiTaskPlanSource {
        val profiles = settingsManager.profiles()
        val candidates = ModelCapabilityRegistry.fromProfiles(profiles)
        val activeProfileId = settingsManager.load().id
        val manualRoute = when {
            requestedModelId != null -> requestedModelId
            automaticByDefault -> null
            else -> activeProfileId
        }
        val route = AutomaticModelSelector.select(
            ModelRoutingRequest(
                prompt = goal,
                estimatedContextTokens = TaskClassifier.estimateTokens(goal) + 4_000,
                manualProfileId = manualRoute,
                preferredProfileId = activeProfileId,
            ),
            candidates,
        )
        val candidate = (route as? com.tim.futureai.domain.ai.ModelSelection.Selected)?.candidate
            ?: throw IllegalStateException("没有适合规划目标的启用模型；请检查模型设置和自动选模能力")
        val profile = settingsManager.loadProfile(candidate.profileId)
            ?: throw IllegalStateException("选中的服务商配置不可读取")
        val provider = providerRegistry.create(profile.providerProtocol) {
            settingsManager.configFor(profile.id, candidate.modelId)
        }
        val inputPrice = profile.inputUsdPerMillion.toDoubleOrNull()
            .takeIf { candidate.modelId == profile.model }
        val outputPrice = profile.outputUsdPerMillion.toDoubleOrNull()
            .takeIf { candidate.modelId == profile.model }
        return AiTaskPlanSource(
            provider = provider,
            profileId = candidate.profileId,
            providerName = candidate.providerName,
            model = candidate.modelId,
            inputUsdPerMillion = inputPrice,
            outputUsdPerMillion = outputPrice,
            usageRepository = usageRepository,
        )
    }
}
