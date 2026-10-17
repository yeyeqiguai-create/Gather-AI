package com.tim.futureai.feature.health

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.tim.futureai.domain.health.HealthIssue
import com.tim.futureai.domain.health.HealthMonitor
import com.tim.futureai.domain.health.HealthReport
import com.tim.futureai.domain.health.HealthStatus
import com.tim.futureai.domain.health.RepairRecord
import com.tim.futureai.domain.health.SelfHealingEngine
import com.tim.futureai.ui.liquid.LiquidStage
import com.tim.futureai.ui.liquid.LocalLiquidSpec
import com.tim.futureai.ui.liquid.softSurface
import com.tim.futureai.ui.theme.FutureGlyph
import com.tim.futureai.ui.theme.FuturePalette

/** Health center: checks, statuses with evidence, issue lifecycle and bounded self-repair. */
@Composable
fun HealthCenterScreen(
    monitor: HealthMonitor,
    healingEngine: SelfHealingEngine,
    issues: List<HealthIssue>,
    repairRecords: List<RepairRecord>,
    latestReport: HealthReport?,
    repairingCheckId: String?,
    notificationDegraded: Boolean,
    onRunAll: () -> Unit,
    onRepair: (String) -> Unit,
    onOpenCenter: () -> Unit,
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
                    FutureGlyph("back", tint = spec.text, size = 19.dp)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("健康与自愈", color = spec.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("自我诊断 · 有限修复 · 复检与提醒", color = spec.muted, fontSize = 10.sp)
                }
            }
        },
        source = {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 104.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                if (notificationDegraded) StatusLine(
                    "通知权限未授予：提醒将只保留在应用内通知中心，不会弹出系统通知。",
                    spec.warning,
                )
                Button(
                    onClick = onRunAll,
                    enabled = repairingCheckId == null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                ) {
                    if (repairingCheckId != null) {
                        CircularProgressIndicator(Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(9.dp)); Text("检查与修复进行中…")
                    } else Text("立即全面检查")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("通知中心：查看提醒、稍后提醒与静音", color = spec.cyan, fontSize = 11.sp,
                        modifier = Modifier.weight(1f).clickable(onClick = onOpenCenter).padding(vertical = 6.dp))
                    Text("›", color = spec.muted, fontSize = 16.sp)
                }
                latestReport?.let { report ->
                    Text("最近检查 · ${report.results.size} 项 · ${report.trigger}", color = spec.muted, fontSize = 10.sp)
                }
                val results = latestReport?.results.orEmpty()
                val unresolved = issues.filter { it.resolvedAtMs == null }
                if (unresolved.isNotEmpty()) {
                    Text("未解决问题 · ${unresolved.size}", color = spec.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    unresolved.sortedByDescending { it.status.ordinal }.forEach { issue ->
                        IssueCard(
                            issue = issue,
                            repairable = issue.status != HealthStatus.UNVERIFIABLE &&
                                healingEngine.strategyFor(issue.checkId) != null,
                            repairing = repairingCheckId == issue.checkId,
                            repairTitle = healingEngine.strategyFor(issue.checkId)?.title,
                            onRepair = { onRepair(issue.checkId) },
                        )
                    }
                } else {
                    StatusLine("当前没有未解决的健康问题。", spec.success)
                }
                Text("检查项", color = spec.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                results.forEach { result ->
                    Column(
                        Modifier.fillMaxWidth().softSurface(RoundedCornerShape(14.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor(result.status)))
                            Spacer(Modifier.width(8.dp))
                            Text(result.title, color = spec.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text(statusLabel(result.status), color = statusColor(result.status), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(result.detail, color = spec.muted, fontSize = 10.sp, lineHeight = 14.sp)
                        Text("证据：${result.evidence}", color = spec.muted.copy(alpha = 0.75f), fontSize = 9.sp)
                        val records = repairRecords.filter { it.checkId == result.checkId }
                        if (records.isNotEmpty()) {
                            Text(
                                "修复记录：" + records.take(3).joinToString("；") { r ->
                                    "第${r.attempt}次 ${r.outcome}${r.recheckStatus?.let { " · 复检 $it" } ?: ""}"
                                },
                                color = spec.muted, fontSize = 9.sp, lineHeight = 13.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
            }
        },
    )
}

@Composable
fun IssueCard(issue: HealthIssue, repairable: Boolean, repairing: Boolean, repairTitle: String?, onRepair: () -> Unit) {
    val spec = LocalLiquidSpec.current
    Column(Modifier.fillMaxWidth().softSurface(RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor(issue.status)))
            Spacer(Modifier.width(8.dp))
            Text(issue.detail, color = spec.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(statusLabel(issue.status), color = statusColor(issue.status), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
        Text("证据：${issue.evidence}", color = spec.muted, fontSize = 9.sp)
        Text("首次发现 ${issue.occurrences} 次前 · 最近 ${issue.lastSeenAtMs}", color = spec.muted.copy(alpha = 0.7f), fontSize = 9.sp)
        if (repairable && repairTitle != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRepair, enabled = !repairing) {
                    Text(if (repairing) "修复中…" else "尝试修复：$repairTitle", color = spec.accentBright, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Text(text, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).softSurface(RoundedCornerShape(12.dp)).padding(11.dp),
        color = color, fontSize = 11.sp, lineHeight = 15.sp)
}

@Composable
fun statusColor(status: HealthStatus): Color = when (status) {
    HealthStatus.OK -> FuturePalette.Success
    HealthStatus.WARNING -> FuturePalette.Warning
    HealthStatus.FAILED -> FuturePalette.Danger
    HealthStatus.UNVERIFIABLE -> FuturePalette.Muted
}

fun statusLabel(status: HealthStatus): String = when (status) {
    HealthStatus.OK -> "正常"
    HealthStatus.WARNING -> "警告"
    HealthStatus.FAILED -> "失败"
    HealthStatus.UNVERIFIABLE -> "无法检查"
}


