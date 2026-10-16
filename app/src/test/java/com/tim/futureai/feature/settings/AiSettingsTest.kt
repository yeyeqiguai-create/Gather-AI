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
        assertEquals("mock-key", reloaded.apiKey)
        assertTrue(AiSettingsManager.validate(reloaded) == null)
    }

    @Test fun save_invalidSettingsDoesNotWriteAnything() {
        val store = FakeSecureStore()
        val result = AiSettingsManager(store).save(AiSettings("bad-url", "model", "mock-key"))
        assertTrue(result is SettingsSaveResult.Invalid)
        assertEquals(null, store.getString("api_key"))
    }
}
