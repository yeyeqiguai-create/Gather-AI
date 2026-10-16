package com.tim.futureai.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRoutingTest {
    private fun candidate(
        id: String,
        tasks: Set<ModelTask> = emptySet(),
        context: Int? = null,
        images: Boolean = false,
        enabled: Boolean = true,
        availability: ProviderAvailability = ProviderAvailability.READY,
        preference: Int = 0,
    ) = ModelCandidate(
        id = id, profileId = id, providerName = "provider-$id", modelId = "model-$id", tasks = tasks,
        contextWindowTokens = context, supportsImages = images, enabled = enabled,
        availability = availability, preference = preference,
        capabilityEvidence = if (tasks.isEmpty()) CapabilityEvidence.UNKNOWN else CapabilityEvidence.PROVIDER_DECLARED,
    )

    @Test fun classifierRecognizesCodingReasoningAnalysisAndLongText() {
        assertEquals(ModelTask.CODING, TaskClassifier.classify("请 debug 这段代码"))
        assertEquals(ModelTask.REASONING, TaskClassifier.classify("请逐步分析并证明结论"))
        assertEquals(ModelTask.ANALYSIS, TaskClassifier.classify("请比较并评估这两个方案"))
        assertEquals(ModelTask.LONG_CONTEXT, TaskClassifier.classify("x".repeat(12_000)))
        assertEquals(ModelTask.GENERAL, TaskClassifier.classify("你好"))
    }

    @Test fun selectorPrefersTaskFitAndUserPreferenceAmongAvailableModels() {
        val result = AutomaticModelSelector.select(
            ModelRoutingRequest("帮我 implement 一个 parser", 2_000, preferredProfileId = "fast"),
            listOf(
                candidate("fast", context = 32_000, preference = 5),
                candidate("code", tasks = setOf(ModelTask.CODING), context = 16_000),
            ),
        ) as ModelSelection.Selected
        assertEquals("code", result.candidate.profileId)
        assertTrue(result.explanation.contains("编程"))
    }

    @Test fun selectorHonorsVisionContextEnabledAndProviderStatusHardRequirements() {
        val candidates = listOf(
            candidate("disabled", images = true, enabled = false),
            candidate("limited", images = true, availability = ProviderAvailability.RATE_LIMITED),
            candidate("small", context = 1_024, images = true),
            candidate("text-only", context = 16_000),
            candidate("good", context = 8_000, images = true),
        )
        val result = AutomaticModelSelector.select(
            ModelRoutingRequest("分析这张图", estimatedContextTokens = 2_000, requiresImages = true),
            candidates,
        ) as ModelSelection.Selected
        assertEquals("good", result.candidate.profileId)
        assertEquals("上下文需求超过登记容量", result.rejectedCandidates["small"])
        assertEquals("不支持图片输入", result.rejectedCandidates["text-only"])
    }

    @Test fun manualChoiceNeverFallsBackToAnotherProvider() {
        val result = AutomaticModelSelector.select(
            ModelRoutingRequest("hello", 10, manualProfileId = "manual"),
            listOf(candidate("other")),
        )
        assertTrue(result is ModelSelection.Unavailable)
        assertTrue((result as ModelSelection.Unavailable).reasons["manual"]!!.contains("不会自动切换"))
    }

    @Test fun automaticChoiceReturnsExplicitReasonWhenNothingCanServeRequest() {
        val result = AutomaticModelSelector.select(
            ModelRoutingRequest("x".repeat(12_000), 99_000),
            listOf(candidate("tiny", context = 16_000), candidate("off", enabled = false)),
        ) as ModelSelection.Unavailable
        assertEquals("上下文需求超过登记容量", result.reasons["tiny"])
        assertEquals("服务商已停用", result.reasons["off"])
    }

    @Test fun tokenEstimateIsConservativeAndNonZero() {
        assertEquals(1, TaskClassifier.estimateTokens(""))
        assertEquals(2, TaskClassifier.estimateTokens("12345"))
    }
}
