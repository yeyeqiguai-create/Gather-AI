package com.tim.futureai.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCapabilityRegistryTest {
    @Test fun registryCreatesStableNonSecretModelEntriesAndExcludesDisabledProviders() {
        val profiles = listOf(
            AiSettings(
                id = "provider-a", name = "Provider A", baseUrl = "https://a.example/v1", model = "default",
                availableModels = "fast\nlong", hasSavedApiKey = true, contextWindowTokens = "64000",
                supportedTasks = setOf(ModelTask.LONG_CONTEXT), supportsImages = true,
                capabilityEvidence = CapabilityEvidence.PROVIDER_DECLARED,
            ),
            AiSettings(
                id = "disabled", name = "Disabled", baseUrl = "https://b.example/v1", model = "off",
                hasSavedApiKey = true, enabled = false,
            ),
        )
        val entries = ModelCapabilityRegistry.fromProfiles(profiles)
        assertEquals(listOf("default", "fast", "long"), entries.map { it.modelId })
        assertEquals(3, entries.map { it.id }.distinct().size)
        assertTrue(entries.all { it.profileId == "provider-a" && it.providerName == "Provider A" })
        assertTrue(entries.all { it.contextWindowTokens == 64_000 && it.supportsImages })
        assertTrue(entries.all { it.capabilityEvidence == CapabilityEvidence.PROVIDER_DECLARED })
        assertFalse(entries.any { it.profileId == "disabled" })
        assertFalse(entries.any { it.modelId.contains("secret") })
    }

    @Test fun unknownCapabilitiesStayUnknownRatherThanInventingTaskSupport() {
        val model = ModelCapabilityRegistry.fromProfiles(listOf(
            AiSettings(id = "p", baseUrl = "https://a.example/v1", model = "m", hasSavedApiKey = true),
        )).single()
        assertEquals(emptySet<ModelTask>(), model.tasks)
        assertEquals(CapabilityEvidence.UNKNOWN, model.capabilityEvidence)
        assertEquals(null, model.contextWindowTokens)
    }
}
