package com.tim.futureai.feature.task

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.futureai.domain.task.TaskAggregate
import com.tim.futureai.domain.task.TaskConclusion
import com.tim.futureai.domain.task.TaskEngine
import com.tim.futureai.domain.task.TaskEnginePolicy
import com.tim.futureai.domain.task.TaskPlanResult
import com.tim.futureai.domain.task.TaskStatus
import com.tim.futureai.domain.task.TaskStep
import com.tim.futureai.domain.task.TaskStepStatus
import com.tim.futureai.domain.task.VerificationStatus
import com.tim.futureai.ui.theme.FutureBackdrop
import com.tim.futureai.ui.theme.FutureGlyph
import com.tim.futureai.ui.theme.FuturePalette
import com.tim.futureai.ui.theme.glassPanel
import kotlinx.coroutines.launch

@Composable
fun TaskCenterScreen(
    engine: TaskEngine,
    policy: TaskEnginePolicy,
    onBack: () -> Unit,
    onPlan: suspend (String, TaskEnginePolicy) -> TaskPlanResult,
    modifier: Modifier = Modifier,
) {
    val tasks by engine.tasks.collectAsState()
    val scope = rememberCoroutineScope()
    var goal by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var approvalTarget by remember { mutableStateOf<Pair<TaskAggregate, TaskStep>?>(null) }
    var cancelTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tasks) {
        if (selectedId == null || tasks.none { it.task.id == selectedId }) selectedId = tasks.firstOrNull()?.task?.id
    }
    val selected = tasks.firstOrNull { it.task.id == selectedId }

    FutureBackdrop(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).clip(CircleShape).glassPanel(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    FutureGlyph("back", tint = FuturePalette.Text, size = 19.dp)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("任务中心", color = FuturePalette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Verified Task Engine · 本地工具执行与证据校验", color = FuturePalette.Muted, fontSize = 10.sp)
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(20.dp)).padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("新建可验证任务", color = FuturePalette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "规划请求会发送给当前选择的 AI 服务商；模型只提出计划。执行仅调用下方列出的本地工具，不运行模型生成代码，也不代表拥有手机系统或其他应用控制能力。",
                        color = FuturePalette.Muted, fontSize = 11.sp, lineHeight = 16.sp,
                    )
                    BasicTextField(
                        value = goal, onValueChange = { goal = it.take(4_000) },
                        modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(13.dp)).background(FuturePalette.Panel).padding(12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = FuturePalette.Text, fontSize = 13.sp, lineHeight = 18.sp),
                        decorationBox = { inner ->
                            if (goal.isBlank()) Text("描述目标，例如：计算 17 × 23，或检查 JSON 是否包含 name,id", color = FuturePalette.Muted, fontSize = 12.sp)
                            inner()
                        },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            enabled = policy.enabled && !busy && goal.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    busy = true
                                    message = null
                                    runCatching { onPlan(goal.trim(), policy) }
                                        .onSuccess { result ->
                                            when (result) {
                                                is TaskPlanResult.Ready -> {
                                                    selectedId = result.aggregate.task.id
                                                    goal = ""
                                                    if (!policy.reviewPlanBeforeExecution) engine.start(result.aggregate.task.id, policy)
                                                    else message = "计划已通过本地校验；请检查步骤后手动开始。"
                                                }
                                                is TaskPlanResult.Rejected -> {
                                                    selectedId = result.aggregate.task.id
                                                    message = result.reason
                                                }
                                                TaskPlanResult.Disabled -> message = "任务模式已在设置中关闭"
                                            }
                                        }
                                        .onFailure { message = it.message ?: "任务规划失败" }
                                    busy = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text("生成并校验计划")
                        }
                        if (!policy.enabled) Text("任务模式已关闭", color = FuturePalette.Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                message?.let { StatusCard(it, failed = it.contains("失败") || it.contains("无法") || it.contains("缺少")) }

                Text("任务历史 · ${tasks.size}", color = FuturePalette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (tasks.isEmpty()) StatusCard("还没有任务。通过上方任务模式提交目标后，计划、执行、验证与恢复状态会显示在这里。")
                tasks.forEach { aggregate ->
                    val task = aggregate.task
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).glassPanel(RoundedCornerShape(16.dp))
                            .clickable { selectedId = task.id }.padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(task.goal.ifBlank { "未命名任务" }, modifier = Modifier.weight(1f), color = FuturePalette.Text,
                                fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(task.status.label(), color = statusColor(task.status), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("${aggregate.verifiedStepCount}/${aggregate.steps.size} 步已验证 · ${task.createdAtMs}", color = FuturePalette.Muted, fontSize = 10.sp)
                    }
                }

                selected?.let { aggregate ->
                    TaskDetails(
                        aggregate = aggregate,
                        onStart = { engine.start(aggregate.task.id, policy) },
                        onPause = { scope.launch { engine.pause(aggregate.task.id) } },
                        onResume = { scope.launch { if (!engine.resume(aggregate.task.id, policy)) message = "任务含有结果不确定的步骤，不能自动恢复。" } },
                        onCancel = { cancelTarget = aggregate.task.id },
                        onApprove = { step -> approvalTarget = aggregate to step },
                        onRetry = { step -> scope.launch { if (!engine.retryStep(aggregate.task.id, step.id, policy)) message = "该步骤不满足安全重试条件" } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    approvalTarget?.let { (aggregate, step) ->
        val tool = step.executorId
        AlertDialog(
            onDismissRequest = { approvalTarget = null },
            title = { Text("确认工具调用") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("工具：$tool")
                    Text("目的：${step.description}")
                    Text("副作用：${if (step.executorId.startsWith("local.")) "当前注册工具仅本地运行，无外部写入" else "请按工具声明逐项检查"}")
                    Text("参数摘要：${step.input.entries.joinToString { "${it.key}=${it.value.take(120)}" }.take(500)}")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = aggregate.task.id
                    val stepId = step.id
                    approvalTarget = null
                    scope.launch { if (!engine.approveStep(id, stepId, policy)) message = "授权未能应用，请检查任务状态" }
                }) { Text("批准这一次") }
            },
            dismissButton = { TextButton(onClick = { approvalTarget = null }) { Text("暂不批准") } },
        )
    }
    cancelTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("取消这个任务？") },
            text = { Text("当前步骤会收到取消信号；若工具副作用结果无法确认，任务会暂停为“不确定”，不会报告成功。") },
            confirmButton = { TextButton(onClick = { cancelTarget = null; scope.launch { engine.cancel(id) } }) { Text("确认取消") } },
            dismissButton = { TextButton(onClick = { cancelTarget = null }) { Text("继续任务") } },
        )
    }
}

@Composable
private fun TaskDetails(
    aggregate: TaskAggregate,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onApprove: (TaskStep) -> Unit,
    onRetry: (TaskStep) -> Unit,
    modifier: Modifier = Modifier,
) {
    val task = aggregate.task
    Column(modifier.glassPanel(RoundedCornerShape(20.dp)).padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("任务详情", color = FuturePalette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("目标：${task.goal}", color = FuturePalette.Muted, fontSize = 11.sp, lineHeight = 16.sp)
        Text("状态：${task.status.label()} · 计划 ${task.planVersion ?: "—"}", color = statusColor(task.status), fontSize = 11.sp)
        if (task.plannerModel != null) {
            val tokenLine = if (task.plannerPromptTokens != null && task.plannerCompletionTokens != null)
                "${task.plannerPromptTokens} 输入 / ${task.plannerCompletionTokens} 输出 Token" else "Token 用量未知"
            Text("规划模型：${task.plannerModel} · $tokenLine · ${task.plannerDurationMs ?: 0} ms" +
                (task.estimatedCostUsd?.let { " · 约 $${"%.6f".format(it)}" } ?: " · 费用未知"), color = FuturePalette.Muted, fontSize = 10.sp)
        }
        if (aggregate.steps.isNotEmpty()) {
            Text("实时进度：${aggregate.steps.firstOrNull { it.status in setOf(TaskStepStatus.RUNNING, TaskStepStatus.VERIFYING) }?.title ?: "等待执行"} · ${aggregate.verifiedStepCount}/${aggregate.steps.size} 已验证 · ${aggregate.failedStepCount} 失败",
                color = FuturePalette.Cyan, fontSize = 10.sp)
        }
        aggregate.steps.forEachIndexed { index, step ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(FuturePalette.Panel).padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}. ${step.title}", color = FuturePalette.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(step.status.label(), color = stepStatusColor(step.status), fontSize = 10.sp)
                }
                Text(step.description, color = FuturePalette.Muted, fontSize = 10.sp)
                Text("执行器：${step.executorId} · 依赖：${step.dependsOn.ifEmpty { listOf("无") }.joinToString()} · 尝试：${step.attemptCount}", color = FuturePalette.Muted, fontSize = 9.sp)
                Text("验证：${step.verification?.status?.name ?: "未运行"}${step.verification?.reason?.let { " · $it" }.orEmpty()}",
                    color = if (step.verification?.status == VerificationStatus.VERIFIED) FuturePalette.Cyan else FuturePalette.Muted, fontSize = 9.sp)
                step.result?.let {
                    Text("结果：${it.summary}", color = FuturePalette.Text, fontSize = 10.sp)
                    Text("输出：${it.output.take(900)}", color = FuturePalette.Muted, fontSize = 9.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
                if (step.status == TaskStepStatus.WAITING_FOR_APPROVAL) {
                    TextButton(onClick = { onApprove(step) }) { Text("检查并确认此步骤") }
                }
                if (step.status == TaskStepStatus.FAILED && !step.executorId.startsWith("external.")) {
                    TextButton(onClick = { onRetry(step) }) { Text("安全条件允许时手动重试") }
                }
            }
        }
        task.result?.let { result ->
            val label = when (result.conclusion) {
                TaskConclusion.VERIFIED_SUCCESS -> "已验证完成"
                TaskConclusion.FAILED -> "失败"
                TaskConclusion.INCONCLUSIVE -> "无法确定"
                TaskConclusion.CANCELLED -> "已取消"
            }
            StatusCard("最终结论：$label · ${result.summary}", failed = result.conclusion != TaskConclusion.VERIFIED_SUCCESS)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (task.status) {
                TaskStatus.READY -> Button(onClick = onStart) { Text("开始执行") }
                TaskStatus.RUNNING, TaskStatus.VERIFYING -> {
                    Button(onClick = onPause) { Text("暂停") }
                    TextButton(onClick = onCancel) { Text("取消") }
                }
                TaskStatus.PAUSED -> {
                    Button(onClick = onResume, enabled = aggregate.steps.none { it.status == TaskStepStatus.UNKNOWN }) { Text("继续") }
                    TextButton(onClick = onCancel) { Text("取消") }
                }
                TaskStatus.WAITING_FOR_APPROVAL -> Unit
                TaskStatus.CREATED, TaskStatus.PLANNING -> Text("规划中…", color = FuturePalette.Muted, fontSize = 11.sp)
                TaskStatus.SUCCEEDED, TaskStatus.FAILED, TaskStatus.CANCELLED -> Unit
            }
        }
        Text("审计事件（${aggregate.events.size}）", color = FuturePalette.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        aggregate.events.takeLast(20).forEach { event ->
            Text("#${event.sequence} · ${event.type} · ${event.summary}", color = FuturePalette.Muted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun StatusCard(text: String, failed: Boolean = false) {
    Text(text, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FuturePalette.Panel).padding(12.dp),
        color = if (failed) FuturePalette.Danger else FuturePalette.Cyan, fontSize = 11.sp, lineHeight = 16.sp)
}

private fun TaskStatus.label() = when (this) {
    TaskStatus.CREATED -> "已创建"
    TaskStatus.PLANNING -> "规划中"
    TaskStatus.READY -> "待执行"
    TaskStatus.RUNNING -> "执行中"
    TaskStatus.WAITING_FOR_APPROVAL -> "待确认"
    TaskStatus.VERIFYING -> "验证中"
    TaskStatus.SUCCEEDED -> "已验证成功"
    TaskStatus.FAILED -> "失败"
    TaskStatus.CANCELLED -> "已取消"
    TaskStatus.PAUSED -> "已暂停"
}

private fun TaskStepStatus.label() = when (this) {
    TaskStepStatus.PLANNED -> "计划中"
    TaskStepStatus.READY -> "就绪"
    TaskStepStatus.RUNNING -> "执行中"
    TaskStepStatus.WAITING_FOR_APPROVAL -> "待确认"
    TaskStepStatus.VERIFYING -> "验证中"
    TaskStepStatus.SUCCEEDED -> "已验证"
    TaskStepStatus.FAILED -> "失败"
    TaskStepStatus.CANCELLED -> "已取消"
    TaskStepStatus.UNKNOWN -> "结果不确定"
}

@Composable
private fun statusColor(status: TaskStatus) = when (status) {
    TaskStatus.SUCCEEDED -> FuturePalette.Cyan
    TaskStatus.FAILED, TaskStatus.CANCELLED -> FuturePalette.Danger
    TaskStatus.WAITING_FOR_APPROVAL -> Color(0xFFFFC96B)
    TaskStatus.RUNNING, TaskStatus.VERIFYING -> FuturePalette.VioletBright
    else -> FuturePalette.Muted
}

@Composable
private fun stepStatusColor(status: TaskStepStatus) = when (status) {
    TaskStepStatus.SUCCEEDED -> FuturePalette.Cyan
    TaskStepStatus.FAILED, TaskStepStatus.UNKNOWN, TaskStepStatus.CANCELLED -> FuturePalette.Danger
    TaskStepStatus.WAITING_FOR_APPROVAL -> Color(0xFFFFC96B)
    TaskStepStatus.RUNNING, TaskStepStatus.VERIFYING -> FuturePalette.VioletBright
    else -> FuturePalette.Muted
}
