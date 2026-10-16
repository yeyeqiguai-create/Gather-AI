package com.tim.futureai.data.settings

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.core.security.SecureKeyStore
import com.tim.futureai.core.security.SecureValueStore
import com.tim.futureai.domain.ai.AiChatConfig
import com.tim.futureai.domain.ai.AiSettings
import com.tim.futureai.domain.ai.CapabilityEvidence
import com.tim.futureai.domain.ai.ModelTask
import com.tim.futureai.domain.ai.ProviderProtocol
import com.tim.futureai.domain.ai.SettingsSaveResult

/** Stores all provider records as one SecureKeyStore-encrypted value; legacy single-provider keys migrate on first save. */
class AiSettingsManager(private val store: SecureValueStore) {
    fun profiles(): List<AiSettings> = readStoredProfiles().map { it.copy(apiKey = "", hasSavedApiKey = it.apiKey.isNotBlank()) }

    fun load(): AiSettings {
        val records = readStoredProfiles()
        val selected = store.getString(ACTIVE_PROFILE_KEY)
        val record = records.firstOrNull { it.id == selected && it.enabled }
            ?: records.firstOrNull { it.enabled } ?: records.firstOrNull()
        return (record ?: AiSettings()).copy(apiKey = "", hasSavedApiKey = record?.apiKey?.isNotBlank() == true)
    }

    fun loadProfile(id: String): AiSettings? = readStoredProfiles().firstOrNull { it.id == id }
        ?.let { it.copy(apiKey = "", hasSavedApiKey = it.apiKey.isNotBlank()) }

    fun configFor(id: String?, modelOverride: String? = null, allowDisabled: Boolean = false): AiChatConfig? {
        val records = readStoredProfiles()
        val record = if (id != null) records.firstOrNull { it.id == id }
        else records.firstOrNull { it.id == store.getString(ACTIVE_PROFILE_KEY) && it.enabled }
            ?: records.firstOrNull { it.enabled }
        val model = modelOverride?.trim()?.takeIf(String::isNotBlank)
        val allowedModels = record?.let { (listOf(it.model) + it.availableModels.split(Regex("[,\\r\\n]+"))).map(String::trim) }.orEmpty()
        return record?.takeIf { it.isConfigured && (it.enabled || allowDisabled) && (model == null || model in allowedModels) }?.let {
            AiChatConfig(it.baseUrl, model ?: it.model, it.apiKey)
        }
    }

    fun selectProfile(id: String): Boolean {
        if (readStoredProfiles().none { it.id == id && it.enabled }) return false
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
            contextWindowTokens = settings.contextWindowTokens.trim(),
            availableModels = settings.availableModels.split(Regex("[,\\r\\n]+"))
                .map(String::trim).filter(String::isNotBlank).filterNot { it == settings.model.trim() }
                .distinct().take(32).joinToString("\n"),
            supportedTasks = settings.supportedTasks.toSet(),
            preference = settings.preference.coerceIn(-20, 20),
        )
        val current = readStoredProfiles()
        val old = current.firstOrNull { it.id == normalized.id }
        val apiKey = normalized.apiKey.ifBlank { old?.apiKey.orEmpty() }
        val error = validate(normalized.copy(apiKey = apiKey, hasSavedApiKey = apiKey.isNotBlank()), apiKey.isNotBlank())
        if (error != null) return SettingsSaveResult.Invalid(error)
        val next = normalized.copy(apiKey = apiKey, hasSavedApiKey = false)
        val updated = if (old == null) current + next else current.map { if (it.id == next.id) next else it }
        if (!store.putString(PROFILES_KEY, encodeProfiles(updated))) return SettingsSaveResult.StorageError
        val activeId = store.getString(ACTIVE_PROFILE_KEY)
        val nextActive = when {
            next.enabled -> next.id
            activeId == next.id -> updated.firstOrNull { it.enabled }?.id
            else -> activeId
        }
        if (nextActive == null) store.remove(ACTIVE_PROFILE_KEY)
        else if (!store.putString(ACTIVE_PROFILE_KEY, nextActive)) return SettingsSaveResult.StorageError
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
            val replacement = next.firstOrNull { it.enabled }
            if (replacement != null) store.putString(ACTIVE_PROFILE_KEY, replacement.id)
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
                        enabled = (fields["enabled"] as? JsonValue.JBool)?.value ?: true,
                        contextWindowTokens = fields["contextWindowTokens"]?.asString().orEmpty(),
                        availableModels = fields["availableModels"]?.asString().orEmpty(),
                        supportedTasks = fields["supportedTasks"]?.asArray()?.items.orEmpty().mapNotNull { value ->
                            value.asString()?.let { runCatching { ModelTask.valueOf(it) }.getOrNull() }
                        }.toSet(),
                        capabilityEvidence = fields["capabilityEvidence"]?.asString()?.let {
                            runCatching { CapabilityEvidence.valueOf(it) }.getOrNull()
                        } ?: CapabilityEvidence.UNKNOWN,
                        preference = (fields["preference"] as? JsonValue.JNumber)?.value?.toInt() ?: 0,
                        providerProtocol = fields["providerProtocol"]?.asString()?.let {
                            runCatching { ProviderProtocol.valueOf(it) }.getOrNull()
                        } ?: ProviderProtocol.OPENAI_COMPATIBLE,
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
            "\"enabled\":" + profile.enabled,
            "\"contextWindowTokens\":" + JsonMini.quote(profile.contextWindowTokens),
            "\"availableModels\":" + JsonMini.quote(profile.availableModels),
            "\"supportedTasks\":[" + profile.supportedTasks.sortedBy { it.name }.joinToString(",") { JsonMini.quote(it.name) } + "]",
            "\"capabilityEvidence\":" + JsonMini.quote(profile.capabilityEvidence.name),
            "\"preference\":" + profile.preference,
            "\"providerProtocol\":" + JsonMini.quote(profile.providerProtocol.name),
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
            if (settings.contextWindowTokens.isNotBlank() && settings.contextWindowTokens.toIntOrNull()?.let { it > 0 } != true) return "上下文容量必须是正整数；未知时留空"
            if (settings.preference !in -20..20) return "模型偏好必须在 -20 至 20 之间"
            if (settings.inputUsdPerMillion.isNotBlank() && settings.inputUsdPerMillion.toDoubleOrNull()?.let { it >= 0 } != true) return "输入 Token 单价必须是非负数字"
            if (settings.outputUsdPerMillion.isNotBlank() && settings.outputUsdPerMillion.toDoubleOrNull()?.let { it >= 0 } != true) return "输出 Token 单价必须是非负数字"
            return null
        }

        private fun Map<String, JsonValue>.string(key: String): String = this[key]?.asString() ?: error("Invalid field")
    }
}
