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
import java.time.Instant

/** Stores all provider records as one SecureKeyStore-encrypted value; legacy single-provider keys migrate on first save. */
class AiSettingsManager(private val store: SecureValueStore) {
    sealed interface ProfileImportResult {
        data class Success(val settings: AiSettings) : ProfileImportResult
        data class BundleSuccess(val profiles: List<AiSettings>, val skippedDuplicates: Int) : ProfileImportResult
        data class Invalid(val message: String) : ProfileImportResult
        data object StorageError : ProfileImportResult
    }

    fun profiles(): List<AiSettings> = readStoredProfiles().map { it.copy(apiKey = "", hasSavedApiKey = it.apiKey.isNotBlank()) }

    fun migrationStatus(): String {
        if (store.contains(PROFILES_KEY)) return if (profileBundleReadable()) "新版加密 Provider 配置包可读取" else "加密 Provider 配置无法解析；已阻止覆盖以保护原数据"
        if (store.contains(SecureKeyStore.KEY_BASE_URL) || store.contains(SecureKeyStore.KEY_MODEL) || store.contains(SecureKeyStore.KEY_API)) {
            return "检测到旧版单 Provider 配置；首次保存时迁移"
        }
        return "无待迁移的旧版 Provider 配置"
    }

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
            AiChatConfig(
                baseUrl = it.baseUrl,
                model = model ?: it.model,
                apiKey = it.apiKey,
                temperature = it.temperature.toDoubleOrNull(),
                maxOutputTokens = it.maxOutputTokens.toIntOrNull(),
                systemInstruction = it.systemInstruction,
                customHeaders = parseCustomHeaders(it.customHeaders).getOrDefault(emptyMap()),
                connectTimeoutMs = it.connectTimeoutSeconds.toIntOrNull()?.times(1000),
                readTimeoutMs = it.readTimeoutSeconds.toIntOrNull()?.times(1000),
            )
        }
    }

    fun selectProfile(id: String): Boolean {
        if (readStoredProfiles().none { it.id == id && it.enabled }) return false
        return store.putString(ACTIVE_PROFILE_KEY, id)
    }

    /** Creates a separate editable profile while retaining its credential only inside the encrypted bundle. */
    fun duplicateProfile(id: String): AiSettings? {
        if (!profileBundleReadable()) return null
        val current = readStoredProfiles()
        val source = current.firstOrNull { it.id == id } ?: return null
        val duplicate = source.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = (source.name.take(42) + " 副本").take(48),
            lastConnectionTestResult = null,
            lastConnectionTestAt = null,
        )
        if (!store.putString(PROFILES_KEY, encodeProfiles(current + duplicate))) return null
        return duplicate.copy(apiKey = "", hasSavedApiKey = duplicate.apiKey.isNotBlank())
    }

    /** Portable single-profile JSON. Secrets and connection-test history are intentionally excluded. */
    fun exportProfile(id: String): String? {
        val profile = readStoredProfiles().firstOrNull { it.id == id } ?: return null
        return encodePortableProfile(profile)
    }

    /** Exports all current profile metadata into one secret-free bundle. */
    fun exportProfilesBundle(): String? {
        val profiles = readStoredProfiles()
        if (profiles.isEmpty()) return null
        return "{" + listOf(
            "\"schema\":" + JsonMini.quote(PROFILE_BUNDLE_SCHEMA),
            "\"formatVersion\":1",
            "\"profiles\":[" + profiles.joinToString(",") { encodePortableProfile(it) } + "]",
        ).joinToString(",") + "}"
    }

    private fun encodePortableProfile(profile: AiSettings): String = "{" + listOf(
            "\"schema\":" + JsonMini.quote(PROFILE_EXPORT_SCHEMA),
            "\"formatVersion\":1",
            "\"name\":" + JsonMini.quote(profile.name),
            "\"baseUrl\":" + JsonMini.quote(profile.baseUrl),
            "\"model\":" + JsonMini.quote(profile.model),
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
            "\"temperature\":" + JsonMini.quote(profile.temperature),
            "\"maxOutputTokens\":" + JsonMini.quote(profile.maxOutputTokens),
            "\"systemInstruction\":" + JsonMini.quote(profile.systemInstruction),
            "\"connectTimeoutSeconds\":" + JsonMini.quote(profile.connectTimeoutSeconds),
            "\"readTimeoutSeconds\":" + JsonMini.quote(profile.readTimeoutSeconds),
        ).joinToString(",") + "}"

    /** Decodes a profile into an unsaved draft; callers must collect a new API Key before saving. */
    fun importProfileDraft(serialized: String): ProfileImportResult {
        if (serialized.length > MAX_PROFILE_IMPORT_CHARS) return ProfileImportResult.Invalid("配置文件超过 1 MB，无法导入")
        val schema = runCatching { JsonMini.parse(serialized).asObject()?.entries?.get("schema")?.asString() }.getOrNull()
        if (schema == PROFILE_BUNDLE_SCHEMA) return importProfilesBundle(serialized)
        return runCatching {
            val fields = JsonMini.parse(serialized).asObject()?.entries ?: error("JSON 根节点必须是对象")
            if (fields["schema"]?.asString() != PROFILE_EXPORT_SCHEMA) error("这不是 Gather AI 服务商配置文件")
            if ((fields["formatVersion"] as? JsonValue.JNumber)?.value?.toInt() != 1) error("不支持此配置文件版本")
            if ("apiKey" in fields) error("配置文件不得包含 API Key；请使用不含密钥的导出文件")
            if ("customHeaders" in fields) error("配置文件不得包含自定义请求头；请在本机重新填写，避免导入凭据")
            fun string(key: String, fallback: String = ""): String = fields[key]?.asString() ?: fallback
            fun bool(key: String, fallback: Boolean): Boolean = (fields[key] as? JsonValue.JBool)?.value ?: fallback
            val protocol = string("providerProtocol", ProviderProtocol.OPENAI_COMPATIBLE.name).let {
                runCatching { ProviderProtocol.valueOf(it) }.getOrNull() ?: error("服务商协议无效")
            }
            val tasks = fields["supportedTasks"]?.asArray()?.items.orEmpty().mapNotNull { value ->
                value.asString()?.let { runCatching { ModelTask.valueOf(it) }.getOrNull() }
            }.toSet()
            val evidence = string("capabilityEvidence", CapabilityEvidence.UNKNOWN.name).let {
                runCatching { CapabilityEvidence.valueOf(it) }.getOrNull() ?: error("能力来源无效")
            }
            val baseUrl = string("baseUrl").trim().trimEnd('/')
            val model = string("model").trim()
            val rawModels = string("availableModels")
            if (baseUrl.length > 2048 || model.length > 256 || rawModels.length > 10_000) error("URL、模型 ID 或备用模型列表超过允许长度")
            val modelIds = rawModels.split(Regex("[,\\r\\n]+"))
                .map(String::trim).filter(String::isNotBlank)
            if (modelIds.any { it.length > 256 }) error("备用模型 ID 不能超过 256 个字符")
            val imported = AiSettings(
                id = java.util.UUID.randomUUID().toString(),
                name = string("name", "导入的配置").trim().take(48).ifBlank { "导入的配置" },
                baseUrl = baseUrl,
                model = model,
                supportsImages = bool("supportsImages", false),
                inputUsdPerMillion = string("inputUsdPerMillion").take(32),
                outputUsdPerMillion = string("outputUsdPerMillion").take(32),
                enabled = bool("enabled", true),
                contextWindowTokens = string("contextWindowTokens").take(12),
                availableModels = modelIds.filterNot { it == model }.distinct().take(32).joinToString("\n"),
                supportedTasks = tasks,
                capabilityEvidence = evidence,
                preference = ((fields["preference"] as? JsonValue.JNumber)?.value?.toInt() ?: 0).coerceIn(-20, 20),
                providerProtocol = protocol,
                temperature = string("temperature").take(8),
                maxOutputTokens = string("maxOutputTokens").take(9),
                systemInstruction = string("systemInstruction"),
                connectTimeoutSeconds = string("connectTimeoutSeconds").take(3),
                readTimeoutSeconds = string("readTimeoutSeconds").take(3),
            )
            val error = validate(imported.copy(apiKey = "import-validation-key"), hasSavedApiKey = true)
            if (error != null) error(error)
            ProfileImportResult.Success(imported)
        }.getOrElse { error -> ProfileImportResult.Invalid(error.message?.take(180) ?: "配置 JSON 格式无效") }
    }

    private fun importProfilesBundle(serialized: String): ProfileImportResult {
        if (!profileBundleReadable()) return ProfileImportResult.Invalid("现有服务商配置无法读取；已阻止覆盖以保护密文")
        return runCatching {
            val fields = JsonMini.parse(serialized).asObject()?.entries ?: error("JSON 根节点必须是对象")
            if (fields["schema"]?.asString() != PROFILE_BUNDLE_SCHEMA) error("服务商配置包标识无效")
            if ((fields["formatVersion"] as? JsonValue.JNumber)?.value?.toInt() != 1) error("不支持此配置包版本")
            if ("apiKey" in fields) error("配置包不得包含 API Key；请使用 Gather AI 的无密钥导出文件")
            val items = fields["profiles"]?.asArray()?.items ?: error("配置包缺少 profiles 列表")
            if (items.isEmpty() || items.size > MAX_BUNDLE_PROFILES) error("配置包必须包含 1 到 $MAX_BUNDLE_PROFILES 个服务商")
            val imported = items.map { item ->
                when (val parsed = importProfileDraft(encodeJsonValue(item))) {
                    is ProfileImportResult.Success -> parsed.settings.copy(
                        enabled = false,
                        lastConnectionTestResult = null,
                        lastConnectionTestAt = null,
                    )
                    is ProfileImportResult.Invalid -> error(parsed.message)
                    is ProfileImportResult.BundleSuccess -> error("嵌套配置包不受支持")
                    ProfileImportResult.StorageError -> error("无法读取配置")
                }
            }
            val existing = readStoredProfiles()
            fun signature(profile: AiSettings): String = listOf(
                profile.name.trim().lowercase(),
                profile.providerProtocol.name,
                profile.baseUrl.trimEnd('/').lowercase(),
                profile.model.trim().lowercase(),
                profile.availableModels.trim().lowercase(),
                profile.supportsImages.toString(),
                profile.inputUsdPerMillion,
                profile.outputUsdPerMillion,
                profile.contextWindowTokens,
                profile.supportedTasks.sortedBy { it.name }.joinToString(","),
                profile.capabilityEvidence.name,
                profile.preference.toString(),
                profile.temperature,
                profile.maxOutputTokens,
                profile.systemInstruction,
                profile.customHeaders,
                profile.connectTimeoutSeconds,
                profile.readTimeoutSeconds,
            ).joinToString("\u0000")
            val known = existing.map(::signature).toMutableSet()
            val accepted = imported.filter { known.add(signature(it)) }
            val skipped = imported.size - accepted.size
            if (accepted.isNotEmpty() && !store.putString(PROFILES_KEY, encodeProfiles(existing + accepted))) {
                return ProfileImportResult.StorageError
            }
            ProfileImportResult.BundleSuccess(
                profiles = accepted.map { it.copy(apiKey = "", hasSavedApiKey = false) },
                skippedDuplicates = skipped,
            )
        }.getOrElse { error -> ProfileImportResult.Invalid(error.message?.take(180) ?: "配置包 JSON 格式无效") }
    }

    private fun encodeJsonValue(value: JsonValue): String = when (value) {
        is JsonValue.JString -> JsonMini.quote(value.value)
        is JsonValue.JNumber -> value.value.toString()
        is JsonValue.JBool -> value.value.toString()
        JsonValue.JNull -> "null"
        is JsonValue.JArray -> value.items.joinToString(",", "[", "]") { encodeJsonValue(it) }
        is JsonValue.JObject -> value.entries.entries.joinToString(",", "{", "}") {
            JsonMini.quote(it.key) + ":" + encodeJsonValue(it.value)
        }
    }

    /** Stores a short, sanitized connection result with the encrypted provider bundle. */
    fun recordConnectionTest(id: String, result: String): Boolean {
        if (!profileBundleReadable()) return false
        val records = readStoredProfiles()
        if (records.none { it.id == id }) return false
        val updated = records.map { profile ->
            if (profile.id == id) profile.copy(lastConnectionTestResult = result.trim().take(180), lastConnectionTestAt = Instant.now().toString()) else profile
        }
        return store.putString(PROFILES_KEY, encodeProfiles(updated))
    }

    fun save(settings: AiSettings): SettingsSaveResult {
        if (!profileBundleReadable()) return SettingsSaveResult.Invalid("现有加密 Provider 配置无法解析，已阻止覆盖以保护原密文")
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
            temperature = settings.temperature.trim(),
            maxOutputTokens = settings.maxOutputTokens.trim(),
            systemInstruction = settings.systemInstruction.trim(),
            customHeaders = settings.customHeaders.trim(),
            connectTimeoutSeconds = settings.connectTimeoutSeconds.trim(),
            readTimeoutSeconds = settings.readTimeoutSeconds.trim(),
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
                        temperature = fields["temperature"]?.asString().orEmpty(),
                        maxOutputTokens = fields["maxOutputTokens"]?.asString().orEmpty(),
                        systemInstruction = fields["systemInstruction"]?.asString().orEmpty(),
                        customHeaders = fields["customHeaders"]?.asString().orEmpty(),
                        connectTimeoutSeconds = fields["connectTimeoutSeconds"]?.asString().orEmpty(),
                        readTimeoutSeconds = fields["readTimeoutSeconds"]?.asString().orEmpty(),
                        lastConnectionTestResult = fields["lastConnectionTestResult"]?.asString(),
                        lastConnectionTestAt = fields["lastConnectionTestAt"]?.asString(),
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

    private fun profileBundleReadable(): Boolean {
        if (!store.contains(PROFILES_KEY)) return true
        return runCatching {
            val root = JsonMini.parse(store.getString(PROFILES_KEY).orEmpty()).asObject()?.entries ?: return false
            root["profiles"]?.asArray() != null
        }.getOrDefault(false)
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
            "\"temperature\":" + JsonMini.quote(profile.temperature),
            "\"maxOutputTokens\":" + JsonMini.quote(profile.maxOutputTokens),
            "\"systemInstruction\":" + JsonMini.quote(profile.systemInstruction),
            "\"customHeaders\":" + JsonMini.quote(profile.customHeaders),
            "\"connectTimeoutSeconds\":" + JsonMini.quote(profile.connectTimeoutSeconds),
            "\"readTimeoutSeconds\":" + JsonMini.quote(profile.readTimeoutSeconds),
            "\"lastConnectionTestResult\":" + (profile.lastConnectionTestResult?.let(JsonMini::quote) ?: "null"),
            "\"lastConnectionTestAt\":" + (profile.lastConnectionTestAt?.let(JsonMini::quote) ?: "null"),
        ).joinToString(",") + "}"
    }

    companion object {
        private const val PROFILES_KEY = "ai_provider_profiles_v1"
        private const val ACTIVE_PROFILE_KEY = "active_ai_provider_profile"
        private const val LEGACY_PROFILE_ID = "legacy"
        private const val PROFILE_EXPORT_SCHEMA = "gather-ai-provider-profile"
        private const val PROFILE_BUNDLE_SCHEMA = "gather-ai-provider-bundle"
        private const val MAX_PROFILE_IMPORT_CHARS = 1_000_000
        private const val MAX_BUNDLE_PROFILES = 50
        private const val MAX_SYSTEM_INSTRUCTION_CHARS = 10_000
        private const val MAX_CUSTOM_HEADERS_CHARS = 8_192

        fun validate(settings: AiSettings, hasSavedApiKey: Boolean = settings.hasSavedApiKey): String? {
            if (settings.baseUrl.isBlank()) return "请输入 API Base URL"
            val url = runCatching { java.net.URI(settings.baseUrl.trim()) }.getOrNull()
            if (url == null || url.scheme !in setOf("http", "https") || url.host.isNullOrBlank()) {
                return "请输入有效的 HTTP 或 HTTPS Base URL"
            }
            if (url.userInfo != null || url.query != null || url.fragment != null) return "Base URL 不能包含账号密码、查询参数或片段"
            if (settings.model.isBlank()) return "请输入 Model 名称"
            if (settings.apiKey.isBlank() && !hasSavedApiKey) return "请输入 API Key"
            if (settings.contextWindowTokens.isNotBlank() && settings.contextWindowTokens.toIntOrNull()?.let { it > 0 } != true) return "上下文容量必须是正整数；未知时留空"
            if (settings.preference !in -20..20) return "模型偏好必须在 -20 至 20 之间"
            val maxTemperature = if (settings.providerProtocol == ProviderProtocol.ANTHROPIC_MESSAGES) 1.0 else 2.0
            if (settings.temperature.isNotBlank() && settings.temperature.toDoubleOrNull()?.let { it in 0.0..maxTemperature } != true) {
                return "当前协议温度范围是 0 到 ${maxTemperature.toInt()}；留空使用协议默认值"
            }
            if (settings.maxOutputTokens.isNotBlank() && settings.maxOutputTokens.toIntOrNull()?.let { it in 1..1_000_000 } != true) return "最大输出 Token 必须是 1 到 1,000,000 的整数；留空使用协议默认值"
            if (settings.systemInstruction.length > MAX_SYSTEM_INSTRUCTION_CHARS) return "系统指令最多 $MAX_SYSTEM_INSTRUCTION_CHARS 个字符"
            if (settings.customHeaders.length > MAX_CUSTOM_HEADERS_CHARS) return "自定义请求头最多 $MAX_CUSTOM_HEADERS_CHARS 个字符"
            val headerError = parseCustomHeaders(settings.customHeaders).exceptionOrNull()?.message
            if (headerError != null) return headerError
            if (settings.connectTimeoutSeconds.isNotBlank() && settings.connectTimeoutSeconds.toIntOrNull()?.let { it in 1..120 } != true) return "连接超时应为 1 到 120 秒；留空使用 15 秒默认值"
            if (settings.readTimeoutSeconds.isNotBlank() && settings.readTimeoutSeconds.toIntOrNull()?.let { it in 1..600 } != true) return "响应空闲超时应为 1 到 600 秒；留空使用 60 秒默认值"
            if (settings.inputUsdPerMillion.isNotBlank() && settings.inputUsdPerMillion.toDoubleOrNull()?.let { it >= 0 } != true) return "输入 Token 单价必须是非负数字"
            if (settings.outputUsdPerMillion.isNotBlank() && settings.outputUsdPerMillion.toDoubleOrNull()?.let { it >= 0 } != true) return "输出 Token 单价必须是非负数字"
            return null
        }

        private fun Map<String, JsonValue>.string(key: String): String = this[key]?.asString() ?: error("Invalid field")
    }
}

fun parseCustomHeaders(raw: String): Result<Map<String, String>> = runCatching {
    val lines = raw.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
    require(lines.size <= 20) { "自定义请求头最多 20 项" }
    val headers = linkedMapOf<String, String>()
    val reserved = setOf("authorization", "content-type", "accept", "host", "content-length", "connection", "transfer-encoding", "x-api-key", "x-goog-api-key", "anthropic-version")
    lines.forEach { line ->
        val split = line.indexOf(':')
        require(split > 0) { "请求头格式应为 Name: value" }
        val name = line.substring(0, split).trim()
        val value = line.substring(split + 1).trim()
        require(name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}"))) { "请求头名称无效" }
        require(name.lowercase() !in reserved) { "$name 由 Gather AI 管理，不能覆盖" }
        require(value.isNotBlank() && value.length <= 2048 && value.none { it == '\r' || it == '\n' || it.code < 0x20 || it.code == 0x7f }) { "$name 的值为空、过长或包含控制字符" }
        require(headers.keys.none { it.equals(name, ignoreCase = true) }) { "请求头名称不能重复：$name" }
        headers[name] = value
    }
    headers
}
