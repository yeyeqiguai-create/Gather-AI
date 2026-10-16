package com.tim.futureai.feature.settings

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.core.security.SecureKeyStore
import com.tim.futureai.core.security.SecureValueStore
import com.tim.futureai.domain.ai.AiChatConfig

/** One OpenAI-compatible endpoint. API keys are only present in memory while editing or creating a provider. */
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
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank() && (apiKey.isNotBlank() || hasSavedApiKey)

    companion object { const val DEFAULT_PROFILE_ID = "default" }
}

sealed interface SettingsSaveResult {
    data object Saved : SettingsSaveResult
    data class Invalid(val message: String) : SettingsSaveResult
    data object StorageError : SettingsSaveResult
}

/** Stores all provider records as one SecureKeyStore-encrypted value; legacy single-provider keys migrate on first save. */
class AiSettingsManager(private val store: SecureValueStore) {
    fun profiles(): List<AiSettings> = readStoredProfiles().map { it.copy(apiKey = "", hasSavedApiKey = it.apiKey.isNotBlank()) }

    fun load(): AiSettings {
        val records = readStoredProfiles()
        val selected = store.getString(ACTIVE_PROFILE_KEY)
        val record = records.firstOrNull { it.id == selected } ?: records.firstOrNull()
        return (record ?: AiSettings()).copy(apiKey = "", hasSavedApiKey = record?.apiKey?.isNotBlank() == true)
    }

    fun loadProfile(id: String): AiSettings? = readStoredProfiles().firstOrNull { it.id == id }
        ?.let { it.copy(apiKey = "", hasSavedApiKey = it.apiKey.isNotBlank()) }

    fun configFor(id: String?): AiChatConfig? {
        val records = readStoredProfiles()
        val record = if (id != null) records.firstOrNull { it.id == id }
        else records.firstOrNull { it.id == store.getString(ACTIVE_PROFILE_KEY) } ?: records.firstOrNull()
        return record?.takeIf { it.isConfigured }?.let { AiChatConfig(it.baseUrl, it.model, it.apiKey) }
    }

    fun selectProfile(id: String): Boolean {
        if (readStoredProfiles().none { it.id == id }) return false
        return store.putString(ACTIVE_PROFILE_KEY, id)
    }

    fun save(settings: AiSettings): SettingsSaveResult {
        val normalized = settings.copy(
            id = settings.id.trim().ifBlank { AiSettings.DEFAULT_PROFILE_ID },
            name = settings.name.trim().take(48).ifBlank { "AI 配置" },
            baseUrl = settings.baseUrl.trim().trimEnd('/'),
            model = settings.model.trim(),
            apiKey = settings.apiKey.trim(),
            inputUsdPerMillion = settings.inputUsdPerMillion.trim(),
            outputUsdPerMillion = settings.outputUsdPerMillion.trim(),
        )
        val current = readStoredProfiles()
        val old = current.firstOrNull { it.id == normalized.id }
        val apiKey = normalized.apiKey.ifBlank { old?.apiKey.orEmpty() }
        val error = validate(normalized.copy(apiKey = apiKey, hasSavedApiKey = apiKey.isNotBlank()), apiKey.isNotBlank())
        if (error != null) return SettingsSaveResult.Invalid(error)
        val next = normalized.copy(apiKey = apiKey, hasSavedApiKey = false)
        val updated = if (old == null) current + next else current.map { if (it.id == next.id) next else it }
        if (!store.putString(PROFILES_KEY, encodeProfiles(updated))) return SettingsSaveResult.StorageError
        if (!store.putString(ACTIVE_PROFILE_KEY, next.id)) return SettingsSaveResult.StorageError
        // Remove the previous one-profile ciphertext only after the encrypted profile bundle is safely stored.
        store.remove(SecureKeyStore.KEY_API)
        store.remove(SecureKeyStore.KEY_BASE_URL)
        store.remove(SecureKeyStore.KEY_MODEL)
        return SettingsSaveResult.Saved
    }

    fun deleteProfile(id: String): Boolean {
        val current = readStoredProfiles()
        if (current.none { it.id == id }) return false
        val next = current.filterNot { it.id == id }
        if (!store.putString(PROFILES_KEY, encodeProfiles(next))) return false
        if (store.getString(ACTIVE_PROFILE_KEY) == id) {
            if (next.isNotEmpty()) store.putString(ACTIVE_PROFILE_KEY, next.first().id)
            else store.remove(ACTIVE_PROFILE_KEY)
        }
        return true
    }

    private fun readStoredProfiles(): List<AiSettings> {
        val serialized = store.getString(PROFILES_KEY)
        if (!serialized.isNullOrBlank()) {
            runCatching {
                val root = JsonMini.parse(serialized).asObject()?.entries ?: error("Invalid provider profiles")
                return root["profiles"]?.asArray()?.items.orEmpty().map { value ->
                    val fields = value.asObject()?.entries ?: error("Invalid provider profile")
                    AiSettings(
                        id = fields.string("id"), name = fields.string("name"), baseUrl = fields.string("baseUrl"),
                        model = fields.string("model"), apiKey = fields.string("apiKey"),
                        supportsImages = (fields["supportsImages"] as? JsonValue.JBool)?.value ?: false,
                        inputUsdPerMillion = fields["inputUsdPerMillion"]?.asString().orEmpty(),
                        outputUsdPerMillion = fields["outputUsdPerMillion"]?.asString().orEmpty(),
                    )
                }
            }
        }
        val baseUrl = store.getString(SecureKeyStore.KEY_BASE_URL).orEmpty()
        val model = store.getString(SecureKeyStore.KEY_MODEL).orEmpty()
        val apiKey = store.getString(SecureKeyStore.KEY_API).orEmpty()
        return if (baseUrl.isBlank() && model.isBlank() && apiKey.isBlank()) emptyList()
        else listOf(AiSettings(baseUrl = baseUrl, model = model, apiKey = apiKey, id = LEGACY_PROFILE_ID, name = "旧配置"))
    }

    private fun encodeProfiles(profiles: List<AiSettings>): String = profiles.joinToString(",", prefix = "{\"profiles\":[", postfix = "]}") { profile ->
        "{" + listOf(
            "\"id\":" + JsonMini.quote(profile.id), "\"name\":" + JsonMini.quote(profile.name),
            "\"baseUrl\":" + JsonMini.quote(profile.baseUrl), "\"model\":" + JsonMini.quote(profile.model),
            "\"apiKey\":" + JsonMini.quote(profile.apiKey),
            "\"supportsImages\":" + profile.supportsImages,
            "\"inputUsdPerMillion\":" + JsonMini.quote(profile.inputUsdPerMillion),
            "\"outputUsdPerMillion\":" + JsonMini.quote(profile.outputUsdPerMillion),
        ).joinToString(",") + "}"
    }

    companion object {
        private const val PROFILES_KEY = "ai_provider_profiles_v1"
        private const val ACTIVE_PROFILE_KEY = "active_ai_provider_profile"
        private const val LEGACY_PROFILE_ID = "legacy"

        fun validate(settings: AiSettings, hasSavedApiKey: Boolean = settings.hasSavedApiKey): String? {
            if (settings.baseUrl.isBlank()) return "请输入 API Base URL"
            val url = runCatching { java.net.URI(settings.baseUrl.trim()) }.getOrNull()
            if (url == null || url.scheme !in setOf("http", "https") || url.host.isNullOrBlank()) {
                return "请输入有效的 HTTP 或 HTTPS Base URL"
            }
            if (settings.model.isBlank()) return "请输入 Model 名称"
            if (settings.apiKey.isBlank() && !hasSavedApiKey) return "请输入 API Key"
            if (settings.inputUsdPerMillion.isNotBlank() && settings.inputUsdPerMillion.toDoubleOrNull()?.let { it >= 0 } != true) return "输入 Token 单价必须是非负数字"
            if (settings.outputUsdPerMillion.isNotBlank() && settings.outputUsdPerMillion.toDoubleOrNull()?.let { it >= 0 } != true) return "输出 Token 单价必须是非负数字"
            return null
        }

        private fun Map<String, JsonValue>.string(key: String): String = this[key]?.asString() ?: error("Invalid field")
    }
}
