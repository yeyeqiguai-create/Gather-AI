package com.tim.futureai.domain.ai

/** Builds non-secret, explicit capability records for each configured model endpoint. */
object ModelCapabilityRegistry {
    fun fromProfiles(profiles: List<AiSettings>): List<ModelCandidate> = profiles
        .filter { it.isAvailableForRouting }
        .flatMap { profile ->
            val modelIds = (listOf(profile.model) + profile.availableModels.split(Regex("[,\\r\\n]+")))
                .map(String::trim).filter(String::isNotBlank).distinct().take(33)
            modelIds.mapIndexed { index, modelId ->
                ModelCandidate(
                    id = if (index == 0) profile.id else routeId(profile.id, modelId),
                    profileId = profile.id,
                    providerName = profile.name,
                    modelId = modelId,
                    contextWindowTokens = profile.contextWindowTokens.toIntOrNull(),
                    tasks = profile.supportedTasks,
                    supportsImages = profile.supportsImages,
                    capabilityEvidence = profile.capabilityEvidence,
                    preference = profile.preference,
                )
            }
        }

    private fun routeId(profileId: String, modelId: String): String =
        "model:${profileId.length}:$profileId:${modelId.length}:$modelId"
}
