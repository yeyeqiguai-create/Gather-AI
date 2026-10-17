package com.tim.futureai.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.futureai.ui.liquid.GlassMaterial
import com.tim.futureai.ui.liquid.LiquidStage
import com.tim.futureai.ui.liquid.LocalLiquidSpec
import com.tim.futureai.ui.liquid.glassSurface
import com.tim.futureai.ui.theme.FutureGlyph
import com.tim.futureai.ui.theme.FuturePalette

/**
 * Liquid Glass Prototype — the verification surface required before the
 * material system is trusted across the app.
 *
 * Shows, against one highly textured scrolling background:
 * - real background sampling and blur updates while content scrolls,
 * - every glass material (Clear / Frosted / Liquid / Dark / Accent),
 * - multi-layer glass and edge refraction on the LIQUID material,
 * - intensity differences from the user's glass preference.
 */
@Composable
fun LiquidPrototypeScreen(onBack: () -> Unit) {
    var material by rememberSaveable { mutableStateOf(GlassMaterial.LIQUID.name) }
    val selected = remember(material) { GlassMaterial.valueOf(material) }
    val spec = LocalLiquidSpec.current

    LiquidStage(
        source = {
            PrototypeTestField()
        },
        overlay = {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(42.dp).clip(CircleShape).glassSurface(selected, CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                        FutureGlyph("back", tint = spec.text, size = 19.dp)
                    }
                    Column(Modifier.weight(1f).padding(start = 13.dp)) {
                        Text("液态玻璃原型", color = spec.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("真实背景采样 · ${selected.name}", color = spec.muted, fontSize = 10.sp)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    GlassMaterial.entries.forEach { entry ->
                        val active = entry == selected
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (active) spec.accent.copy(alpha = 0.30f) else spec.surfaceTint.copy(alpha = 0.5f))
                                .border(1.dp, if (active) spec.accentBright else spec.surfaceEdge, RoundedCornerShape(11.dp))
                                .clickable { material = entry.name }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        ) {
                            Text(entry.name, color = if (active) spec.accentBright else spec.muted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    MaterialCard("Clear", GlassMaterial.CLEAR, selected, Modifier.weight(1f)) { material = GlassMaterial.CLEAR.name }
                    MaterialCard("Frosted", GlassMaterial.FROSTED, selected, Modifier.weight(1f)) { material = GlassMaterial.FROSTED.name }
                    MaterialCard("Liquid", GlassMaterial.LIQUID, selected, Modifier.weight(1f)) { material = GlassMaterial.LIQUID.name }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    MaterialCard("Dark", GlassMaterial.DARK, selected, Modifier.weight(1f)) { material = GlassMaterial.DARK.name }
                    MaterialCard("Accent", GlassMaterial.ACCENT, selected, Modifier.weight(1f)) { material = GlassMaterial.ACCENT.name }
                    Box(Modifier.weight(1f).glassSurface(selected, RoundedCornerShape(17.dp)).padding(11.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "滚动内容穿过玻璃；边缘可见折射与高光。",
                            color = spec.muted, fontSize = 9.sp, lineHeight = 13.sp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        },
    )
}

/** Vivid, high-contrast test field: stripes, blobs and a scrolling tile river. */
@Composable
private fun PrototypeTestField() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            Modifier.fillMaxWidth().height(220.dp).background(
                Brush.sweepGradient(listOf(Color(0xFFE45A7A), Color(0xFFF2B134), Color(0xFF4FC08D), Color(0xFF3D7BD9), Color(0xFFE45A7A))),
            ),
        )
        Box(
            Modifier.fillMaxWidth().height(120.dp).background(
                Brush.horizontalGradient(listOf(Color(0xFF111318), Color(0xFF3A3F52), Color(0xFF111318))),
            ),
        )
        val tiles = listOf(
            Color(0xFFE45A7A), Color(0xFFF2B134), Color(0xFF4FC08D), Color(0xFF3D7BD9),
            Color(0xFF9B59F5), Color(0xFF1ABC9C), Color(0xFFE67E22), Color(0xFF2ECCF1),
        )
        repeat(6) { row ->
            Row(Modifier.fillMaxWidth().height(88.dp)) {
                tiles.forEachIndexed { index, color ->
                    Box(
                        Modifier.weight(1f).fillMaxSize()
                            .background(if ((row + index) % 2 == 0) color else color.copy(alpha = 0.72f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f)),
                    ) {
                        Text(
                            "${row * 8 + index + 1}",
                            color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun MaterialCard(
    label: String,
    material: GlassMaterial,
    selected: GlassMaterial,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val spec = LocalLiquidSpec.current
    val refraction = spec.material(material).refractionBandDp > 0f
    val active = material == selected
    Box(
        modifier
            .height(64.dp)
            .glassSurface(material, RoundedCornerShape(17.dp))
            .border(
                width = if (active) 1.5.dp else 0.dp,
                color = spec.accentBright,
                shape = RoundedCornerShape(17.dp),
            )
            .clickable(onClick = onClick)
            .padding(11.dp),
    ) {
        Text(label, color = spec.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.TopStart))
        Text(
            if (refraction) "折射" else "模糊",
            color = spec.muted, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}
