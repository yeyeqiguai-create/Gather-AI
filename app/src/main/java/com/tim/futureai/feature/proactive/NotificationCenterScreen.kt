package com.tim.futureai.feature.proactive

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.futureai.domain.proactive.ProactiveEvent
import com.tim.futureai.domain.proactive.ProactiveStatus
import com.tim.futureai.ui.liquid.LiquidStage
import com.tim.futureai.ui.liquid.LocalLiquidSpec
import com.tim.futureai.ui.liquid.softSurface
import com.tim.futureai.ui.theme.FuturePalette

/** Notification center: prioritized events with snooze/mute/resolve and degraded-mode notice. */
@Composable
fun NotificationCenterScreen(
    events: List<ProactiveEvent>,
    notificationsEnabled: Boolean,
    onSnooze: (String) -> Unit,
    onMute: (String) -> Unit,
    onResolve: (String) -> Unit,
    onOpenRelated: (ProactiveEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = LocalLiquidSpec.current
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
                    Text("通知中心", color = spec.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("事件 · 去重 · 冷却 · 稍后提醒", color = spec.muted, fontSize = 10.sp)
                }
            }
        },
        source = {
            LazyColumn(
                Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 104.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!notificationsEnabled) {
                    item {
                        Text(
                            "通知权限未授予：系统通知已停用，事件仅在此中心显示；授予权限后新事件会自动恢复通知。",
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(spec.warning.copy(alpha = 0.14f)).padding(11.dp),
                            color = spec.warning, fontSize = 11.sp, lineHeight = 15.sp,
                        )
                    }
                }
                if (events.isEmpty()) {
                    item { Text("暂无提醒事件。", color = spec.muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 24.dp)) }
                }
                items(events, key = { it.eventId }) { event ->
                    Column(
                        Modifier.fillMaxWidth().softSurface(RoundedCornerShape(15.dp)).padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(severityColor(event.severity)))
                            Spacer(Modifier.width(8.dp))
                            Text(event.title, color = spec.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(statusLabel(event.status), color = spec.muted, fontSize = 10.sp)
                        }
                        Text(event.detail, color = spec.muted, fontSize = 11.sp, lineHeight = 15.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${event.type} · 共 ${event.occurrences} 次", color = spec.muted.copy(alpha = 0.7f), fontSize = 9.sp, modifier = Modifier.weight(1f))
                            if (event.status == ProactiveStatus.SNOOZED) Text("已稍后提醒", color = spec.muted, fontSize = 10.sp)
                            TextButton(onClick = { onSnooze(event.eventId) }) { Text("稍后 1 小时", color = spec.cyan, fontSize = 10.sp) }
                            TextButton(onClick = { onMute(event.eventId) }) { Text("静音", color = spec.muted, fontSize = 10.sp) }
                            TextButton(onClick = { onResolve(event.eventId) }) { Text("已知悉", color = spec.accentBright, fontSize = 10.sp) }
                        }
                        if (event.relatedKind == "TASK") {
                            Text(
                                "查看关联任务 →", color = spec.cyan, fontSize = 10.sp,
                                modifier = Modifier.clickable(onClick = { onOpenRelated(event) }),
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun severityColor(severity: Int): Color = when (severity) {
    3 -> FuturePalette.Danger
    2 -> FuturePalette.Warning
    else -> FuturePalette.Cyan
}

private fun statusLabel(status: ProactiveStatus): String = when (status) {
    ProactiveStatus.PENDING -> "待通知"
    ProactiveStatus.NOTIFIED -> "已通知"
    ProactiveStatus.SNOOZED -> "已稍后"
    ProactiveStatus.MUTED -> "已静音"
    ProactiveStatus.RESOLVED -> "已处理"
}


