package com.tim.futureai.data.settings

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.core.security.SecureValueStore
import com.tim.futureai.domain.settings.AppPreferences
import com.tim.futureai.domain.settings.RoutingMode
import com.tim.futureai.domain.settings.ThemeMode

/** App-level preferences share the encrypted store; provider secrets remain in their existing bundle. */
class AppPreferencesManager(private val store: SecureValueStore) {
    fun load(): AppPreferences = runCatching {
        val fields = JsonMini.parse(store.getString(KEY).orEmpty()).asObject()?.entries ?: return AppPreferences()
        AppPreferences(
            themeMode = fields["themeMode"]?.asString()?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            fontScale = fields["fontScale"]?.asString()?.toFloatOrNull() ?: 1f,
            compactChat = fields.bool("compactChat", false),
            animationsEnabled = fields.bool("animationsEnabled", true),
            showModelNames = fields.bool("showModelNames", true),
            showRoutingReason = fields.bool("showRoutingReason", true),
            routingMode = fields["routingMode"]?.asString()?.let { runCatching { RoutingMode.valueOf(it) }.getOrNull() } ?: RoutingMode.MANUAL,
            inheritGlobalModel = fields.bool("inheritGlobalModel", true),
            voiceInputEnabled = fields.bool("voiceInputEnabled", true),
            textToSpeechEnabled = fields.bool("textToSpeechEnabled", true),
            startupUpdateCheck = fields.bool("startupUpdateCheck", true),
            historyLimit = fields["historyLimit"]?.asString()?.toIntOrNull() ?: 0,
        ).normalized()
    }.getOrDefault(AppPreferences())

    fun save(value: AppPreferences): Boolean {
        val p = value.normalized()
        val fields = listOf(
            "themeMode" to p.themeMode.name, "fontScale" to p.fontScale.toString(),
            "compactChat" to p.compactChat.toString(), "animationsEnabled" to p.animationsEnabled.toString(),
            "showModelNames" to p.showModelNames.toString(), "showRoutingReason" to p.showRoutingReason.toString(),
            "routingMode" to p.routingMode.name, "inheritGlobalModel" to p.inheritGlobalModel.toString(),
            "voiceInputEnabled" to p.voiceInputEnabled.toString(), "textToSpeechEnabled" to p.textToSpeechEnabled.toString(),
            "startupUpdateCheck" to p.startupUpdateCheck.toString(), "historyLimit" to p.historyLimit.toString(),
        )
        val json = fields.joinToString(",", prefix = "{", postfix = "}") { (key, v) -> "${JsonMini.quote(key)}:${JsonMini.quote(v)}" }
        return store.putString(KEY, json)
    }

    fun reset(): Boolean = save(AppPreferences())

    private fun Map<String, JsonValue>.bool(key: String, fallback: Boolean) =
        (this[key] as? JsonValue.JBool)?.value ?: this[key]?.asString()?.toBooleanStrictOrNull() ?: fallback

    companion object { const val KEY = "app_preferences_v1" }
}
