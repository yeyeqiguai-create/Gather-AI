package com.tim.futureai.feature.link

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tim.futureai.ui.liquid.LocalLiquidSpec

internal val LocalLinkReducedMotion = staticCompositionLocalOf { false }

/** Keep the link surface inside Gather AI's shared color system. */
@Composable
internal fun LinkGlassScene(reducedMotion: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val spec = LocalLiquidSpec.current
    CompositionLocalProvider(LocalLinkReducedMotion provides reducedMotion) {
        Box(modifier.fillMaxSize().background(spec.ambientMiddle), content = content)
    }
}

@Composable
internal fun LiquidButton(onClick: () -> Unit, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = com.tim.futureai.ui.theme.ContinuousCornerShape(12.dp), content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Button(onClick = onClick, modifier = modifier, shape = shape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), content = content)
}
