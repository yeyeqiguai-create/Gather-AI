package com.tim.futureai.feature.settings

import com.tim.futureai.core.security.SecureValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSettingsTest {
    private class FakeSecureStore : SecureValueStore {
        private val values = mutableMapOf<String, String>()
        override fun putString(key: String, plainText: String): Boolean {
            values[key] = plainText
            return true
        }
        override fun getString(key: String): String? = values[key]
        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun remove(key: String): Boolean { values.remove(key); return true }
    }

    @Test fun validate_requiresBaseUrl() {
        assertEquals("请输入 API Base URL", AiSettingsManager.validate(AiSettings(model = "m", apiKey = "k")))
    }

    @Test fun validate_requiresHttpUrl() {
        assertEquals("请输入有效的 HTTP 或 HTTPS Base URL", AiSettingsManager.validate(AiSettings("file:///tmp", "m", "k")))
    }

    @Test fun validate_requiresModelAndKey() {
        assertEquals("请输入 Model 名称", AiSettingsManager.validate(AiSettings("https://example.com/v1", "", "k")))
        assertEquals("请输入 API Key", AiSettingsManager.validate(AiSettings("https://example.com/v1", "m", " ")))
    }

    @Test fun validate_acceptsCompleteConfiguration() {
        assertNull(AiSettingsManager.validate(AiSettings("https://example.com/v1", "m", "k")))
    }

    @Test fun save_normalizesAndReloadsConfiguration() {
        val store = FakeSecureStore()
        val manager = AiSettingsManager(store)
        assertEquals(
            SettingsSaveResult.Saved,
            manager.save(AiSettings(" https://example.com/v1/ ", " model-x ", " mock-key ")),
        )

        val reloaded = AiSettingsManager(store).load()
        assertEquals("https://example.com/v1", reloaded.baseUrl)
        assertEquals("model-x", reloaded.model)
        assertEquals("", reloaded.apiKey)
        assertTrue(reloaded.hasSavedApiKey)
        assertTrue(AiSettingsManager.validate(reloaded) == null)
    }

    @Test fun saveWithBlankKeyPreservesExistingSecureKey() {
        val store = FakeSecureStore()
        val manager = AiSettingsManager(store)
        manager.save(AiSettings("https://example.com/v1", "model-x", "mock-key"))

        val reopened = manager.load()
        assertEquals(SettingsSaveResult.Saved, manager.save(reopened.copy(model = "model-y")))
        assertEquals("mock-key", manager.configFor("default")?.apiKey)
        assertEquals("model-y", manager.load().model)
    }

    @Test fun save_invalidSettingsDoesNotWriteAnything() {
        val store = FakeSecureStore()
        val result = AiSettingsManager(store).save(AiSettings("bad-url", "model", "mock-key"))
        assertTrue(result is SettingsSaveResult.Invalid)
        assertEquals(null, store.getString("ai_provider_profiles_v1"))
    }

    @Test fun profiles_areEncryptedTogetherAndCanBeSelectedOrDeleted() {
        val store = FakeSecureStore()
        val manager = AiSettingsManager(store)
        assertEquals(SettingsSaveResult.Saved, manager.save(AiSettings(
            baseUrl = "https://one.example/v1", model = "model-one", apiKey = "secret-one", id = "one", name = "One",
        )))
        assertEquals(SettingsSaveResult.Saved, manager.save(AiSettings(
            baseUrl = "https://two.example/v1", model = "model-two", apiKey = "secret-two", id = "two", name = "Two",
        )))

        assertEquals(listOf("one", "two"), manager.profiles().map { it.id })
        assertTrue(manager.profiles().all { it.apiKey.isBlank() && it.hasSavedApiKey })
        assertEquals("secret-one", manager.configFor("one")?.apiKey)
        assertTrue(manager.selectProfile("one"))
        assertEquals("one", manager.load().id)
        assertTrue(manager.deleteProfile("one"))
        assertEquals("two", manager.load().id)
        assertNull(manager.configFor("one"))
    }

    @Test fun loadMigratesLegacyConfigurationOnSaveWithoutDroppingKey() {
        val store = FakeSecureStore()
        store.putString("base_url", "https://legacy.example/v1")
        store.putString("model", "legacy-model")
        store.putString("api_key", "legacy-secret")
        val manager = AiSettingsManager(store)
        val legacy = manager.load()
        assertEquals("legacy", legacy.id)
        assertEquals(SettingsSaveResult.Saved, manager.save(legacy.copy(model = "updated-model")))
        assertEquals("legacy-secret", manager.configFor("legacy")?.apiKey)
        assertNull(store.getString("api_key"))
    }
}
