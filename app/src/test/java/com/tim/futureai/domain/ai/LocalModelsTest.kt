package com.tim.futureai.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalModelsTest {
    @Test fun parsesStrictJsonDecision() {
        val d = LocalModels.parseDecision("{\"tier\":\"2b\",\"reason\":\"需要推理\"}")!!
        assertEquals(LocalModelTier.MEDIUM_2B, d.tier)
        assertEquals("需要推理", d.reason)
    }

    @Test fun parsesLooseTextAndDefaultsToSmallest() {
        assertEquals(LocalModelTier.TINY_0_4B, LocalModels.parseDecision("0.4b 就够了")!!.tier)
        assertEquals(LocalModelTier.SMALL_1B, LocalModels.parseDecision("tier: 1b")!!.tier)
        assertNull(LocalModels.parseDecision("无法判断"))
    }

    @Test fun routerPromptCarriesTheUserText() {
        val prompt = LocalModels.routerPrompt("帮我写一个排序函数")
        assertEquals(true, prompt.contains("排序函数"))
        assertEquals(true, prompt.contains("0.4b"))
    }
}
