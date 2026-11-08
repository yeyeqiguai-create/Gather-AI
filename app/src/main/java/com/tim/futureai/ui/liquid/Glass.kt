package com.tim.futureai.ui.liquid

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
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
        try { layer.record { this@onDrawWithContent.drawContent() } }
        finally { state.recording = false }
        drawLayer(layer)
    }
}

@RequiresApi(33)
private class RefractionShaderHolder(val runtime: RuntimeShader, val optical: Boolean) {
    private var logged = false
    fun effect(size: Size, bandPx: Float, strength: Float, corner: Float, pointer: Offset, pressure: Float): android.graphics.RenderEffect {
        runtime.setFloatUniform("uSize", floatArrayOf(size.width, size.height))
        runtime.setFloatUniform("uBand", bandPx)
        runtime.setFloatUniform("uStrength", strength)
        if (optical) {
            runtime.setFloatUniform("uCorner", corner)
            runtime.setFloatUniform("uPointer", pointer.x, pointer.y)
            runtime.setFloatUniform("uPressure", pressure)
        }
        val effect = android.graphics.RenderEffect.createRuntimeShaderEffect(runtime, "uContent")
        if (!logged && optical) { android.util.Log.i("GatherGlass", "Rounded lens refraction active"); logged = true }
        return effect
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
    shape: Shape = com.tim.futureai.ui.theme.ContinuousCornerShape(24.dp),
    opticalLens: Boolean = false,
): Modifier = composed {
    val state = LocalGlassState.current
    val spec = LocalLiquidSpec.current
    val prefs = LocalGlassPrefs.current
    val screenDensity = LocalDensity.current
    val materialSpec = spec.material(material)
    val renderMode = GlassRenderPolicy.surfaceMode(
        sdkInt = Build.VERSION.SDK_INT,
        prefs = prefs,
        hasSource = state.source != null,
        blurDp = materialSpec.blurDp,
        refractionBandDp = if (material == GlassMaterial.LIQUID) materialSpec.refractionBandDp else 0f,
    )
    val refractionShader = remember(renderMode, opticalLens) {
        if (Build.VERSION.SDK_INT >= 33 && renderMode == GlassRenderMode.REFRACTION) {
            runCatching { RefractionShaderHolder(RuntimeShader(if (opticalLens) AGSL_REFRACTION else AGSL_LEGACY_REFRACTION), opticalLens) }.onFailure { android.util.Log.w("GatherGlass", "Lens shader compilation failed", it) }.getOrNull()
        } else null
    }
    val blurLayer = if (renderMode != GlassRenderMode.TINT) rememberGraphicsLayer() else null
    val refractionLayer = if (refractionShader != null) rememberGraphicsLayer() else null
    var panelBounds by remember { mutableStateOf<Rect?>(null) }
    var pointerPosition by remember { mutableStateOf(Offset.Unspecified) }
    var pressed by remember { mutableStateOf(false) }
    val trackPointer = refractionShader != null && opticalLens && !prefs.reduceMotion
    val depth = if (trackPointer) {
        val animatedDepth by animateFloatAsState(
            if (pressed) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.80f, stiffness = 380f),
            label = "liquid-lens-pressure",
        )
        animatedDepth
    } else 0f
    // Blur hides high-frequency detail anyway. Half-resolution sampling cuts
    // offscreen pixel work by 75%; children and the final edge remain native.
    val sampleScale = 0.5f
    val blurRadiusPx = materialSpec.blurDp * (0.5f + prefs.intensity) * screenDensity.density * sampleScale
    val refractionBandPx = materialSpec.refractionBandDp * screenDensity.density
    val refractionStrength = (if (opticalLens) 1f else 0.55f) * prefs.intensity.coerceIn(0.25f, 1f)
    val blurEffect = remember(renderMode, blurRadiusPx) {
        if (Build.VERSION.SDK_INT >= 31 && renderMode != GlassRenderMode.TINT && blurRadiusPx > 0f) android.graphics.RenderEffect.createBlurEffect(
            blurRadiusPx, blurRadiusPx, android.graphics.Shader.TileMode.CLAMP,
        ).asComposeRenderEffect() else null
    }

    var surfaceModifier = this
    if (blurLayer != null) {
        surfaceModifier = surfaceModifier.onGloballyPositioned { panelBounds = it.boundsInRoot() }
    }
    if (trackPointer) {
        surfaceModifier = surfaceModifier.pointerInput(Unit) {
            try {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.firstOrNull()?.let { pointerPosition = it.position }
                        pressed = event.changes.any { it.pressed }
                    }
                }
            } finally {
                // A preference change or detached surface cancels this input
                // node; never retain a pressed lens when it is re-enabled.
                pressed = false
                pointerPosition = Offset.Unspecified
            }
        }
    }
    surfaceModifier
        // Crisp clip for the whole glass surface (tint, effects and children).
        .graphicsLayer {
            this.shape = shape
            clip = true
        }
        .drawWithCache {
            val m = materialSpec
            val bg = state.source
            val bounds = panelBounds
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
                // Evaluate while drawing: a cached decision must never sample
                // the same layer that is currently being recorded.
                val useBlur = blurLayer != null && bg != null && bounds != null &&
                    !state.recording && size.width > 0 && size.height > 0
                // 1. Blurred (+ refracted) background sampling, composited as
                //    image layers so the panel's own content stays crisp.
                if (Build.VERSION.SDK_INT >= 31 && useBlur) {
                    val panelOffset = bounds.topLeft - state.stagePosition
                    val sampleSize = IntSize(kotlin.math.ceil(size.width * sampleScale).toInt(), kotlin.math.ceil(size.height * sampleScale).toInt())
                    blurLayer.record(density = this, size = sampleSize, layoutDirection = layoutDirection) {
                        scale(sampleScale, pivot = Offset.Zero) {
                            translate(-panelOffset.x, -panelOffset.y) { drawLayer(bg) }
                        }
                    }
                    blurLayer.renderEffect = blurEffect
                    var drewRefraction = false
                    if (Build.VERSION.SDK_INT >= 33 && refractionLayer != null && refractionShader != null) {
                        refractionLayer.record(density = this, size = sampleSize, layoutDirection = layoutDirection) { drawLayer(blurLayer) }
                        refractionLayer.renderEffect = runCatching {
                            val corner = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
                            val pointer = if (!prefs.reduceMotion && pointerPosition.isSpecified) Offset((pointerPosition.x / size.width).coerceIn(0f, 1f), (pointerPosition.y / size.height).coerceIn(0f, 1f)) else Offset(0.20f, 0.08f)
                            refractionShader.effect(Size(sampleSize.width.toFloat(), sampleSize.height.toFloat()), refractionBandPx * sampleScale, refractionStrength, (corner + depth * 2.5f * screenDensity.density) * sampleScale, pointer, depth)
                        }.onFailure { android.util.Log.w("GatherGlass", "Lens render effect failed", it) }.getOrNull()?.asComposeRenderEffect()
                        if (refractionLayer.renderEffect != null) {
                            scale(1f / sampleScale, pivot = Offset.Zero) { drawLayer(refractionLayer) }
                            drewRefraction = true
                        }
                    }
                    if (!drewRefraction) scale(1f / sampleScale, pivot = Offset.Zero) { drawLayer(blurLayer) }
                }
                // 2. Tint + specular + edge highlight above the blurred sample.
                val tintAlpha = if (useBlur) m.tintAlpha else 1f
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

/**
 * Tint-only soft surface for content that is part of the recorded source
 * (scrolling cards, message bubbles). Fast, no blur, keeps hierarchy.
 */
fun Modifier.softSurface(
    shape: Shape = com.tim.futureai.ui.theme.ContinuousCornerShape(18.dp),
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
