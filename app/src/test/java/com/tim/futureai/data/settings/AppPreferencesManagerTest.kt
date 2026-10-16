package com.tim.futureai.data.settings

import com.tim.futureai.core.security.SecureValueStore
import com.tim.futureai.domain.settings.AppPreferences
import com.tim.futureai.domain.settings.RoutingMode
import com.tim.futureai.domain.settings.SettingsSection
import com.tim.futureai.domain.settings.SettingsSearchRegistry
import com.tim.futureai.domain.settings.ThemeMode
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
