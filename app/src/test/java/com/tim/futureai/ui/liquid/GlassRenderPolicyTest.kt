package com.tim.futureai.ui.liquid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassRenderPolicyTest {
    private val normal = GlassRenderPrefs(intensity = 0.55f)

    @Test fun platformFallbackKeepsOnlyEffectsSupportedByTheDevice() {
        val expected = mapOf(
            24 to GlassRenderMode.TINT,
            30 to GlassRenderMode.TINT,
            31 to GlassRenderMode.BLUR,
            32 to GlassRenderMode.BLUR,
            33 to GlassRenderMode.REFRACTION,
            37 to GlassRenderMode.REFRACTION,
        )
        expected.forEach { (sdk, mode) ->
            assertEquals("API $sdk", mode, liquidMode(sdk, normal))
            assertEquals("Source recording on API $sdk", mode != GlassRenderMode.TINT, GlassRenderPolicy.recordsSource(sdk, normal))
        }
    }

    @Test fun lightweightModeDisablesTheSourceAndAllPanelEffects() {
        for (sdk in listOf(24, 31, 33, 37)) {
            val lightweight = normal.copy(lowPerformance = true)
            assertFalse(GlassRenderPolicy.recordsSource(sdk, lightweight))
            assertEquals(GlassRenderMode.TINT, liquidMode(sdk, lightweight))
        }
    }

    @Test fun zeroStrengthAndItsDeadZoneNeverAllocateAnEffectSource() {
        for (intensity in listOf(0f, 0.005f, 0.01f)) {
            val prefs = normal.copy(intensity = intensity)
            assertFalse(GlassRenderPolicy.recordsSource(33, prefs))
            assertEquals(GlassRenderMode.TINT, liquidMode(33, prefs))
        }
        val visibleGlass = normal.copy(intensity = 0.011f)
        assertTrue(GlassRenderPolicy.recordsSource(33, visibleGlass))
        assertEquals(GlassRenderMode.REFRACTION, liquidMode(33, visibleGlass))
    }

    @Test fun dialogsWithoutAStageSourceRemainTintOnly() {
        assertEquals(
            GlassRenderMode.TINT,
            GlassRenderPolicy.surfaceMode(33, normal, hasSource = false, blurDp = 16f, refractionBandDp = 9f),
        )
    }

    @Test fun materialParametersDoNotAllocateUnusedEffects() {
        assertEquals(
            GlassRenderMode.BLUR,
            GlassRenderPolicy.surfaceMode(33, normal, hasSource = true, blurDp = 18f, refractionBandDp = 0f),
        )
        assertEquals(
            GlassRenderMode.TINT,
            GlassRenderPolicy.surfaceMode(33, normal, hasSource = true, blurDp = 0f, refractionBandDp = 9f),
        )
    }

    @Test fun reduceMotionPreservesStaticBlurAndRefraction() {
        val stillGlass = normal.copy(reduceMotion = true, dynamicBackground = false)
        assertTrue(GlassRenderPolicy.recordsSource(33, stillGlass))
        assertEquals(GlassRenderMode.REFRACTION, liquidMode(33, stillGlass))
    }

    private fun liquidMode(sdkInt: Int, prefs: GlassRenderPrefs) =
        GlassRenderPolicy.surfaceMode(sdkInt, prefs, hasSource = true, blurDp = 16f, refractionBandDp = 9f)
}
