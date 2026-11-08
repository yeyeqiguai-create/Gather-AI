package com.tim.futureai.ui.liquid

import androidx.compose.ui.graphics.Color
import com.tim.futureai.domain.settings.AccentColor
import com.tim.futureai.domain.settings.VisualTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualThemeSpecTest {
    @Test fun monochromeThemesKeepNeutralBasesAndApplyOnlyTheChosenAccent() {
        val black = VisualTheme.MONOCHROME_BLACK.spec(AccentColor.MINT)
        val white = VisualTheme.MONOCHROME_WHITE.spec(AccentColor.MINT)

        assertEquals(Color.Black, black.ambientMiddle)
        assertEquals(Color.White, black.text)
        assertEquals(Color.White, white.ambientMiddle)
        assertEquals(Color(0xFF111111), white.text)
        assertEquals(Color(AccentColor.MINT.primaryHex), black.accent)
        assertEquals(black.accent, white.accent)
        assertTrue(black.frosted.blurDp > 0f && white.frosted.blurDp > 0f)
        assertEquals(black.ambientMiddle, black.frosted.tint)
        assertEquals(white.ambientMiddle, white.frosted.tint)
        assertTrue(black.liquid.tintAlpha < 1f && white.liquid.refractionBandDp > 0f)
    }

    @Test fun technologyPresetsKeepDistinctBasesWhileSharingTheSelectedAccent() {
        val accent = AccentColor.CYAN
        val graphite = VisualTheme.GRAPHITE.spec(accent)
        val midnight = VisualTheme.MIDNIGHT.spec(accent)
        val deepSea = VisualTheme.DEEP_SEA.spec(accent)

        assertEquals(Color(accent.primaryHex), graphite.accent)
        assertEquals(Color(accent.primaryHex), midnight.accent)
        assertEquals(Color(accent.primaryHex), deepSea.accent)
        assertNotEquals(graphite.ambientMiddle, midnight.ambientMiddle)
        assertNotEquals(midnight.ambientMiddle, deepSea.ambientMiddle)
    }
}
