package com.tim.futureai.feature.audit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.futureai.ui.liquid.LiquidStage
import com.tim.futureai.ui.liquid.LocalLiquidSpec
import com.tim.futureai.ui.liquid.softSurface

/** One traceable record; kind is TASK / REPAIR / NOTIFY. */
data class AuditEntry(val id: String, val atMs: Long, val kind: String, val title: String, val detail: String)

private fun formatAuditTime(ms: Long): String {
    if (ms <= 0L) return "—"
    val total = ms / 1000
    val days = total / 86400
    val hours = (total % 86400) / 3600
    val minutes = (total % 3600) / 60
    return if (days > 0) days.toString() + "d " + hours + "h"
    else hours.toString() + ":" + (if (minutes < 10) "0" else "") + minutes
}

@Composable
fun AuditScreen(
    entries: List<AuditEntry>,
    onExport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = LocalLiquidSpec.current
    var filter by remember { mutableStateOf("全部") }
    val visible = remember(entries, filter) {
        if (filter == "全部") entries else entries.filter { it.kind == filter }
    }
    LiquidStage(
        modifier = modifier.fillMaxSize(),
        overlay = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).clip(CircleShape).softSurface(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Text("←", color = spec.text, fontSize = 18.sp)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("审计与历史", color = spec.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("任务 · 修复 · 通知", color = spec.muted, fontSize = 10.sp)
                }
                TextButton(onClick = onExport) { Text("导出", color = spec.cyan, fontSize = 11.sp) }
            }
        },
        source = {
            Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 104.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("全部", "TASK", "REPAIR", "NOTIFY").forEach { option ->
                        TextButton(onClick = { filter = option }) {
                            Text(option, color = if (filter == option) spec.cyan else spec.muted, fontSize = 11.sp)
                        }
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxSize().padding(bottom = 20.dp)) {
                    if (visible.isEmpty()) {
                        item { Text("暂无记录。", color = spec.muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 20.dp)) }
                    }
                    items(visible, key = { it.id }) { entry ->
                        Column(Modifier.fillMaxWidth().softSurface(RoundedCornerShape(12.dp)).padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.kind, color = spec.cyan, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.weight(1f))
                                Text(formatAuditTime(entry.atMs), color = spec.muted, fontSize = 9.sp)
                            }
                            Text(entry.title, color = spec.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                            if (entry.detail.isNotBlank()) Text(entry.detail, color = spec.muted, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 4)
                        }
                    }
                }
            }
        },
    )
}
