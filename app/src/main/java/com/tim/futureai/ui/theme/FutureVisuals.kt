package com.tim.futureai.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object FuturePalette {
    val Night = Color(0xFF080A12)
    val DeepBlue = Color(0xFF10152A)
    val Panel = Color(0xCC171C2D)
    val PanelSoft = Color(0x991C2236)
    val Edge = Color(0x36DDE3FF)
    val Violet = Color(0xFF9B7BFF)
    val VioletBright = Color(0xFFB7A1FF)
    val Cyan = Color(0xFF7BD9F5)
    val Text = Color(0xFFF4F2FF)
    val Muted = Color(0xFFA6AAC0)
    val Success = Color(0xFF8AE0C2)
    val Danger = Color(0xFFFF9BAF)
}

@Composable
fun FutureBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to FuturePalette.DeepBlue,
                    0.48f to FuturePalette.Night,
                    1f to Color(0xFF090B13),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(
                    colors = listOf(Color(0x332F6AE6), Color.Transparent),
                    center = Offset(size.width * 0.92f, size.height * 0.12f),
                    radius = size.width * 0.82f,
                ),
                radius = size.width * 0.82f,
                center = Offset(size.width * 0.92f, size.height * 0.12f),
            )
            drawCircle(
                Brush.radialGradient(
                    colors = listOf(Color(0x292F1B72), Color.Transparent),
                    center = Offset(size.width * 0.02f, size.height * 0.62f),
                    radius = size.width * 0.72f,
                ),
                radius = size.width * 0.72f,
                center = Offset(size.width * 0.02f, size.height * 0.62f),
            )
        }
        content()
    }
}

fun Modifier.glassPanel(shape: RoundedCornerShape = RoundedCornerShape(24.dp)): Modifier =
    background(
        Brush.verticalGradient(listOf(Color(0xD91B2032), Color(0xB3121728))),
        shape,
    ).border(1.dp, Brush.verticalGradient(listOf(Color(0x55F1EEFF), Color(0x14B5C2FF))), shape)

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
                line(9f, 4f, 15f, 4f, 1.8f)
                drawRoundRect(tint, topLeft = point(6f, 8f), size = androidx.compose.ui.geometry.Size(12f * unit, 13f * unit), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * unit), style = Stroke(1.7f * unit))
                line(10f, 11f, 10f, 17f, 1.4f); line(14f, 11f, 14f, 17f, 1.4f)
            }
            "check" -> {
                drawCircle(tint, 9f * unit, center = point(12f, 12f), style = Stroke(1.7f * unit))
                line(7.5f, 12f, 10.5f, 15f, 1.8f)
                line(10.5f, 15f, 16.8f, 8.5f, 1.8f)
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
        }
    }
}
