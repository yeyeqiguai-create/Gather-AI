package com.tim.futureai.feature.arena

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.futureai.domain.ai.AiSettings
import com.tim.futureai.domain.arena.ArenaSession
import com.tim.futureai.domain.reputation.ReputationView
import com.tim.futureai.ui.liquid.LiquidStage
import com.tim.futureai.ui.liquid.LocalLiquidSpec
import com.tim.futureai.ui.liquid.softSurface
import com.tim.futureai.ui.theme.FuturePalette

/**
 * Model Arena: pick at least two models, run one prompt independently, compare
 * answers with cross-review, objective verification and a justified confidence.
 */
@Composable
fun ArenaScreen(
    profiles: List<AiSettings>,
    sessions: List<ArenaSession>,
    reputations: List<ReputationView>,
    running: Boolean,
    onRun: (String, List<String>) -> Unit,
    onResetReputation: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = LocalLiquidSpec.current
    var prompt by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
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
                    Text("Model Arena", color = spec.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("独立生成 · 交叉评审 · 客观验证 · 信誉回填", color = spec.muted, fontSize = 10.sp)
                }
            }
        },
        source = {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 104.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Column(Modifier.fillMaxWidth().softSurface(RoundedCornerShape(16.dp)).padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("问题（至少选择两个模型）", color = spec.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    BasicTextField(
                        value = prompt, onValueChange = { prompt = it.take(2000) },
                        modifier = Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(12.dp))
                            .background(spec.surfaceTint.copy(alpha = 0.5f)).padding(11.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = spec.text, fontSize = 13.sp, lineHeight = 18.sp),
                        decorationBox = { inner ->
                            if (prompt.isBlank()) Text("例如：计算 128 * 64 并说明步骤", color = spec.muted, fontSize = 12.sp)
                            inner()
                        },
                    )
                    profiles.forEach { profile ->
                        val active = profile.id in selected
                        val available = profile.isAvailableForRouting
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                                .background(if (active) spec.accent.copy(alpha = 0.18f) else spec.surfaceTint.copy(alpha = 0.4f))
                                .clickable(enabled = available) {
                                    if (active) selected.remove(profile.id) else selected.add(profile.id)
                                }
                                .padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${profile.name} · ${profile.model}", color = if (available) spec.text else spec.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            if (!available) Text("不可用", color = spec.muted, fontSize = 10.sp)
                        }
                    }
                    Button(
                        onClick = { onRun(prompt.trim(), selected.toList()) },
                        enabled = !running && prompt.isNotBlank() && selected.size >= 2,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (running) {
                            CircularProgressIndicator(Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(9.dp)); Text("竞技中…")
                        } else Text("开始对决（${selected.size} 个模型）")
                    }
                }

                sessions.firstOrNull()?.let { session ->
                    Text("最近一轮", color = spec.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    session.entries.forEach { entry ->
                        Column(Modifier.fillMaxWidth().softSurface(RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.modelKey, color = spec.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                val mark = session.verification?.perEntry?.get(entry.modelKey)
                                if (mark != null) {
                                    Text(if (mark) "客观通过" else "客观不符",
                                        color = if (mark) spec.success else spec.danger, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (entry.error != null) Text("错误：${entry.error}", color = spec.danger, fontSize = 10.sp)
                            else Text(entry.answer.orEmpty(), color = spec.text, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 12)
                            Text("延迟 ${entry.latencyMs} ms" +
                                (entry.promptTokens?.let { " · 输入 $it tok" } ?: "") +
                                (entry.completionTokens?.let { " · 输出 $it tok" } ?: "") +
                                (entry.costUsd?.let { " · 约 $${"%.6f".format(it)}" } ?: ""),
                                color = spec.muted.copy(alpha = 0.75f), fontSize = 9.sp)
                        }
                    }
                    session.review?.let { review ->
                        Column(Modifier.fillMaxWidth().softSurface(RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("交叉评审 · ${review.reviewerModelKey}", color = spec.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (review.agreements.isNotEmpty()) Text("一致：${review.agreements.joinToString("；")}", color = spec.success, fontSize = 11.sp, lineHeight = 15.sp)
                            if (review.conflicts.isNotEmpty()) Text("分歧：${review.conflicts.joinToString("；")}", color = spec.warning, fontSize = 11.sp, lineHeight = 15.sp)
                            if (review.notes.isNotBlank()) Text(review.notes, color = spec.muted, fontSize = 10.sp, lineHeight = 14.sp)
                        }
                    }
                    session.verification?.let { verification ->
                        StatusCard("客观验证（${verification.method}）：${verification.detail}", passed = verification.passed)
                    }
                    session.conclusion?.let { conclusion ->
                        StatusCard("推荐：${conclusion.recommendedModelKey ?: "无"} · 置信度 ${conclusion.confidence} — ${conclusion.basis}", passed = null)
                    }
                }

                Text("模型信誉（按任务类型 · 样本不足不计分）", color = spec.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (reputations.isEmpty()) StatusCard("尚无信誉样本；完成任务或 Arena 对决后按（模型 × 任务类型）累计。", passed = null)
                reputations.forEach { view ->
                    Column(Modifier.fillMaxWidth().softSurface(RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${view.modelKey} · ${view.category}", color = spec.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text(
                                if (view.sufficientSamples) "成功率 ${(view.successRate!! * 100).toInt()}%" else "样本不足（${view.sampleSize}）",
                                color = if (view.sufficientSamples) spec.success else spec.muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text("样本 ${view.sampleSize} · 失败 ${view.failure} · 平均延迟 ${view.averageLatencyMs ?: "—"} ms · 累计费用 ${"$"}${"%.6f".format(view.totalCostUsd)}",
                            color = spec.muted, fontSize = 9.sp)
                        if (view.failureTypes.isNotEmpty()) {
                            Text("失败类型：" + view.failureTypes.entries.joinToString("；") { "${it.key} ×${it.value}" },
                                color = spec.muted.copy(alpha = 0.8f), fontSize = 9.sp)
                        }
                    }
                }
                TextButton(onClick = onResetReputation, modifier = Modifier.align(Alignment.End)) {
                    Text("重置全部信誉统计", color = spec.danger, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
        },
    )
}

@Composable
private fun StatusCard(text: String, passed: Boolean?) {
    val spec = LocalLiquidSpec.current
    val accent = when (passed) { true -> spec.success; false -> spec.danger; null -> spec.accent }
    Text(text, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(accent.copy(alpha = 0.12f)).border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(11.dp),
        color = if (passed == false) spec.danger else spec.text, fontSize = 11.sp, lineHeight = 15.sp)
}
