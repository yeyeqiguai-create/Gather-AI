package com.tim.futureai.ui.liquid

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * One Liquid stage: ambient backdrop + scrolling content are recorded as the
 * glass source; fixed glass surfaces (headers, composer, sheets) live in the
 * overlay and blur what is actually behind them.
 */
@Composable
fun LiquidStage(
    modifier: Modifier = Modifier,
    source: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    val state = remember { LiquidGlassState() }
    CompositionLocalProvider(LocalGlassState provides state) {
        Box(
            modifier
                .fillMaxSize()
                .onGloballyPositioned { state.stagePosition = it.boundsInRoot().topLeft },
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .liquidSource(state),
            ) {
                AmbientBackdrop()
                source()
            }
            overlay()
        }
    }
}

/**
 * Compat entry for screens that have not been split into source/overlay:
 * everything renders directly on the themed backdrop and glass surfaces
 * degrade to tint-only fills (no source recording, no blur cost).
 */
@Composable
fun LiquidBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalGlassState provides remember { LiquidGlassState() }) {
        Box(modifier.fillMaxSize()) {
            AmbientBackdrop()
            content()
        }
    }
}

/**
 * Ambient backdrop: theme gradient, soft light blobs, optional fine grain
 * texture, and an optional slow aurora drift when the user enables dynamic
 * backgrounds and motion.
 */
@Composable
fun AmbientBackdrop() {
    val spec = LocalLiquidSpec.current
    val prefs = LocalGlassPrefs.current
    val drift = prefs.dynamicBackground && !prefs.reduceMotion
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Restart),
        label = "aurora-phase",
    )
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(
            0f to spec.ambientTop,
            0.52f to spec.ambientMiddle,
            1f to spec.ambientBottom,
        ))
        val w = size.width
        val h = size.height
        val blobs = if (drift) {
            listOf(
                Triple(spec.blobPrimary, Offset(w * (0.86f + 0.05f * sin(phase)), h * (0.10f + 0.03f * sin(phase * 1.3f))), w * 0.82f),
                Triple(spec.blobSecondary, Offset(w * (0.04f + 0.04f * sin(phase * 0.8f + 1f)), h * (0.58f + 0.05f * sin(phase + 2f))), w * 0.74f),
                Triple(spec.blobTertiary, Offset(w * (0.52f + 0.06f * sin(phase * 0.6f + 3f)), h * (0.94f + 0.03f * sin(phase * 1.1f))), w * 0.66f),
            )
        } else {
            listOf(
                Triple(spec.blobPrimary, Offset(w * 0.88f, h * 0.10f), w * 0.82f),
                Triple(spec.blobSecondary, Offset(w * 0.05f, h * 0.60f), w * 0.74f),
                Triple(spec.blobTertiary, Offset(w * 0.55f, h * 0.96f), w * 0.66f),
            )
        }
        blobs.forEach { (color, center, radius) ->
            drawCircle(
                brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
                radius = radius,
                center = center,
            )
        }
        if (spec.backdropTextureAlpha > 0f) {
            // Fine grain: sparse hairline strokes keep the backdrop from banding.
            val step = 4.dp.toPx()
            var y = 0f
            var rowIndex = 0
            while (y < h) {
                val path = Path()
                var x = 0f
                var started = false
                while (x < w) {
                    val offset = if (drift) sin((x / w * 6f) + phase + rowIndex) * 0.8f else 0f
                    if (!started) { path.moveTo(x, y + offset); started = true } else path.lineTo(x, y + offset)
                    x += step * 2f
                }
                drawPath(path, Color.White.copy(alpha = spec.backdropTextureAlpha), style = androidx.compose.ui.graphics.drawscope.Stroke(0.6f))
                y += step
                rowIndex++
            }
        }
    }
}
