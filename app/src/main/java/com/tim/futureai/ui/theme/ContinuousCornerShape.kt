package com.tim.futureai.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** A superellipse corner (n=4) with the calm, continuous curvature used by modern UI. */
class ContinuousCornerShape(
    private val radius: Dp = 20.dp,
    private val exponent: Float = 4f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }.coerceAtLeast(0f)
            .coerceAtMost(minOf(size.width, size.height) / 2f)
        if (r <= 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
        val power = 2f / exponent.coerceAtLeast(2f)
        fun corner(cx: Float, cy: Float, start: Float, end: Float, rx: Float = r, ry: Float = r): Path.() -> Unit = {
            for (i in 1..24) {
                val angle = Math.toRadians(start + (end - start) * i / 24.0)
                val c = cos(angle).toFloat()
                val s = sin(angle).toFloat()
                lineTo(cx + rx * signPower(c, power), cy + ry * signPower(s, power))
            }
        }
        val path = Path().apply {
            moveTo(r, 0f)
            lineTo(size.width - r, 0f)
            corner(size.width - r, r, -90f, 0f).invoke(this)
            lineTo(size.width, size.height - r)
            corner(size.width - r, size.height - r, 0f, 90f).invoke(this)
            lineTo(r, size.height)
            corner(r, size.height - r, 90f, 180f).invoke(this)
            lineTo(0f, r)
            corner(r, r, 180f, 270f).invoke(this)
            close()
        }
        return Outline.Generic(path)
    }

    private fun signPower(value: Float, power: Float): Float =
        kotlin.math.sign(value) * abs(value).pow(power)
}
