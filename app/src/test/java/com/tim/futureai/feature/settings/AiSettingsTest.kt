package com.tim.futureai.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiSettingsTest {
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
}
