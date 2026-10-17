package com.tim.futureai.data.settings

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.core.security.SecureValueStore
import com.tim.futureai.domain.settings.AppPreferences
import com.tim.futureai.domain.settings.RoutingMode
import com.tim.futureai.domain.settings.ThemeMode
import com.tim.futureai.domain.settings.VisualTheme
import com.tim.futureai.domain.task.TaskAuthorizationPolicy

/** App-level preferences share the encrypted store; provider secrets remain in their existing bundle. */
class AppPreferencesManager(private val store: SecureValueStore) {
    fun load(): AppPreferences = runCatching {
        val fields = JsonMini.parse(store.getString(KEY).orEmpty()).asObject()?.entries ?: return AppPreferences()
        AppPreferences(
            themeMode = fields["themeMode"]?.asString()?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            visualTheme = fields["visualTheme"]?.asString()?.let { runCatching { VisualTheme.valueOf(it) }.getOrNull() }
                ?: when (fields["themeMode"]?.asString()) {
                    "LIGHT" -> VisualTheme.ARCTIC
                    else -> VisualTheme.LIQUID_INDIGO
                },
            glassIntensity = fields["glassIntensity"]?.asString()?.toFloatOrNull() ?: 0.6f,
            lowPerformanceMode = fields.bool("lowPerformanceMode", false),
            dynamicBackground = fields.bool("dynamicBackground", false),
            fontScale = fields["fontScale"]?.asString()?.toFloatOrNull() ?: 1f,
            onboardingDone = fields.bool("onboardingDone", false),
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
            taskModeEnabled = fields.bool("taskModeEnabled", true),
            taskAuthorizationPolicy = fields["taskAuthorizationPolicy"]?.asString()?.let {
                runCatching { TaskAuthorizationPolicy.valueOf(it) }.getOrNull()
            } ?: TaskAuthorizationPolicy.CONFIRM_SIDE_EFFECTS,
            taskMaxSteps = fields.numberString("taskMaxSteps", 12).toIntOrNull() ?: 12,
            taskMaxRetries = fields.numberString("taskMaxRetries", 1).toIntOrNull() ?: 1,
            taskStepTimeoutMs = fields.numberString("taskStepTimeoutMs", 30_000).toLongOrNull() ?: 30_000,
            taskTotalTimeoutMs = fields.numberString("taskTotalTimeoutMs", 300_000).toLongOrNull() ?: 300_000,
            requireTaskPlanReview = fields.bool("requireTaskPlanReview", true),
            retainTaskHistory = fields.bool("retainTaskHistory", true),
        ).normalized()
    }.getOrDefault(AppPreferences())

    fun save(value: AppPreferences): Boolean {
        val p = value.normalized()
        val fields = listOf(
            "themeMode" to p.themeMode.name,
            "visualTheme" to p.visualTheme.name,
            "glassIntensity" to p.glassIntensity.toString(),
            "lowPerformanceMode" to p.lowPerformanceMode.toString(),
            "dynamicBackground" to p.dynamicBackground.toString(),
            "fontScale" to p.fontScale.toString(),
            "onboardingDone" to p.onboardingDone.toString(),
            "compactChat" to p.compactChat.toString(), "animationsEnabled" to p.animationsEnabled.toString(),
            "showModelNames" to p.showModelNames.toString(), "showRoutingReason" to p.showRoutingReason.toString(),
            "routingMode" to p.routingMode.name, "inheritGlobalModel" to p.inheritGlobalModel.toString(),
            "voiceInputEnabled" to p.voiceInputEnabled.toString(), "textToSpeechEnabled" to p.textToSpeechEnabled.toString(),
            "startupUpdateCheck" to p.startupUpdateCheck.toString(), "historyLimit" to p.historyLimit.toString(),
            "taskModeEnabled" to p.taskModeEnabled.toString(), "taskAuthorizationPolicy" to p.taskAuthorizationPolicy.name,
            "taskMaxSteps" to p.taskMaxSteps.toString(), "taskMaxRetries" to p.taskMaxRetries.toString(),
            "taskStepTimeoutMs" to p.taskStepTimeoutMs.toString(), "taskTotalTimeoutMs" to p.taskTotalTimeoutMs.toString(),
            "requireTaskPlanReview" to p.requireTaskPlanReview.toString(), "retainTaskHistory" to p.retainTaskHistory.toString(),
        )
        val json = fields.joinToString(",", prefix = "{", postfix = "}") { (key, v) -> "${JsonMini.quote(key)}:${JsonMini.quote(v)}" }
        return store.putString(KEY, json)
    }

    fun reset(): Boolean = save(AppPreferences())

    private fun Map<String, JsonValue>.bool(key: String, fallback: Boolean) =
        (this[key] as? JsonValue.JBool)?.value ?: this[key]?.asString()?.toBooleanStrictOrNull() ?: fallback
    private fun Map<String, JsonValue>.numberString(key: String, fallback: Int): String =
        this[key]?.let { value -> value.asString() ?: (value as? JsonValue.JNumber)?.value?.toLong()?.toString() } ?: fallback.toString()

    companion object { const val KEY = "app_preferences_v1" }
}
