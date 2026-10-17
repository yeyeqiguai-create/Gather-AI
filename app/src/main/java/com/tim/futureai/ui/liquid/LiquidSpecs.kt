package com.tim.futureai.ui.liquid

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.tim.futureai.ui.liquid.GlassMaterial

private fun materials(
    glassTint: Color,
    glassTintAlpha: Float,
    edge: Color,
    edgeAlpha: Float,
    darkTint: Color,
    accentTint: Color,
    blurScale: Float = 1f,
): Map<GlassMaterial, LiquidMaterialSpec> = mapOf(
    GlassMaterial.CLEAR to LiquidMaterialSpec(
        blurDp = 10f * blurScale, tint = glassTint, tintAlpha = glassTintAlpha * 0.45f,
        edgeHighlight = edge, edgeAlpha = edgeAlpha * 0.55f, specularAlpha = 0.10f, refractionBandDp = 0f,
    ),
    GlassMaterial.FROSTED to LiquidMaterialSpec(
        blurDp = 22f * blurScale, tint = glassTint, tintAlpha = glassTintAlpha,
        edgeHighlight = edge, edgeAlpha = edgeAlpha, specularAlpha = 0.16f, refractionBandDp = 0f,
    ),
    GlassMaterial.LIQUID to LiquidMaterialSpec(
        blurDp = 18f * blurScale, tint = glassTint, tintAlpha = glassTintAlpha * 0.72f,
        edgeHighlight = edge, edgeAlpha = (edgeAlpha * 1.5f).coerceAtMost(1f), specularAlpha = 0.26f, refractionBandDp = 14f,
    ),
    GlassMaterial.DARK to LiquidMaterialSpec(
        blurDp = 26f * blurScale, tint = darkTint, tintAlpha = 0.66f,
        edgeHighlight = edge, edgeAlpha = edgeAlpha * 0.7f, specularAlpha = 0.08f, refractionBandDp = 0f,
    ),
    GlassMaterial.ACCENT to LiquidMaterialSpec(
        blurDp = 20f * blurScale, tint = accentTint, tintAlpha = 0.5f,
        edgeHighlight = edge, edgeAlpha = edgeAlpha, specularAlpha = 0.22f, refractionBandDp = 0f,
    ),
)

/** Liquid Indigo — the Gather AI brand default: deep space blue-violet. */
val LiquidIndigoSpec: LiquidSpec = run {
    val m = materials(
        glassTint = Color(0xFF151B33), glassTintAlpha = 0.80f,
        edge = Color(0xFFDDE3FF), edgeAlpha = 0.30f,
        darkTint = Color(0xFF0B0F1E), accentTint = Color(0xFF5A48C2),
    )
    LiquidSpec(
        visualTheme = LiquidVisualTheme.LIQUID_INDIGO,
        label = "Liquid Indigo", description = "深空蓝紫 · 品牌默认",
        isLight = false,
        ambientTop = Color(0xFF0D1230), ambientMiddle = Color(0xFF080B16), ambientBottom = Color(0xFF05060C),
        blobPrimary = Color(0x402F6AE6), blobSecondary = Color(0x382F1B72), blobTertiary = Color(0x30145C8A),
        backdropTextureAlpha = 0.05f,
        text = Color(0xFFF4F2FF), muted = Color(0xFFA6AAC0),
        accent = Color(0xFF9B7BFF), accentBright = Color(0xFFB7A1FF), cyan = Color(0xFF7BD9F5),
        success = Color(0xFF8AE0C2), danger = Color(0xFFFF9BAF), warning = Color(0xFFFFC96B),
        surfaceTint = Color(0xFF171C2D), surfaceTintAlpha = 0.94f, surfaceEdge = Color(0x36DDE3FF),
        codeBlockBackground = Color(0xFF10131D), codeBlockHeader = Color(0xFF1B2030), codeText = Color(0xFFD8E6FF),
        clear = m.getValue(GlassMaterial.CLEAR), frosted = m.getValue(GlassMaterial.FROSTED),
        liquid = m.getValue(GlassMaterial.LIQUID), dark = m.getValue(GlassMaterial.DARK),
        accentGlass = m.getValue(GlassMaterial.ACCENT),
    )
}

/** Obsidian — restrained graphite and silver for long, focused sessions. */
val ObsidianSpec: LiquidSpec = run {
    val m = materials(
        glassTint = Color(0xFF16181C), glassTintAlpha = 0.84f,
        edge = Color(0xFFE8EAEE), edgeAlpha = 0.22f,
        darkTint = Color(0xFF0C0D10), accentTint = Color(0xFF43505E),
        blurScale = 0.9f,
    )
    LiquidSpec(
        visualTheme = LiquidVisualTheme.OBSIDIAN,
        label = "Obsidian", description = "石墨黑 · 克制专业",
        isLight = false,
        ambientTop = Color(0xFF141518), ambientMiddle = Color(0xFF0C0D0F), ambientBottom = Color(0xFF070808),
        blobPrimary = Color(0x2A3C4656), blobSecondary = Color(0x201C2028), blobTertiary = Color(0x18202830),
        backdropTextureAlpha = 0.04f,
        text = Color(0xFFECEDEF), muted = Color(0xFF9A9DA5),
        accent = Color(0xFFAEB6C2), accentBright = Color(0xFFCBD2DB), cyan = Color(0xFF8FB6C9),
        success = Color(0xFF7FCDA9), danger = Color(0xFFF08C9E), warning = Color(0xFFE8B96A),
        surfaceTint = Color(0xFF16181D), surfaceTintAlpha = 0.95f, surfaceEdge = Color(0x2AE8EAEE),
        codeBlockBackground = Color(0xFF101114), codeBlockHeader = Color(0xFF191B1F), codeText = Color(0xFFD4DBE4),
        clear = m.getValue(GlassMaterial.CLEAR), frosted = m.getValue(GlassMaterial.FROSTED),
        liquid = m.getValue(GlassMaterial.LIQUID), dark = m.getValue(GlassMaterial.DARK),
        accentGlass = m.getValue(GlassMaterial.ACCENT),
    )
}

/** Arctic — light glacier glass with crisp contrast. */
val ArcticSpec: LiquidSpec = run {
    val m = materials(
        glassTint = Color(0xFFF7FBFF), glassTintAlpha = 0.84f,
        edge = Color(0xFFFFFFFF), edgeAlpha = 0.85f,
        darkTint = Color(0xFF2A3A48), accentTint = Color(0xFF1E6FA8),
    )
    LiquidSpec(
        visualTheme = LiquidVisualTheme.ARCTIC,
        label = "Arctic", description = "冰川浅色 · 明亮通透",
        isLight = true,
        ambientTop = Color(0xFFE9F2FA), ambientMiddle = Color(0xFFDDEAF6), ambientBottom = Color(0xFFCFE2F2),
        blobPrimary = Color(0x2E9ECBEE), blobSecondary = Color(0x24B4D8F2), blobTertiary = Color(0x1E7FB3D9),
        backdropTextureAlpha = 0.05f,
        text = Color(0xFF16222E), muted = Color(0xFF4E5E6E),
        accent = Color(0xFF1E6FA8), accentBright = Color(0xFF15598A), cyan = Color(0xFF087B9B),
        success = Color(0xFF16785B), danger = Color(0xFFB32645), warning = Color(0xFF9A6B1F),
        surfaceTint = Color(0xFFFFFFFF), surfaceTintAlpha = 0.94f, surfaceEdge = Color(0x59FFFFFF),
        codeBlockBackground = Color(0xFF1B2530), codeBlockHeader = Color(0xFF243140), codeText = Color(0xFFD8E9F7),
        clear = m.getValue(GlassMaterial.CLEAR), frosted = m.getValue(GlassMaterial.FROSTED),
        liquid = m.getValue(GlassMaterial.LIQUID), dark = m.getValue(GlassMaterial.DARK),
        accentGlass = m.getValue(GlassMaterial.ACCENT),
    )
}

/** Aurora — deep teal-navy with living aurora light. */
val AuroraSpec: LiquidSpec = run {
    val m = materials(
        glassTint = Color(0xFF0F2028), glassTintAlpha = 0.80f,
        edge = Color(0xFFD9FFF3), edgeAlpha = 0.30f,
        darkTint = Color(0xFF081318), accentTint = Color(0xFF157A6B),
    )
    LiquidSpec(
        visualTheme = LiquidVisualTheme.AURORA,
        label = "Aurora", description = "极光渐变 · 灵动有生命力",
        isLight = false,
        ambientTop = Color(0xFF0B1B26), ambientMiddle = Color(0xFF071118), ambientBottom = Color(0xFF040B10),
        blobPrimary = Color(0x360FA88F), blobSecondary = Color(0x302F6AE6), blobTertiary = Color(0x2A7B4FD8),
        backdropTextureAlpha = 0.05f,
        text = Color(0xFFEEF7F4), muted = Color(0xFF9BB5B0),
        accent = Color(0xFF4FE3C1), accentBright = Color(0xFF7FF0DA), cyan = Color(0xFF6FD3F0),
        success = Color(0xFF4FE3C1), danger = Color(0xFFFF9BAF), warning = Color(0xFFFFC96B),
        surfaceTint = Color(0xFF102329), surfaceTintAlpha = 0.94f, surfaceEdge = Color(0x36D9FFF3),
        codeBlockBackground = Color(0xFF0D1A20), codeBlockHeader = Color(0xFF15252C), codeText = Color(0xFFD2ECF4),
        clear = m.getValue(GlassMaterial.CLEAR), frosted = m.getValue(GlassMaterial.FROSTED),
        liquid = m.getValue(GlassMaterial.LIQUID), dark = m.getValue(GlassMaterial.DARK),
        accentGlass = m.getValue(GlassMaterial.ACCENT),
    )
}

fun LiquidVisualTheme.spec(): LiquidSpec = when (this) {
    LiquidVisualTheme.LIQUID_INDIGO -> LiquidIndigoSpec
    LiquidVisualTheme.OBSIDIAN -> ObsidianSpec
    LiquidVisualTheme.ARCTIC -> ArcticSpec
    LiquidVisualTheme.AURORA -> AuroraSpec
}

/** Root theme composable: provides Liquid tokens plus a mapped Material3 scheme. */
@Composable
fun GatherLiquidTheme(
    visualTheme: LiquidVisualTheme,
    fontScale: Float = 1f,
    glassPrefs: GlassRenderPrefs = GlassRenderPrefs(),
    content: @Composable () -> Unit,
) {
    val spec = visualTheme.spec()
    val density = LocalDensity.current
    val scheme = if (spec.isLight) lightColorScheme(
        primary = spec.accent, onPrimary = Color.White,
        primaryContainer = spec.accent.copy(alpha = 0.22f), onPrimaryContainer = spec.text,
        background = spec.ambientMiddle, onBackground = spec.text,
        surface = spec.surfaceTint, onSurface = spec.text,
        surfaceVariant = spec.ambientTop, onSurfaceVariant = spec.muted,
        error = spec.danger, errorContainer = spec.danger.copy(alpha = 0.18f), onErrorContainer = spec.text,
        outline = spec.surfaceEdge,
    ) else darkColorScheme(
        primary = spec.accent, onPrimary = Color(0xFF0A0C14),
        primaryContainer = spec.accent.copy(alpha = 0.22f), onPrimaryContainer = spec.text,
        background = spec.ambientMiddle, onBackground = spec.text,
        surface = spec.surfaceTint, onSurface = spec.text,
        surfaceVariant = spec.ambientTop, onSurfaceVariant = spec.muted,
        error = spec.danger, errorContainer = spec.danger.copy(alpha = 0.18f), onErrorContainer = spec.text,
        outline = spec.surfaceEdge,
    )
    CompositionLocalProvider(
        LocalLiquidSpec provides spec,
        LocalGlassPrefs provides glassPrefs,
        LocalDensity provides Density(density.density, fontScale.coerceIn(0.85f, 1.25f)),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
