package com.tim.futureai.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tim.futureai.ui.liquid.AmbientBackdrop
import com.tim.futureai.ui.liquid.GlassMaterial
import com.tim.futureai.ui.liquid.LocalLiquidSpec
import com.tim.futureai.ui.liquid.LiquidIndigoSpec
import com.tim.futureai.ui.liquid.LiquidVisualTheme
import com.tim.futureai.ui.liquid.glassSurface

/**
 * Compatibility layer over the Liquid Reality design system.
 *
 * Existing screens read [FuturePalette] and use [glassPanel]; both now
 * delegate to the active Liquid theme so every screen automatically follows
 * the four-theme system. New code should prefer `ui.liquid` tokens directly.
 */

val LocalFutureColors = staticCompositionLocalOf { LiquidIndigoSpec }

object FuturePalette {
    val Night: Color @Composable get() = LocalLiquidSpec.current.ambientMiddle
    val DeepBlue: Color @Composable get() = LocalLiquidSpec.current.ambientTop
    val Panel: Color @Composable get() = LocalLiquidSpec.current.surfaceTint
    val PanelSoft: Color @Composable get() = LocalLiquidSpec.current.surfaceTint.copy(alpha = LocalLiquidSpec.current.surfaceTintAlpha * 0.75f)
    val Edge: Color @Composable get() = LocalLiquidSpec.current.surfaceEdge
    val Violet: Color @Composable get() = LocalLiquidSpec.current.accent
    val VioletBright: Color @Composable get() = LocalLiquidSpec.current.accentBright
    val Cyan: Color @Composable get() = LocalLiquidSpec.current.cyan
    val Text: Color @Composable get() = LocalLiquidSpec.current.text
    val Muted: Color @Composable get() = LocalLiquidSpec.current.muted
    val Success: Color @Composable get() = LocalLiquidSpec.current.success
    val Danger: Color @Composable get() = LocalLiquidSpec.current.danger
    val Warning: Color @Composable get() = LocalLiquidSpec.current.warning
}

@Composable
fun FutureBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize()) {
        AmbientBackdrop()
        content()
    }
}

/**
 * Legacy glass panel. Routes to the real Liquid glass pipeline; surfaces that
 * are part of a recorded stage source (scrolling cards) automatically degrade
 * to the fast tint-only material.
 */
@Composable
fun Modifier.glassPanel(shape: androidx.compose.foundation.shape.RoundedCornerShape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)): Modifier {
    return glassSurface(GlassMaterial.FROSTED, shape)
}

@Composable
fun FutureGlyph(
    name: String,
    modifier: Modifier = Modifier,
    tint: Color = FuturePalette.Text,
    size: Dp = 22.dp,
) {
    Canvas(modifier.size(size).semantics { contentDescription = name }) {
        val unit = this.size.minDimension / 24f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = 1.8f) {
            drawLine(tint, point(x1, y1), point(x2, y2), strokeWidth = width * unit, cap = StrokeCap.Round)
        }
        when (name) {
            "search" -> {
                drawCircle(tint, 6.5f * unit, center = point(10.5f, 10.5f), style = Stroke(1.8f * unit))
                line(15.3f, 15.3f, 21f, 21f, 1.9f)
            }
            "pin", "pin_filled" -> {
                val path = Path().apply {
                    moveTo(8f * unit, 3f * unit)
                    lineTo(16f * unit, 3f * unit)
                    lineTo(15f * unit, 8f * unit)
                    lineTo(19f * unit, 12f * unit)
                    lineTo(14f * unit, 13f * unit)
                    lineTo(12f * unit, 21f * unit)
                    lineTo(10f * unit, 13f * unit)
                    lineTo(5f * unit, 12f * unit)
                    lineTo(9f * unit, 8f * unit)
                    close()
                }
                drawPath(path, tint, style = if (name == "pin_filled") androidx.compose.ui.graphics.drawscope.Fill else Stroke(1.7f * unit))
            }
            "settings" -> {
                drawCircle(tint, 7.2f * unit, center = point(12f, 12f), style = Stroke(1.8f * unit))
                drawCircle(tint, 2.4f * unit, center = point(12f, 12f), style = Stroke(1.8f * unit))
                for (angle in 0 until 8) {
                    val radians = Math.toRadians(angle * 45.0)
                    val inner = 8.7f
                    val outer = 10.8f
                    line(
                        12f + (kotlin.math.cos(radians) * inner).toFloat(),
                        12f + (kotlin.math.sin(radians) * inner).toFloat(),
                        12f + (kotlin.math.cos(radians) * outer).toFloat(),
                        12f + (kotlin.math.sin(radians) * outer).toFloat(),
                    )
                }
            }
            "back" -> {
                line(19f, 12f, 5f, 12f, 2f)
                line(5f, 12f, 12f, 5f, 2f)
                line(5f, 12f, 12f, 19f, 2f)
            }
            "send" -> {
                val path = Path().apply {
                    moveTo(3f * unit, 11f * unit)
                    lineTo(20.5f * unit, 3.5f * unit)
                    lineTo(15.2f * unit, 21f * unit)
                    lineTo(11.6f * unit, 13.2f * unit)
                    close()
                }
                drawPath(path, tint)
                line(11.6f, 13.2f, 20.5f, 3.5f, 1.1f)
            }
            "eye" -> {
                val path = Path().apply {
                    moveTo(2f * unit, 12f * unit)
                    cubicTo(6f * unit, 5f * unit, 18f * unit, 5f * unit, 22f * unit, 12f * unit)
                    cubicTo(18f * unit, 19f * unit, 6f * unit, 19f * unit, 2f * unit, 12f * unit)
                }
                drawPath(path, tint, style = Stroke(1.8f * unit, cap = StrokeCap.Round))
                drawCircle(tint, 2.5f * unit, center = point(12f, 12f), style = Stroke(1.8f * unit))
            }
            "eye_off" -> {
                line(3f, 3f, 21f, 21f)
                val path = Path().apply {
                    moveTo(5f * unit, 7f * unit)
                    cubicTo(2f * unit, 9f * unit, 1.5f * unit, 12f * unit, 2f * unit, 12f * unit)
                    cubicTo(6f * unit, 19f * unit, 18f * unit, 19f * unit, 22f * unit, 12f * unit)
                    cubicTo(21f * unit, 10f * unit, 20f * unit, 9f * unit, 18f * unit, 7f * unit)
                }
                drawPath(path, tint, style = Stroke(1.7f * unit, cap = StrokeCap.Round))
            }
            "spark" -> {
                line(12f, 2.5f, 12f, 17.5f, 1.7f)
                line(4.5f, 10f, 19.5f, 10f, 1.7f)
                line(19f, 15f, 19f, 22f, 1.4f)
                line(15.5f, 18.5f, 22.5f, 18.5f, 1.4f)
            }
            "stop" -> drawRoundRect(tint, topLeft = point(6f, 6f), size = androidx.compose.ui.geometry.Size(12f * unit, 12f * unit), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * unit))
            "copy" -> {
                drawRoundRect(tint, topLeft = point(8f, 4f), size = androidx.compose.ui.geometry.Size(12f * unit, 14f * unit), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * unit), style = Stroke(1.7f * unit))
                drawRoundRect(tint, topLeft = point(4f, 7f), size = androidx.compose.ui.geometry.Size(12f * unit, 14f * unit), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * unit), style = Stroke(1.7f * unit))
            }
            "retry" -> {
                val path = Path().apply {
                    moveTo(20f * unit, 10f * unit)
                    cubicTo(19f * unit, 5f * unit, 14f * unit, 3f * unit, 9f * unit, 5f * unit)
                    cubicTo(4f * unit, 7f * unit, 3f * unit, 13f * unit, 6f * unit, 17f * unit)
                    cubicTo(9f * unit, 21f * unit, 16f * unit, 21f * unit, 19f * unit, 16f * unit)
                }
                drawPath(path, tint, style = Stroke(1.8f * unit, cap = StrokeCap.Round))
                line(20f, 4f, 20f, 10f, 1.8f)
                line(20f, 10f, 14f, 10f, 1.8f)
            }
            "sessions" -> {
                drawRoundRect(tint, topLeft = point(3f, 4f), size = androidx.compose.ui.geometry.Size(18f * unit, 5f * unit), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * unit), style = Stroke(1.6f * unit))
                drawRoundRect(tint, topLeft = point(3f, 11f), size = androidx.compose.ui.geometry.Size(18f * unit, 5f * unit), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * unit), style = Stroke(1.6f * unit))
                line(7f, 18f, 17f, 18f, 1.6f)
                line(7f, 21f, 17f, 21f, 1.6f)
            }
            "plus" -> { line(12f, 4f, 12f, 20f, 2f); line(4f, 12f, 20f, 12f, 2f) }
            "edit" -> {
                line(5f, 19f, 7f, 13f, 1.8f)
                line(7f, 13f, 17f, 3f, 1.8f)
                line(17f, 3f, 21f, 7f, 1.8f)
                line(21f, 7f, 11f, 17f, 1.8f)
                line(11f, 17f, 5f, 19f, 1.8f)
            }
            "delete" -> {
                line(4f, 7f, 20f, 7f, 1.8f)
                line(9f, 4f, 15f, 4f, (1.8f))
                drawRoundRect(tint, topLeft = point(6f, 8f), size = androidx.compose.ui.geometry.Size(12f * unit, 13f * unit), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * unit), style = Stroke(1.7f * unit))
                line(10f, 11f, 10f, 17f, 1.4f); line(14f, 11f, 14f, 17f, 1.4f)
            }
            "check" -> {
                drawCircle(tint, 9f * unit, center = point(12f, 12f), style = Stroke(1.7f * unit))
                line(7.5f, 12f, 10.5f, 15f, 1.8f)
                line(10.5f, 15f, 16.8f, 8.5f, 1.8f)
            }
            "mic" -> {
                // Capsule body + stand, matching the shared 1.8 stroke weight.
                drawRoundRect(tint, topLeft = point(9f, 3f), size = androidx.compose.ui.geometry.Size(6f * unit, 10f * unit), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * unit), style = Stroke(1.8f * unit))
                val path = Path().apply {
                    moveTo(6f * unit, 11f * unit)
                    cubicTo(6f * unit, 15f * unit, 8.5f * unit, 17f * unit, 12f * unit, 17f * unit)
                    cubicTo(15.5f * unit, 17f * unit, 18f * unit, 15f * unit, 18f * unit, 11f * unit)
                }
                drawPath(path, tint, style = Stroke(1.8f * unit, cap = StrokeCap.Round))
                line(12f, 17f, 12f, 21f, 1.8f)
                line(8.5f, 21f, 15.5f, 21f, 1.8f)
            }
            "chat" -> {
                val path = Path().apply {
                    moveTo(4f * unit, 5f * unit)
                    lineTo(20f * unit, 5f * unit)
                    lineTo(20f * unit, 16f * unit)
                    lineTo(9f * unit, 16f * unit)
                    lineTo(5f * unit, 20f * unit)
                    lineTo(5f * unit, 16f * unit)
                    lineTo(4f * unit, 16f * unit)
                    close()
                }
                drawPath(path, tint, style = Stroke(1.7f * unit, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            "route" -> {
                drawCircle(tint, 3f * unit, center = point(5f, 19f), style = Stroke(1.7f * unit))
                drawCircle(tint, 3f * unit, center = point(19f, 5f), style = Stroke(1.7f * unit))
                val path = Path().apply {
                    moveTo(7.5f * unit, 17f * unit)
                    cubicTo(13f * unit, 15f * unit, 11f * unit, 9f * unit, 16.5f * unit, 7f * unit)
                }
                drawPath(path, tint, style = Stroke(1.7f * unit, cap = StrokeCap.Round))
            }
            "chart" -> {
                line(4f, 20f, 20f, 20f, 1.7f)
                line(7f, 20f, 7f, 13f, 1.7f)
                line(12f, 20f, 12f, 8f, 1.7f)
                line(17f, 20f, 17f, 15f, 1.7f)
            }
            "attach" -> {
                val path = Path().apply {
                    moveTo(16.5f * unit, 8f * unit)
                    lineTo(16.5f * unit, 16f * unit)
                    cubicTo(16.5f * unit, 19f * unit, 7.5f * unit, 19f * unit, 7.5f * unit, 16f * unit)
                    lineTo(7.5f * unit, 7f * unit)
                    cubicTo(7.5f * unit, 4.5f * unit, 14f * unit, 4.5f * unit, 14f * unit, 7f * unit)
                    lineTo(14f * unit, 15f * unit)
                    cubicTo(14f * unit, 16.5f * unit, 10f * unit, 16.5f * unit, 10f * unit, 15f * unit)
                    lineTo(10f * unit, 9f * unit)
                }
                drawPath(path, tint, style = Stroke(1.6f * unit, cap = StrokeCap.Round))
            }
            "download" -> {
                line(12f, 4f, 12f, 15f, 1.8f)
                line(12f, 15f, 7.5f, 10.5f, 1.8f)
                line(12f, 15f, 16.5f, 10.5f, 1.8f)
                line(5f, 19.5f, 19f, 19.5f, 1.8f)
            }
            "database" -> {
                drawOval(tint, topLeft = point(5f, 4f), size = androidx.compose.ui.geometry.Size(14f * unit, 6f * unit), style = Stroke(1.7f * unit))
                val path = Path().apply {
                    moveTo(5f * unit, 7f * unit)
                    lineTo(5f * unit, 17f * unit)
                    cubicTo(5f * unit, 20f * unit, 19f * unit, 20f * unit, 19f * unit, 17f * unit)
                    lineTo(19f * unit, 7f * unit)
                }
                drawPath(path, tint, style = Stroke(1.7f * unit))
                line(5f, 12f, 19f, 12f, 1.4f)
            }
            "pulse" -> {
                val path = Path().apply {
                    moveTo(3f * unit, 12f * unit)
                    lineTo(7f * unit, 12f * unit)
                    lineTo(9.5f * unit, 6f * unit)
                    lineTo(13f * unit, 18f * unit)
                    lineTo(15.5f * unit, 12f * unit)
                    lineTo(21f * unit, 12f * unit)
                }
                drawPath(path, tint, style = Stroke(1.8f * unit, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            "star" -> {
                val path = Path().apply {
                    moveTo(12f * unit, 3f * unit)
                    lineTo(14.6f * unit, 9f * unit)
                    lineTo(21f * unit, 9.6f * unit)
                    lineTo(16f * unit, 13.8f * unit)
                    lineTo(17.6f * unit, 20.5f * unit)
                    lineTo(12f * unit, 16.8f * unit)
                    lineTo(6.4f * unit, 20.5f * unit)
                    lineTo(8f * unit, 13.8f * unit)
                    lineTo(3f * unit, 9.6f * unit)
                    lineTo(9.4f * unit, 9f * unit)
                    close()
                }
                drawPath(path, tint, style = Stroke(1.6f * unit, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            "theme" -> {
                drawCircle(tint, 7f * unit, center = point(12f, 12f), style = Stroke(1.7f * unit))
                drawArc(tint, startAngle = 90f, sweepAngle = 180f, useCenter = true, topLeft = point(8f, 8f), size = androidx.compose.ui.geometry.Size(8f * unit, 8f * unit))
                for (angle in 0 until 8) {
                    val radians = Math.toRadians(angle * 45.0)
                    line(
                        12f + (kotlin.math.cos(radians) * 9.2f).toFloat(),
                        12f + (kotlin.math.sin(radians) * 9.2f).toFloat(),
                        12f + (kotlin.math.cos(radians) * 10.8f).toFloat(),
                        12f + (kotlin.math.sin(radians) * 10.8f).toFloat(),
                    )
                }
            }
            "taskbox" -> {
                drawRoundRect(tint, topLeft = point(8f, 3f), size = androidx.compose.ui.geometry.Size(12f * unit, 18f * unit), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * unit), style = Stroke(1.7f * unit))
                line(11f, 10f, 13f, 12.5f, 1.7f)
                line(13f, 12.5f, 17f, 7.5f, 1.7f)
                line(4f, 7f, 8f, 7f, 1.6f)
                line(4f, 12f, 8f, 12f, 1.6f)
                line(4f, 17f, 8f, 17f, 1.6f)
            }
            "cloud" -> {
                val path = Path().apply {
                    moveTo(7f * unit, 18f * unit)
                    cubicTo(2f * unit, 18f * unit, 2f * unit, 11f * unit, 7f * unit, 10f * unit)
                    cubicTo(8f * unit, 4f * unit, 17f * unit, 4f * unit, 18f * unit, 10f * unit)
                    cubicTo(23f * unit, 10f * unit, 23f * unit, 18f * unit, 18f * unit, 18f * unit)
                    close()
                }
                drawPath(path, tint, style = Stroke(1.6f * unit, cap = StrokeCap.Round))
            }
            "play" -> {
                val path = Path().apply {
                    moveTo(8f * unit, 5f * unit)
                    lineTo(19f * unit, 12f * unit)
                    lineTo(8f * unit, 19f * unit)
                    close()
                }
                drawPath(path, tint, style = Stroke(1.7f * unit, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            "pause" -> {
                line(9f, 5f, 9f, 19f, 2.2f)
                line(15f, 5f, 15f, 19f, 2.2f)
            }
            "shield" -> {
                val path = Path().apply {
                    moveTo(12f * unit, 3f * unit)
                    lineTo(20f * unit, 6f * unit)
                    lineTo(20f * unit, 12f * unit)
                    cubicTo(20f * unit, 17f * unit, 16f * unit, 20f * unit, 12f * unit, 21f * unit)
                    cubicTo(8f * unit, 20f * unit, 4f * unit, 17f * unit, 4f * unit, 12f * unit)
                    lineTo(4f * unit, 6f * unit)
                    close()
                }
                drawPath(path, tint, style = Stroke(1.7f * unit, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}
