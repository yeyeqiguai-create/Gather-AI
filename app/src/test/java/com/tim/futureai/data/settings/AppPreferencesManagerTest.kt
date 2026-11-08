package com.tim.futureai.data.settings

import com.tim.futureai.core.security.SecureValueStore
import com.tim.futureai.domain.settings.AppPreferences
import com.tim.futureai.domain.settings.AccentColor
import com.tim.futureai.domain.settings.RoutingMode
import com.tim.futureai.domain.settings.SettingsSection
import com.tim.futureai.domain.settings.SettingsSearchRegistry
import com.tim.futureai.domain.settings.ThemeMode
import com.tim.futureai.domain.settings.VisualTheme
import com.tim.futureai.domain.task.TaskAuthorizationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesManagerTest {
    private class MemoryStore : SecureValueStore {
        val values = mutableMapOf<String, String>()
        override fun putString(key: String, plainText: String) = true.also { values[key] = plainText }
        override fun getString(key: String) = values[key]
        override fun contains(key: String) = key in values
        override fun remove(key: String) = (values.remove(key) != null)
    }

    @Test fun persistsAndRestoresPreferences() {
        val store = MemoryStore()
        val manager = AppPreferencesManager(store)
        val expected = AppPreferences(
            themeMode = ThemeMode.LIGHT, fontScale = 1.15f, compactChat = true, animationsEnabled = false,
            showModelNames = false, showRoutingReason = false, routingMode = RoutingMode.AUTOMATIC,
            inheritGlobalModel = false, voiceInputEnabled = false, textToSpeechEnabled = true,
            startupUpdateCheck = false, historyLimit = 120,
        )
        assertTrue(manager.save(expected))
        assertEquals(expected, AppPreferencesManager(store).load())
    }

    @Test fun malformedPreferencesFallBackAndValuesAreBounded() {
        val store = MemoryStore()
        store.values[AppPreferencesManager.KEY] = "{broken"
        assertEquals(AppPreferences(), AppPreferencesManager(store).load())
        val manager = AppPreferencesManager(store)
        assertTrue(manager.save(AppPreferences(fontScale = 8f, historyLimit = -2)))
        assertEquals(1.25f, manager.load().fontScale)
        assertEquals(0, manager.load().historyLimit)
    }

    @Test fun resetOnlyRestoresPreferenceDefaults() {
        val store = MemoryStore()
        val manager = AppPreferencesManager(store)
        manager.save(AppPreferences(themeMode = ThemeMode.LIGHT))
        store.values["provider_bundle"] = "encrypted-provider-data"
        assertTrue(manager.reset())
        assertEquals(AppPreferences(), manager.load())
        assertEquals("encrypted-provider-data", store.values["provider_bundle"])
    }

    @Test fun taskEngineSettingsPersistAndNormalizeToSafeBounds() {
        val store = MemoryStore()
        val manager = AppPreferencesManager(store)
        val requested = AppPreferences(
            taskModeEnabled = false,
            taskAuthorizationPolicy = TaskAuthorizationPolicy.CONFIRM_EACH_TOOL,
            taskMaxSteps = 80,
            taskMaxRetries = 9,
            taskStepTimeoutMs = 1_000,
            taskTotalTimeoutMs = 8_000,
            requireTaskPlanReview = false,
            retainTaskHistory = false,
        )
        assertTrue(manager.save(requested))
        val loaded = AppPreferencesManager(store).load()
        assertFalse(loaded.taskModeEnabled)
        assertEquals(TaskAuthorizationPolicy.CONFIRM_EACH_TOOL, loaded.taskAuthorizationPolicy)
        assertEquals(30, loaded.taskMaxSteps)
        assertEquals(3, loaded.taskMaxRetries)
        assertEquals(5_000L, loaded.taskStepTimeoutMs)
        assertEquals(10_000L, loaded.taskTotalTimeoutMs)
        assertFalse(loaded.requireTaskPlanReview)
        assertFalse(loaded.retainTaskHistory)
        assertTrue(SettingsSearchRegistry.search("任务引擎").any { it.section == SettingsSection.TASKS })
    }

    @Test fun liquidThemeSettingsPersistAndGlassIntensityIsBounded() {
        val store = MemoryStore()
        val manager = AppPreferencesManager(store)
        val requested = AppPreferences(
            visualTheme = VisualTheme.AURORA,
            accentColor = AccentColor.MINT,
            glassIntensity = 1.7f,
            lowPerformanceMode = true,
            dynamicBackground = true,
        )
        assertTrue(manager.save(requested))
        val loaded = AppPreferencesManager(store).load()
        assertEquals(VisualTheme.AURORA, loaded.visualTheme)
        assertEquals(AccentColor.MINT, loaded.accentColor)
        assertEquals(1f, loaded.glassIntensity)
        assertTrue(loaded.lowPerformanceMode)
        assertTrue(loaded.dynamicBackground)
        assertTrue(manager.save(AppPreferences(glassIntensity = -0.5f)))
        assertEquals(0f, AppPreferencesManager(store).load().glassIntensity)
    }

    @Test fun legacyThemeModeMapsToVisualThemeWhenFieldMissing() {
        val store = MemoryStore()
        val legacyJson = "{\"themeMode\":\"LIGHT\",\"fontScale\":\"1.0\"}"
        store.values[AppPreferencesManager.KEY] = legacyJson
        assertEquals(VisualTheme.ARCTIC, AppPreferencesManager(store).load().visualTheme)
        store.values[AppPreferencesManager.KEY] = "{\"themeMode\":\"DARK\",\"fontScale\":\"1.0\"}"
        assertEquals(VisualTheme.GRAPHITE, AppPreferencesManager(store).load().visualTheme)
    }

    @Test fun followingSystemPersistsWithoutReplacingManualThemeOrAccent() {
        val store = MemoryStore()
        val manager = AppPreferencesManager(store)
        val requested = AppPreferences(
            visualTheme = VisualTheme.DEEP_SEA,
            followSystemTheme = true,
            accentColor = AccentColor.CYAN,
        )
        assertTrue(manager.save(requested))
        val loaded = AppPreferencesManager(store).load()
        assertEquals(requested, loaded)
        assertEquals(VisualTheme.MONOCHROME_WHITE, loaded.effectiveVisualTheme(systemDark = false))
        assertEquals(VisualTheme.GRAPHITE, loaded.effectiveVisualTheme(systemDark = true))
        assertEquals(VisualTheme.DEEP_SEA, loaded.copy(followSystemTheme = false).effectiveVisualTheme(systemDark = false))
        assertEquals(AccentColor.CYAN, loaded.accentColor)
    }

    @Test fun existingSystemModeDoesNotEnableFollowingOrReplaceManualTheme() {
        val store = MemoryStore()
        store.values[AppPreferencesManager.KEY] = """{"themeMode":"SYSTEM","visualTheme":"MIDNIGHT","visualDesignVersion":"3"}"""
        val loaded = AppPreferencesManager(store).load()
        assertFalse(loaded.followSystemTheme)
        assertEquals(VisualTheme.MIDNIGHT, loaded.effectiveVisualTheme(systemDark = false))
        assertEquals(VisualTheme.MIDNIGHT, loaded.effectiveVisualTheme(systemDark = true))
        assertFalse(AppPreferences().followSystemTheme)
    }

    @Test fun selectingManualThemeDisablesFollowingAndPersistsChoice() {
        val store = MemoryStore()
        val manager = AppPreferencesManager(store)
        val manual = AppPreferences(visualTheme = VisualTheme.DEEP_SEA, followSystemTheme = true)
            .selectVisualTheme(VisualTheme.MONOCHROME_BLACK)
        assertTrue(manager.save(manual))
        val loaded = manager.load()
        assertFalse(loaded.followSystemTheme)
        assertEquals(VisualTheme.MONOCHROME_BLACK, loaded.effectiveVisualTheme(systemDark = false))
        assertEquals(VisualTheme.MONOCHROME_BLACK, loaded.effectiveVisualTheme(systemDark = true))
    }

    @Test fun followSystemFlagAcceptsJsonBooleanAndRejectsMalformedValues() {
        val store = MemoryStore()
        store.values[AppPreferencesManager.KEY] = """{"followSystemTheme":true,"visualDesignVersion":"3"}"""
        assertTrue(AppPreferencesManager(store).load().followSystemTheme)
        store.values[AppPreferencesManager.KEY] = """{"followSystemTheme":"unknown","visualDesignVersion":"3"}"""
        assertFalse(AppPreferencesManager(store).load().followSystemTheme)
    }

    @Test fun visualRefreshMigratesTheOldDefaultOnceAndAllowsLaterThemeSelection() {
        val store = MemoryStore()
        store.values[AppPreferencesManager.KEY] = "{\"visualTheme\":\"OBSIDIAN\",\"glassIntensity\":\"0.32\"}"
        val manager = AppPreferencesManager(store)

        val refreshed = manager.load()
        assertEquals(VisualTheme.GRAPHITE, refreshed.visualTheme)
        assertEquals(AccentColor.NEUTRAL, refreshed.accentColor)
        assertEquals(3, refreshed.visualDesignVersion)
        assertEquals(0.55f, refreshed.glassIntensity)
        assertTrue(store.values[AppPreferencesManager.KEY]!!.contains("visualDesignVersion"))

        assertTrue(manager.save(refreshed.copy(visualTheme = VisualTheme.MONOCHROME_WHITE)))
        assertEquals(VisualTheme.MONOCHROME_WHITE, manager.load().visualTheme)
    }

    @Test fun globalAutomaticModeOrDisabledInheritanceRoutesNewSessionsAutomatically() {
        assertFalse(AppPreferences().usesAutomaticModelSelection())
        assertTrue(AppPreferences(routingMode = RoutingMode.AUTOMATIC).usesAutomaticModelSelection())
        assertTrue(AppPreferences(inheritGlobalModel = false).usesAutomaticModelSelection())
    }

    @Test fun searchUsesRegisteredSettingsSections() {
        assertEquals(SettingsSection.UPDATES, SettingsSearchRegistry.search("SHA-256").single().section)
        val modelResults = SettingsSearchRegistry.search("模型").map { it.section }
        assertTrue(modelResults.contains(SettingsSection.CHAT))
        assertTrue(modelResults.contains(SettingsSection.PROVIDERS))
        assertTrue(modelResults.contains(SettingsSection.MODEL_ROUTING))
        assertEquals("Aurora · gpt-test", SettingsSearchRegistry.search("gpt-test", listOf("Aurora" to listOf("gpt-test"))).single().title)
        assertFalse(SettingsSearchRegistry.search("not-a-setting").isNotEmpty())
        assertEquals(SettingsSection.registered.toSet().size, SettingsSection.registered.size)
    }
}
