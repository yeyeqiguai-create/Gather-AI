package com.tim.futureai.feature.settings

import com.tim.futureai.core.security.SecureKeyStore

data class AiSettings(
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
)

sealed interface SettingsSaveResult {
    data object Saved : SettingsSaveResult
    data class Invalid(val message: String) : SettingsSaveResult
    data object StorageError : SettingsSaveResult
}

/** Validation and persistence boundary; API keys are only passed to SecureKeyStore. */
class AiSettingsManager(private val store: SecureKeyStore) {
    fun load(): AiSettings = AiSettings(
        baseUrl = store.getString(SecureKeyStore.KEY_BASE_URL).orEmpty(),
        model = store.getString(SecureKeyStore.KEY_MODEL).orEmpty(),
        apiKey = store.getString(SecureKeyStore.KEY_API).orEmpty(),
    )

    fun save(settings: AiSettings): SettingsSaveResult {
        val normalized = settings.copy(
            baseUrl = settings.baseUrl.trim().trimEnd('/'),
            model = settings.model.trim(),
            apiKey = settings.apiKey.trim(),
        )
        val error = validate(normalized)
        if (error != null) return SettingsSaveResult.Invalid(error)
        if (!store.putString(SecureKeyStore.KEY_BASE_URL, normalized.baseUrl) ||
            !store.putString(SecureKeyStore.KEY_MODEL, normalized.model) ||
            !store.putString(SecureKeyStore.KEY_API, normalized.apiKey)
        ) return SettingsSaveResult.StorageError
        return SettingsSaveResult.Saved
    }

    companion object {
        fun validate(settings: AiSettings): String? {
            if (settings.baseUrl.isBlank()) return "请输入 API Base URL"
            val url = runCatching { java.net.URI(settings.baseUrl.trim()) }.getOrNull()
            if (url == null || url.scheme !in setOf("http", "https") || url.host.isNullOrBlank()) {
                return "请输入有效的 HTTP 或 HTTPS Base URL"
            }
            if (settings.model.isBlank()) return "请输入 Model 名称"
            if (settings.apiKey.isBlank()) return "请输入 API Key"
            return null
        }
    }
}
