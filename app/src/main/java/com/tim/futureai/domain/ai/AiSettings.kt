package com.tim.futureai.domain.ai

/** Provider configuration metadata; API credentials are only populated transiently inside the secure data repository. */
data class AiSettings(
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val hasSavedApiKey: Boolean = false,
    val id: String = DEFAULT_PROFILE_ID,
    val name: String = "默认配置",
    val supportsImages: Boolean = false,
    val inputUsdPerMillion: String = "",
    val outputUsdPerMillion: String = "",
    val enabled: Boolean = true,
    val contextWindowTokens: String = "",
    val availableModels: String = "",
    val supportedTasks: Set<ModelTask> = emptySet(),
    val capabilityEvidence: CapabilityEvidence = CapabilityEvidence.UNKNOWN,
    val preference: Int = 0,
    val providerProtocol: ProviderProtocol = ProviderProtocol.OPENAI_COMPATIBLE,
    val lastConnectionTestResult: String? = null,
    val lastConnectionTestAt: String? = null,
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank() && (apiKey.isNotBlank() || hasSavedApiKey)
    val isAvailableForRouting: Boolean get() = enabled && isConfigured

    companion object { const val DEFAULT_PROFILE_ID = "default" }
}

sealed interface SettingsSaveResult {
    data object Saved : SettingsSaveResult
    data class Invalid(val message: String) : SettingsSaveResult
    data object StorageError : SettingsSaveResult
}
