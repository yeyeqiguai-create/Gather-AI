package com.tim.futureai.ui.liquid

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Liquid Reality design system: theme specifications and design tokens.
 *
 * The theme enum lives in `domain.settings` (pure Kotlin, persistable in
 * AppPreferences); this module maps it to full visual specifications.
 */
typealias LiquidVisualTheme = com.tim.futureai.domain.settings.VisualTheme

/** Blur and refraction render behavior for one glass material in one theme. */
@Immutable
data class LiquidMaterialSpec(
    val blurDp: Float,
    val tint: Color,
    val tintAlpha: Float,
    val edgeHighlight: Color,
    val edgeAlpha: Float,
    val specularAlpha: Float,
    val refractionBandDp: Float,
)

enum class GlassMaterial { CLEAR, FROSTED, LIQUID, DARK, ACCENT }

@Immutable
data class LiquidMotion(
    val fastMillis: Int = 140,
    val normalMillis: Int = 220,
    val slowMillis: Int = 380,
    val emphasized: CubicBezierEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val springDamping: Float = 0.82f,
)

@Immutable
data class LiquidSpec(
    val visualTheme: LiquidVisualTheme,
    val label: String,
    val description: String,
    val isLight: Boolean,
    // Ambient backdrop
    val ambientTop: Color,
    val ambientMiddle: Color,
    val ambientBottom: Color,
    val blobPrimary: Color,
    val blobSecondary: Color,
    val blobTertiary: Color,
    val backdropTextureAlpha: Float,
    // Text and semantic colors
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentBright: Color,
    val cyan: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    // In-source soft surfaces (messages, settings cards): no blur, tint only
    val surfaceTint: Color,
    val surfaceTintAlpha: Float,
    val surfaceEdge: Color,
    val codeBlockBackground: Color,
    val codeBlockHeader: Color,
    val codeText: Color,
    // Per-material glass parameters
    val clear: LiquidMaterialSpec,
    val frosted: LiquidMaterialSpec,
    val liquid: LiquidMaterialSpec,
    val dark: LiquidMaterialSpec,
    val accentGlass: LiquidMaterialSpec,
    val motion: LiquidMotion = LiquidMotion(),
) {
    fun material(material: GlassMaterial): LiquidMaterialSpec = when (material) {
        GlassMaterial.CLEAR -> clear
        GlassMaterial.FROSTED -> frosted
        GlassMaterial.LIQUID -> liquid
        GlassMaterial.DARK -> dark
        GlassMaterial.ACCENT -> accentGlass
    }
}

/** User-controlled glass rendering preferences, mapped from AppPreferences. */
@Immutable
data class GlassRenderPrefs(
    val intensity: Float = 0.6f,
    val lowPerformance: Boolean = false,
    val dynamicBackground: Boolean = false,
    val reduceMotion: Boolean = false,
)

val LocalLiquidSpec = staticCompositionLocalOf { LiquidIndigoSpec }
val LocalGlassPrefs = staticCompositionLocalOf { GlassRenderPrefs() }
