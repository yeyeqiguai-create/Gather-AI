package com.tim.futureai.ui.liquid

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Shared state for one Liquid glass stage. The stage records its source
 * content into a GraphicsLayer; glass surfaces sample that layer with a real
 * blur RenderEffect, aligned by root-relative offsets.
 */
class LiquidGlassState {
    internal var source: androidx.compose.ui.graphics.layer.GraphicsLayer? = null
    internal var stagePosition: Offset = Offset.Zero
    internal var recording = false

    /** RenderEffect-based blur requires API 31+. */
    val blurCapable: Boolean get() = Build.VERSION.SDK_INT >= 31

    /** AGSL edge refraction requires API 33+. */
    val refractionCapable: Boolean get() = Build.VERSION.SDK_INT >= 33
}

val LocalGlassState = compositionLocalOf { LiquidGlassState() }

/**
 * Records the content this modifier is attached to as the stage's glass
 * source. Attach it to the background + scrolling content; glass surfaces in
 * the stage overlay then blur what is actually behind them.
 */
fun Modifier.liquidSource(state: LiquidGlassState): Modifier = this.drawWithCache {
    val layer = state.source
    onDrawWithContent {
        if (layer == null) {
            drawContent()
            return@onDrawWithContent
        }
        state.recording = true
        layer.record { this@onDrawWithContent.drawContent() }
        state.recording = false
        drawLayer(layer)
    }
}

private class RefractionShaderHolder(val runtime: RuntimeShader) {
    fun effect(size: Size, bandPx: Float, strength: Float): android.graphics.RenderEffect {
        runtime.setFloatUniform("uSize", floatArrayOf(size.width, size.height))
        runtime.setFloatUniform("uBand", bandPx)
        runtime.setFloatUniform("uStrength", strength)
        return android.graphics.RenderEffect.createRuntimeShaderEffect(runtime, "uContent")
    }
}

/**
 * Liquid glass surface: real background blur + tint + specular + edge
 * highlight, with optional AGSL edge refraction for the LIQUID material.
 *
 * Node structure (outer → inner):
 * 1. crisp clip to the panel shape
 * 2. tint + specular + edge highlight drawn on top of the blurred result
 * 3. blur node (RenderEffect, clipped to shape)
 * 4. refraction node (AGSL, LIQUID material only, API 33+)
 * 5. background sampling: the recorded source layer translated so the region
 *    behind this panel aligns with the panel
 *
 * While the stage records its source (when this surface is part of the
 * recorded content, e.g. scrolling cards), glass degrades to a tint-only
 * fill. The same fallback applies below API 31, in low-performance mode, or
 * when no stage source exists (dialogs).
 */
fun Modifier.glassSurface(
    material: GlassMaterial = GlassMaterial.FROSTED,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
): Modifier = composed {
    val state = LocalGlassState.current
    val spec = LocalLiquidSpec.current
    val prefs = LocalGlassPrefs.current
    val screenDensity = LocalDensity.current
    val refractionShader = remember(material) {
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching { RefractionShaderHolder(RuntimeShader(AGSL_REFRACTION)) }.getOrNull()
        } else null
    }
    val blurLayer = rememberGraphicsLayer()
    val refractionLayer = rememberGraphicsLayer()
    var panelBounds by remember { mutableStateOf<Rect?>(null) }
    val materialSpec = spec.material(material)
    val blurRadiusPx = materialSpec.blurDp * (0.5f + prefs.intensity) * screenDensity.density
    val refractionBandPx = materialSpec.refractionBandDp * screenDensity.density
    val refractionStrength = 0.55f * prefs.intensity.coerceIn(0.25f, 1f)

    onGloballyPositioned { panelBounds = it.boundsInRoot() }
        // Crisp clip for the whole glass surface (tint, effects and children).
        .graphicsLayer {
            this.shape = shape
            clip = true
        }
        .drawWithCache {
            val m = materialSpec
            val bg = state.source
            val bounds = panelBounds
            val useBlur = canBlur(state, prefs) && m.blurDp > 0f && bg != null && bounds != null
            val useRefraction = useBlur && m.refractionBandDp > 0f && state.refractionCapable && refractionShader != null
            val outline = shape.createOutline(size, layoutDirection, this)
            val edgePath = outlinePath(outline)
            val specular = Brush.verticalGradient(
                0f to Color.White.copy(alpha = m.specularAlpha),
                0.35f to Color.Transparent,
            )
            val edgeBrush = Brush.verticalGradient(
                0f to m.edgeHighlight.copy(alpha = m.edgeAlpha),
                0.6f to m.edgeHighlight.copy(alpha = m.edgeAlpha * 0.35f),
                1f to m.edgeHighlight.copy(alpha = m.edgeAlpha * 0.15f),
            )
            onDrawWithContent {
                // 1. Blurred (+ refracted) background sampling, composited as
                //    image layers so the panel's own content stays crisp.
                if (useBlur && bg != null && bounds != null) {
                    val panelOffset = bounds.topLeft - state.stagePosition
                    blurLayer.record(density = this, size = IntSize(size.width.toInt(), size.height.toInt()), layoutDirection = layoutDirection) {
                        translate(-panelOffset.x, -panelOffset.y) { drawLayer(bg) }
                    }
                    blurLayer.renderEffect = android.graphics.RenderEffect.createBlurEffect(
                        blurRadiusPx, blurRadiusPx, android.graphics.Shader.TileMode.CLAMP,
                    ).asComposeRenderEffect()
                    var drewRefraction = false
                    if (useRefraction) {
                        refractionLayer.record(density = this, size = IntSize(size.width.toInt(), size.height.toInt()), layoutDirection = layoutDirection) { drawLayer(blurLayer) }
                        refractionLayer.renderEffect = runCatching {
                            refractionShader.effect(size, refractionBandPx, refractionStrength)
                        }.getOrNull()?.asComposeRenderEffect()
                        if (refractionLayer.renderEffect != null) {
                            drawLayer(refractionLayer)
                            drewRefraction = true
                        }
                    }
                    if (!drewRefraction) drawLayer(blurLayer)
                }
                // 2. Tint + specular + edge highlight above the blurred sample.
                val tintAlpha = if (useBlur) m.tintAlpha else (m.tintAlpha * 1.35f).coerceAtMost(0.94f)
                drawPath(edgePath, m.tint, tintAlpha)
                drawRect(brush = specular)
                drawPath(edgePath, edgeBrush, style = Stroke(width = 1.dp.toPx()))
                // 3. Panel content, crisp on top.
                drawContent()
            }
        }
}

private fun outlinePath(outline: Outline): Path = Path().apply {
    when (outline) {
        is Outline.Rectangle -> addRect(outline.rect)
        is Outline.Rounded -> addRoundRect(outline.roundRect)
        is Outline.Generic -> addPath(outline.path)
    }
}

private fun DrawScope.drawOutlineCompat(outline: Outline, color: Color, alpha: Float = 1f) {
    drawPath(outlinePath(outline), color, alpha)
}

private fun DrawScope.drawOutlineCompat(outline: Outline, brush: Brush, alpha: Float = 1f, style: DrawStyle = Fill) {
    drawPath(outlinePath(outline), brush, alpha, style = style)
}

private fun canBlur(state: LiquidGlassState, prefs: GlassRenderPrefs): Boolean =
    state.blurCapable && !prefs.lowPerformance && !state.recording &&
        state.source != null && prefs.intensity > 0.01f

/**
 * Tint-only soft surface for content that is part of the recorded source
 * (scrolling cards, message bubbles). Fast, no blur, keeps hierarchy.
 */
fun Modifier.softSurface(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
): Modifier = composed {
    val spec = LocalLiquidSpec.current
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawWithContent {
            drawOutlineCompat(outline, spec.surfaceTint, spec.surfaceTintAlpha)
            drawOutlineCompat(
                outline,
                Brush.verticalGradient(
                    0f to spec.surfaceEdge,
                    1f to spec.surfaceEdge.copy(alpha = 0.35f),
                ),
                style = Stroke(width = 1.dp.toPx()),
            )
            drawContent()
        }
    }
}
