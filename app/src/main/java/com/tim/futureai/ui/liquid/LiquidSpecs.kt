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
import com.tim.futureai.domain.settings.AccentColor

private fun flatMaterials(light: Boolean): Map<GlassMaterial, LiquidMaterialSpec> = materials(
    glassTint = if (light) Color.White else Color(0xFF101010),
    glassTintAlpha = 0.98f,
    edge = if (light) Color(0xFF111111) else Color.White,
    edgeAlpha = 0.035f,
    darkTint = if (light) Color(0xFF222222) else Color.Black,
    accentTint = if (light) Color(0xFFEAEAEA) else Color(0xFF242424),
    blurScale = 0f,
).mapValues { (_, material) -> material.copy(specularAlpha = 0f, refractionBandDp = 0f) }

/** True monochrome bases; the accent is layered onto controls and status tokens separately. */
val MonochromeBlackSpec: LiquidSpec = run {
    val m = flatMaterials(light = false)
    LiquidSpec(
        visualTheme = LiquidVisualTheme.MONOCHROME_BLACK, label = "纯黑", description = "纯黑基底 · 可选强调色",
        isLight = false,
        ambientTop = Color.Black, ambientMiddle = Color.Black, ambientBottom = Color.Black,
        blobPrimary = Color.Transparent, blobSecondary = Color.Transparent, blobTertiary = Color.Transparent,
        backdropTextureAlpha = 0f,
        text = Color.White, muted = Color(0xFFAAAAAA),
        accent = Color(0xFF737373), accentBright = Color(0xFFA5A5A5), cyan = Color(0xFF999999),
        success = Color(0xFF76A88A), danger = Color(0xFFD77B7B), warning = Color(0xFFC29A61),
        surfaceTint = Color(0xFF111111), surfaceTintAlpha = 1f, surfaceEdge = Color(0xFF292929),
        codeBlockBackground = Color(0xFF080808), codeBlockHeader = Color(0xFF151515), codeText = Color(0xFFE2E2E2),
        clear = m.getValue(GlassMaterial.CLEAR), frosted = m.getValue(GlassMaterial.FROSTED),
        liquid = m.getValue(GlassMaterial.LIQUID), dark = m.getValue(GlassMaterial.DARK),
        accentGlass = m.getValue(GlassMaterial.ACCENT),
    )
}

val MonochromeWhiteSpec: LiquidSpec = run {
    val m = flatMaterials(light = true)
    LiquidSpec(
        visualTheme = LiquidVisualTheme.MONOCHROME_WHITE, label = "纯白", description = "纯白基底 · 可选强调色",
        isLight = true,
        ambientTop = Color.White, ambientMiddle = Color.White, ambientBottom = Color(0xFFFAFAFA),
        blobPrimary = Color.Transparent, blobSecondary = Color.Transparent, blobTertiary = Color.Transparent,
        backdropTextureAlpha = 0f,
        text = Color(0xFF111111), muted = Color(0xFF666666),
        accent = Color(0xFF737373), accentBright = Color(0xFF4A4A4A), cyan = Color(0xFF777777),
        success = Color(0xFF28734E), danger = Color(0xFFB42335), warning = Color(0xFF8F601A),
        surfaceTint = Color(0xFFF7F7F7), surfaceTintAlpha = 1f, surfaceEdge = Color(0xFFE5E5E5),
        codeBlockBackground = Color(0xFF171717), codeBlockHeader = Color(0xFF242424), codeText = Color(0xFFE8E8E8),
        clear = m.getValue(GlassMaterial.CLEAR), frosted = m.getValue(GlassMaterial.FROSTED),
        liquid = m.getValue(GlassMaterial.LIQUID), dark = m.getValue(GlassMaterial.DARK),
        accentGlass = m.getValue(GlassMaterial.ACCENT),
    )
}

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
        blurDp = 10f * blurScale, tint = glassTint, tintAlpha = glassTintAlpha * 0.30f,
        edgeHighlight = edge, edgeAlpha = edgeAlpha * 0.40f, specularAlpha = 0.035f, refractionBandDp = 0f,
    ),
    GlassMaterial.FROSTED to LiquidMaterialSpec(
        blurDp = 22f * blurScale, tint = glassTint, tintAlpha = glassTintAlpha * 0.58f,
        edgeHighlight = edge, edgeAlpha = edgeAlpha * 0.72f, specularAlpha = 0.075f, refractionBandDp = 10f,
    ),
    GlassMaterial.LIQUID to LiquidMaterialSpec(
        blurDp = 18f * blurScale, tint = glassTint, tintAlpha = glassTintAlpha * 0.34f,
        edgeHighlight = edge, edgeAlpha = (edgeAlpha * 0.90f).coerceAtMost(1f), specularAlpha = 0.12f, refractionBandDp = 20f,
    ),
    GlassMaterial.DARK to LiquidMaterialSpec(
        blurDp = 26f * blurScale, tint = darkTint, tintAlpha = 0.66f,
        edgeHighlight = edge, edgeAlpha = edgeAlpha * 0.55f, specularAlpha = 0.035f, refractionBandDp = 0f,
    ),
    GlassMaterial.ACCENT to LiquidMaterialSpec(
        blurDp = 20f * blurScale, tint = accentTint, tintAlpha = 0.5f,
        edgeHighlight = edge, edgeAlpha = edgeAlpha * 0.75f, specularAlpha = 0.10f, refractionBandDp = 0f,
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
        glassTint = Color(0xFF191B1F), glassTintAlpha = 0.92f,
        edge = Color(0xFFB9C1CC), edgeAlpha = 0.12f,
        darkTint = Color(0xFF101114), accentTint = Color(0xFF354151),
        blurScale = 0.55f,
    )
    LiquidSpec(
        visualTheme = LiquidVisualTheme.OBSIDIAN,
        label = "Obsidian", description = "石墨黑 · 克制专业",
        isLight = false,
        ambientTop = Color(0xFF212121), ambientMiddle = Color(0xFF212121), ambientBottom = Color(0xFF212121),
        blobPrimary = Color.Transparent, blobSecondary = Color.Transparent, blobTertiary = Color.Transparent,
        backdropTextureAlpha = 0f,
        text = Color(0xFFECECEC), muted = Color(0xFFABABAB),
        accent = Color(0xFF8EA2BA), accentBright = Color(0xFFB0C0D2), cyan = Color(0xFF9AAFC2),
        success = Color(0xFF7FCDA9), danger = Color(0xFFF08C9E), warning = Color(0xFFE8B96A),
        surfaceTint = Color(0xFF2F2F2F), surfaceTintAlpha = 1f, surfaceEdge = Color(0xFF3D3D3D),
        codeBlockBackground = Color(0xFF111316), codeBlockHeader = Color(0xFF1B1E22), codeText = Color(0xFFD6DCE4),
        clear = m.getValue(GlassMaterial.CLEAR), frosted = m.getValue(GlassMaterial.FROSTED),
        liquid = m.getValue(GlassMaterial.LIQUID), dark = m.getValue(GlassMaterial.DARK),
        accentGlass = m.getValue(GlassMaterial.ACCENT),
    )
}

val MidnightSpec: LiquidSpec = ObsidianSpec.copy(
    visualTheme = LiquidVisualTheme.MIDNIGHT, label = "午夜蓝", description = "深蓝黑 · 冷静聚焦",
    ambientTop = Color(0xFF111722), ambientMiddle = Color(0xFF0A0E15), ambientBottom = Color(0xFF070A10),
    surfaceTint = Color(0xFF141A23), surfaceEdge = Color(0xFF252E3A),
    codeBlockBackground = Color(0xFF0A0E14), codeBlockHeader = Color(0xFF171E28),
)

val DeepSeaSpec: LiquidSpec = ObsidianSpec.copy(
    visualTheme = LiquidVisualTheme.DEEP_SEA, label = "深海", description = "深石墨青 · 安静清晰",
    ambientTop = Color(0xFF121A1A), ambientMiddle = Color(0xFF0B1111), ambientBottom = Color(0xFF070C0C),
    surfaceTint = Color(0xFF151C1C), surfaceEdge = Color(0xFF263231),
    codeBlockBackground = Color(0xFF0B1110), codeBlockHeader = Color(0xFF17201F),
)

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

fun LiquidVisualTheme.spec(accentColor: AccentColor = AccentColor.NEUTRAL): LiquidSpec {
    val base = when (this) {
        LiquidVisualTheme.MONOCHROME_BLACK -> MonochromeBlackSpec
        LiquidVisualTheme.MONOCHROME_WHITE -> MonochromeWhiteSpec
        LiquidVisualTheme.GRAPHITE, LiquidVisualTheme.OBSIDIAN -> ObsidianSpec.copy(visualTheme = this, label = "石墨", description = "深灰黑 · 克制专业")
        LiquidVisualTheme.MIDNIGHT -> MidnightSpec
        LiquidVisualTheme.DEEP_SEA, LiquidVisualTheme.AURORA -> DeepSeaSpec.copy(visualTheme = this)
        LiquidVisualTheme.ARCTIC -> ArcticSpec
        LiquidVisualTheme.LIQUID_INDIGO -> LiquidIndigoSpec
    }
    val accent = if (accentColor == AccentColor.NEUTRAL) base.text else Color(accentColor.primaryHex)
    // A neutral optical material across every palette; accents belong to controls.
    val frost = LiquidMaterialSpec(
        blurDp = 20f, tint = base.ambientMiddle, tintAlpha = if (base.isLight) 0.62f else 0.56f,
        edgeHighlight = if (base.isLight) Color.Black else Color.White,
        edgeAlpha = if (base.isLight) 0.055f else 0.075f,
        specularAlpha = if (base.isLight) 0.07f else 0.018f, refractionBandDp = 0f,
    )
    val lens = frost.copy(blurDp = 18f, tint = base.surfaceTint,
        tintAlpha = if (base.isLight) 0.44f else 0.38f,
        edgeAlpha = if (base.isLight) 0.085f else 0.12f,
        specularAlpha = if (base.isLight) 0.13f else 0.028f, refractionBandDp = 10f)
    return base.copy(
        clear = frost.copy(tintAlpha = 0.50f), frosted = frost, liquid = lens,
        accent = accent,
        accentBright = if (accentColor == AccentColor.NEUTRAL) base.text else Color(accentColor.brightHex),
        cyan = if (accentColor == AccentColor.NEUTRAL) base.text else Color(accentColor.secondaryHex),
        accentGlass = lens.copy(tint = base.surfaceTint),
    )
}

/** Root theme composable: provides Liquid tokens plus a mapped Material3 scheme. */
@Composable
fun GatherLiquidTheme(
    visualTheme: LiquidVisualTheme,
    accentColor: AccentColor = AccentColor.NEUTRAL,
    fontScale: Float = 1f,
    glassPrefs: GlassRenderPrefs = GlassRenderPrefs(intensity = 0.18f),
    content: @Composable () -> Unit,
) {
    val spec = visualTheme.spec(accentColor)
    val density = LocalDensity.current
    val onAccent = if (accentColor == AccentColor.NEUTRAL) spec.ambientMiddle else Color.White
    val scheme = if (spec.isLight) lightColorScheme(
        primary = spec.accent, onPrimary = onAccent,
        secondary = spec.accent, onSecondary = onAccent, secondaryContainer = spec.surfaceTint, onSecondaryContainer = spec.text,
        tertiary = spec.accent, onTertiary = onAccent,
        surfaceContainer = spec.surfaceTint, surfaceContainerHigh = spec.surfaceTint, surfaceContainerHighest = spec.surfaceTint,
        surfaceContainerLow = spec.ambientMiddle, surfaceContainerLowest = spec.ambientMiddle, surfaceTint = Color.Transparent,
        primaryContainer = spec.accent.copy(alpha = 0.22f), onPrimaryContainer = spec.text,
        background = spec.ambientMiddle, onBackground = spec.text,
        surface = spec.surfaceTint, onSurface = spec.text,
        surfaceVariant = spec.ambientTop, onSurfaceVariant = spec.muted,
        error = spec.danger, errorContainer = spec.danger.copy(alpha = 0.18f), onErrorContainer = spec.text,
        outline = spec.surfaceEdge,
    ) else darkColorScheme(
        primary = spec.accent, onPrimary = onAccent,
        secondary = spec.accent, onSecondary = onAccent, secondaryContainer = spec.surfaceTint, onSecondaryContainer = spec.text,
        tertiary = spec.accent, onTertiary = onAccent,
        surfaceContainer = spec.surfaceTint, surfaceContainerHigh = spec.surfaceTint, surfaceContainerHighest = spec.surfaceTint,
        surfaceContainerLow = spec.ambientMiddle, surfaceContainerLowest = spec.ambientMiddle, surfaceTint = Color.Transparent,
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
        LocalDensity provides Density(density.density, density.fontScale * fontScale.coerceIn(0.85f, 1.25f)),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
