package com.tim.futureai.domain.arena

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArenaAggregationTest {
    @Test fun objectiveMethodDetection() {
        assertEquals(ArenaVerificationMethod.OBJECTIVE_CALCULATOR, ArenaAggregation.detectObjective("计算 17 * 23 的结果"))
        assertEquals(ArenaVerificationMethod.OBJECTIVE_CALCULATOR, ArenaAggregation.detectObjective("Calculate 12 + 30 please"))
        assertEquals(ArenaVerificationMethod.OBJECTIVE_JSON, ArenaAggregation.detectObjective("把结果输出为 JSON"))
        assertEquals(ArenaVerificationMethod.STRUCTURAL, ArenaAggregation.detectObjective("写一首诗"))
    }

    @Test fun calculatorVerificationPassesMatchingAnswers() {
        val entries = listOf(
            ArenaEntry("a", "p1", "答案是 391", 100, null, null, null, null),
            ArenaEntry("b", "p2", "391", 120, null, null, null, null),
            ArenaEntry("c", "p3", "错了，是 380", 90, null, null, null, null),
            ArenaEntry("d", "p4", null, 50, null, null, null, "timeout"),
        )
        val verification = ArenaVerification(
            ArenaVerificationMethod.OBJECTIVE_CALCULATOR, passed = true,
            detail = "期望值 391（表达式 17 * 23）",
            perEntry = mapOf("a" to true, "b" to true, "c" to false, "d" to false),
        )
        val conclusion = ArenaAggregation.conclusion(entries, null, verification)
        assertEquals("a", conclusion.recommendedModelKey)
        assertEquals("HIGH", conclusion.confidence)
        assertTrue(conclusion.basis.contains("391"))
    }

    @Test fun structuralOnlyIsAlwaysLowConfidence() {
        val entries = listOf(ArenaEntry("a", "p1", "好答案", 100, null, null, null, null))
        val conclusion = ArenaAggregation.conclusion(entries, null, ArenaVerification(ArenaVerificationMethod.STRUCTURAL, null, "结构校验", mapOf("a" to true)))
        assertEquals("LOW", conclusion.confidence)
        assertTrue(conclusion.basis.contains("无客观验证器"))
    }

    @Test fun numbersExtractionIgnoresSurroundingText() {
        assertEquals(listOf("391", "17"), ArenaAggregation.numbersIn("结果 391，用 17 步"))
        assertTrue(ArenaAggregation.numbersIn("没有数字").isEmpty())
        assertFalse(ArenaAggregation.numbersIn("v1.2").isEmpty())
    }
}
