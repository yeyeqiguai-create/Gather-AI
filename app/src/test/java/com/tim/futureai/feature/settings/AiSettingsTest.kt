package com.tim.futureai.feature.settings

import com.tim.futureai.core.security.SecureValueStore
import com.tim.futureai.data.settings.AiSettingsManager
import com.tim.futureai.domain.ai.CapabilityEvidence
import com.tim.futureai.domain.ai.ModelTask
import com.tim.futureai.domain.ai.ProviderProtocol
import com.tim.futureai.domain.ai.AiSettings
import com.tim.futureai.domain.ai.SettingsSaveResult
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

    @Test fun capabilityRegistryFieldsRoundTripInsideEncryptedProfileBundle() {
        val store = FakeSecureStore()
        val manager = AiSettingsManager(store)
        val source = AiSettings(
            baseUrl = "https://provider.example/v1", model = "reasoner", apiKey = "secret",
            id = "reasoner", name = "Reasoning provider", contextWindowTokens = "64000",
            availableModels = " fast-model \nlong-model,reasoner",
            supportedTasks = setOf(ModelTask.REASONING, ModelTask.CODING),
            capabilityEvidence = CapabilityEvidence.PROVIDER_DECLARED, preference = 8,
            providerProtocol = ProviderProtocol.OPENAI_COMPATIBLE,
        )
        assertEquals(SettingsSaveResult.Saved, manager.save(source))
        val persisted = store.getString("ai_provider_profiles_v1").orEmpty()
        assertTrue("profile bundle is stored through the SecureValueStore abstraction", persisted.contains("reasoner"))
        assertTrue("API key is not exposed by the profiles UI model", manager.profiles().single().apiKey.isBlank())
        val restored = AiSettingsManager(store).loadProfile("reasoner")!!
        assertEquals("64000", restored.contextWindowTokens)
        assertEquals("fast-model\nlong-model", restored.availableModels)
        assertEquals(setOf(ModelTask.REASONING, ModelTask.CODING), restored.supportedTasks)
        assertEquals(CapabilityEvidence.PROVIDER_DECLARED, restored.capabilityEvidence)
        assertEquals(8, restored.preference)
        assertEquals(ProviderProtocol.OPENAI_COMPATIBLE, restored.providerProtocol)
        assertTrue(restored.enabled)
        assertEquals("fast-model", AiSettingsManager(store).configFor("reasoner", "fast-model")?.model)
        assertNull(AiSettingsManager(store).configFor("reasoner", "unregistered-model"))
    }

    @Test fun disabledProviderCannotBecomeActiveRouteAndLegacyProfilesRemainEnabled() {
        val store = FakeSecureStore()
        val manager = AiSettingsManager(store)
        assertEquals(SettingsSaveResult.Saved, manager.save(AiSettings(
            baseUrl = "https://disabled.example/v1", model = "m", apiKey = "k", id = "disabled", enabled = false,
        )))
        assertNull(manager.configFor("disabled"))
        assertTrue(!manager.selectProfile("disabled"))
        assertTrue(manager.profiles().single().enabled.not())

        val legacy = FakeSecureStore().apply {
            putString("base_url", "https://legacy.example/v1")
            putString("model", "legacy")
            putString("api_key", "legacy-secret")
        }
        assertTrue(AiSettingsManager(legacy).profiles().single().enabled)
    }
}
