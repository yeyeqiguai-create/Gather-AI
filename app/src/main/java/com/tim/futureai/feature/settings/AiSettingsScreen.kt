package com.tim.futureai.feature.settings

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.futureai.ui.liquid.LiquidStage
import com.tim.futureai.ui.liquid.LocalLiquidSpec
import com.tim.futureai.ui.theme.FutureGlyph
import com.tim.futureai.ui.theme.FuturePalette
import com.tim.futureai.ui.theme.glassPanel
import com.tim.futureai.feature.update.UpdateCheckResult
import com.tim.futureai.feature.update.UpdateSection
import com.tim.futureai.domain.usage.UsageSnapshot
import com.tim.futureai.domain.usage.UsagePriceKey
import com.tim.futureai.domain.usage.ModelUsagePrice
import com.tim.futureai.domain.ai.CapabilityEvidence
import com.tim.futureai.domain.ai.ModelTask
import com.tim.futureai.domain.ai.AiSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    settings: AiSettings,
    message: String?,
    onSettingsChanged: (AiSettings) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    messageIsError: Boolean = false,
    updateResult: UpdateCheckResult? = null,
    updateChecking: Boolean = false,
    updateMessage: String? = null,
    downloadProgress: Int? = null,
    onCheckUpdates: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    installUpdateButtonText: String = "系统确认安装",
    onViewUpdateNotes: () -> Unit = {},
    profiles: List<AiSettings> = emptyList(),
    onSelectProfile: (String) -> Unit = {},
    onCreateProfile: () -> Unit = {},
    onCreateLocalPreset: (com.tim.futureai.domain.ai.LocalModelTier) -> Unit = {},
    onDeleteProfile: (String) -> Unit = {},
    onTestConnection: () -> Unit = {},
    usage: UsageSnapshot = UsageSnapshot(),
    includeUsageAndUpdates: Boolean = true,
    sessionReferences: Map<String, Int> = emptyMap(),
) {
    var showApiKey by remember { mutableStateOf(false) }
    var evidenceMenuExpanded by remember { mutableStateOf(false) }
    var deleteProfileId by remember { mutableStateOf<String?>(null) }
    val spec = LocalLiquidSpec.current
    LiquidStage(
        modifier = modifier,
        overlay = {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 20.dp, top = 10.dp, bottom = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).glassPanel(CircleShape).clickable(onClick = onBack),
                            contentAlignment = Alignment.Center,
                        ) { FutureGlyph("back", tint = FuturePalette.Text, size = 20.dp) }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI 服务", color = FuturePalette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text("连接 OpenAI-compatible API", color = FuturePalette.Muted, fontSize = 11.sp)
                        }
                        val configured = settings.isConfigured
                        Row(
                            modifier = Modifier.clip(CircleShape).background(if (configured) FuturePalette.Success.copy(alpha = 0.16f) else FuturePalette.Muted.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(if (configured) FuturePalette.Success else FuturePalette.Muted))
                            Spacer(Modifier.width(6.dp))
                            Text(if (configured) "已配置" else "未配置", color = if (configured) FuturePalette.Success else FuturePalette.Muted, fontSize = 10.sp)
                        }
                    }
                }
        },
        source = {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 104.dp).imePadding().navigationBarsPadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(17.dp),
            ) {
                IntroPanel()
                Row(
                    modifier = Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("启用此服务商参与选模", color = FuturePalette.Text, fontSize = 12.sp)
                        Text("停用后不会自动路由；手动发送也会被阻止", color = FuturePalette.Muted, fontSize = 10.sp)
                    }
                    Checkbox(checked = settings.enabled, onCheckedChange = { onSettingsChanged(settings.copy(enabled = it)) })
                }
                Column(
                    modifier = Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(22.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("服务商与模型", color = FuturePalette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = onCreateProfile) { Text("＋ 新增", color = FuturePalette.VioletBright) }
                        com.tim.futureai.domain.ai.LocalModelTier.entries.forEach { tier ->
                            TextButton(onClick = { onCreateLocalPreset(tier) }) {
                                Text(tier.label, color = FuturePalette.Cyan, fontSize = 11.sp)
                            }
                        }
                    }
                    profiles.forEach { profile ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(if (profile.id == settings.id) spec.accent.copy(alpha = 0.22f) else spec.surfaceTint.copy(alpha = 0.45f))
                                .clickable { onSelectProfile(profile.id) }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(profile.name, color = FuturePalette.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                val testedAt = profile.lastConnectionTestAt?.replace('T', ' ')?.take(16) ?: "时间未知"
                                Text(
                                    "${if (profile.enabled) "已启用" else "已停用"} · ${profile.providerProtocol.name} · ${profile.model}" +
                                        (profile.lastConnectionTestResult?.let { " · 最近测试 $testedAt：$it" } ?: " · 尚未测试"),
                                    color = FuturePalette.Muted, fontSize = 9.sp, maxLines = 2,
                                )
                            }
                            if (profile.id == settings.id) Text("编辑中", color = FuturePalette.Cyan, fontSize = 10.sp)
                            else TextButton(onClick = { onSelectProfile(profile.id) }) { Text("编辑", color = FuturePalette.VioletBright) }
                            TextButton(onClick = { deleteProfileId = profile.id }) { Text("删除", color = FuturePalette.Danger) }
                        }
                    }
                    Text("每个会话可单独选择已保存的模型；密钥仍由 Android Keystore 加密保存。", color = FuturePalette.Muted, fontSize = 10.sp)
                }
                Column(
                    modifier = Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(26.dp)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    Text("连接信息", color = FuturePalette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("协议：OpenAI-compatible。不同原生协议会由独立适配器注册；未实现的协议会拒绝发送。", color = FuturePalette.Muted, fontSize = 10.sp, lineHeight = 15.sp)
                    SettingFieldLabel("配置名称", "用于区分服务商")
                    OutlinedTextField(
                        value = settings.name,
                        onValueChange = { onSettingsChanged(settings.copy(name = it)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("同一服务商的其他模型（每行一个 ID；共用该服务商密钥和能力声明）", color = FuturePalette.Muted, fontSize = 10.sp)
                    OutlinedTextField(
                        value = settings.availableModels,
                        onValueChange = { onSettingsChanged(settings.copy(availableModels = it)) },
                        placeholder = { Text("例如：model-fast\nmodel-long-context", color = FuturePalette.Muted.copy(alpha = 0.75f)) },
                        minLines = 2, maxLines = 4,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = settings.contextWindowTokens,
                        onValueChange = { onSettingsChanged(settings.copy(contextWindowTokens = it.filter(Char::isDigit).take(9))) },
                        label = { Text("上下文容量（tokens；未知留空）") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                    )
                    Text("任务适配能力（仅服务商声明；不代表基准测试）", color = FuturePalette.Text, fontSize = 11.sp)
                    ModelTask.values().filterNot { it == ModelTask.GENERAL }.chunked(2).forEach { rowTasks ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            rowTasks.forEach { task ->
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = task in settings.supportedTasks,
                                        onCheckedChange = { checked ->
                                            val tasks = if (checked) settings.supportedTasks + task else settings.supportedTasks - task
                                            onSettingsChanged(settings.copy(
                                                supportedTasks = tasks,
                                                capabilityEvidence = if (tasks.isEmpty()) CapabilityEvidence.UNKNOWN else CapabilityEvidence.PROVIDER_DECLARED,
                                            ))
                                        },
                                    )
                                    Text(task.label(), color = FuturePalette.Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("能力信息来源", color = FuturePalette.Text, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { evidenceMenuExpanded = true }) {
                                Text(settings.capabilityEvidence.label(), color = FuturePalette.Cyan, fontSize = 11.sp)
                            }
                            DropdownMenu(expanded = evidenceMenuExpanded, onDismissRequest = { evidenceMenuExpanded = false }) {
                                listOf(CapabilityEvidence.UNKNOWN, CapabilityEvidence.PROVIDER_DECLARED).forEach { evidence ->
                                    DropdownMenuItem(
                                        text = { Text(evidence.label()) },
                                        onClick = { onSettingsChanged(settings.copy(capabilityEvidence = evidence)); evidenceMenuExpanded = false },
                                    )
                                }
                            }
                        }
                    }
                    Text("“已验证”仅供未来由可复现测试确认后记录；当前不会伪造模型成绩或容量。", color = FuturePalette.Muted, fontSize = 9.sp)
                    OutlinedTextField(
                        value = settings.preference.toString(),
                        onValueChange = { value -> value.toIntOrNull()?.let { onSettingsChanged(settings.copy(preference = it.coerceIn(-20, 20))) } },
                        label = { Text("自动选模偏好（-20 到 20）") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                    )
                    SettingFieldLabel("API Base URL", "OpenAI-compatible 接口地址")
                    OutlinedTextField(
                        value = settings.baseUrl,
                        onValueChange = { onSettingsChanged(settings.copy(baseUrl = it)) },
                        placeholder = { Text("https://api.example.com/v1", color = FuturePalette.Muted.copy(alpha = 0.75f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None),
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SettingFieldLabel("Model", "填写服务商提供的模型 ID")
                    OutlinedTextField(
                        value = settings.model,
                        onValueChange = { onSettingsChanged(settings.copy(model = it)) },
                        placeholder = { Text("例如：gpt-4o-mini", color = FuturePalette.Muted.copy(alpha = 0.75f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("模型支持图片输入", color = FuturePalette.Text, fontSize = 12.sp)
                            Text("仅对明确支持视觉输入的模型开启", color = FuturePalette.Muted, fontSize = 10.sp)
                        }
                        Checkbox(checked = settings.supportsImages, onCheckedChange = { onSettingsChanged(settings.copy(supportsImages = it)) })
                    }
                    Text("默认模型单价（USD / 每百万 Token；其他模型价格未知时不估算）", color = FuturePalette.Muted, fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = settings.inputUsdPerMillion,
                            onValueChange = { onSettingsChanged(settings.copy(inputUsdPerMillion = it)) },
                            label = { Text("输入") }, placeholder = { Text("例如 0.15") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(14.dp),
                            colors = fieldColors(), modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = settings.outputUsdPerMillion,
                            onValueChange = { onSettingsChanged(settings.copy(outputUsdPerMillion = it)) },
                            label = { Text("输出") }, placeholder = { Text("例如 0.60") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(14.dp),
                            colors = fieldColors(), modifier = Modifier.weight(1f),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingFieldLabel("API Key", "仅保存在本机安全存储中", Modifier.weight(1f))
                        if (settings.hasSavedApiKey && settings.apiKey.isBlank()) {
                            Text("已安全保存", color = FuturePalette.Success, fontSize = 10.sp)
                        }
                    }
                    OutlinedTextField(
                        value = settings.apiKey,
                        onValueChange = { onSettingsChanged(settings.copy(apiKey = it)) },
                        placeholder = {
                            Text(
                                if (settings.hasSavedApiKey) "留空以保留已保存的密钥" else "粘贴 API Key",
                                color = FuturePalette.Muted.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                            )
                        },
                        singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).clickable { showApiKey = !showApiKey },
                                contentAlignment = Alignment.Center,
                            ) { FutureGlyph(if (showApiKey) "eye_off" else "eye", tint = FuturePalette.Muted, size = 19.dp) }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, capitalization = KeyboardCapitalization.None),
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (settings.hasSavedApiKey && settings.apiKey.isBlank()) "密钥已加密保存。留空会继续使用当前密钥；输入新值会安全替换。"
                        else "密钥会通过 Android Keystore 加密，不会以明文写入普通存储。",
                        color = FuturePalette.Muted,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                    )
                    if (message != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            FutureGlyph(if (messageIsError) "cloud" else "check", tint = if (messageIsError) FuturePalette.Danger else FuturePalette.Success, size = 17.dp)
                            Text(message, color = if (messageIsError) FuturePalette.Danger else FuturePalette.Success, fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(17.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Box(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(spec.accentBright, spec.accent))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                FutureGlyph("check", tint = Color.White, size = 17.dp)
                                Text("保存配置", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    TextButton(onClick = onTestConnection, modifier = Modifier.fillMaxWidth()) {
                        Text("测试连接（不消耗生成 Token）", color = FuturePalette.Cyan)
                    }
                }
                if (includeUsageAndUpdates) UsageCard(usage, profiles)
                if (includeUsageAndUpdates) UpdateSection(
                    result = updateResult,
                    checking = updateChecking,
                    message = updateMessage,
                    downloadProgress = downloadProgress,
                    onCheck = onCheckUpdates,
                    onDownload = onDownloadUpdate,
                    onCancel = onCancelDownload,
                    onInstall = onInstallUpdate,
                    installButtonText = installUpdateButtonText,
                    onViewNotes = onViewUpdateNotes,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(18.dp)).padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    FutureGlyph("spark", tint = FuturePalette.Cyan, size = 17.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("当前版本使用 Chat Completions 格式，适用于 OpenAI-compatible 服务。Base URL 填写到版本路径（如 /v1），无需添加 /chat/completions。", color = FuturePalette.Muted, fontSize = 11.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
        },
    )
    deleteProfileId?.let { id ->
            val name = profiles.firstOrNull { it.id == id }?.name ?: "该服务商"
            val linked = sessionReferences[id] ?: 0
            AlertDialog(
                onDismissRequest = { deleteProfileId = null },
                title = { Text("删除 $name？") },
                text = { Text("将删除该服务商配置及其加密保存的 API Key。$linked 个会话仍保留历史，但后续请求需重新选择可用模型。聊天历史、其他服务商和用量统计不会删除。") },
                confirmButton = { TextButton(onClick = { onDeleteProfile(id); deleteProfileId = null }) { Text("删除服务商", color = FuturePalette.Danger) } },
                dismissButton = { TextButton(onClick = { deleteProfileId = null }) { Text("取消") } },
            )
        }
}

@Composable
internal fun UsageCard(usage: UsageSnapshot, profiles: List<AiSettings>, sessionTitles: Map<String, String> = emptyMap()) {
    val modelPrices = profiles.associate { profile ->
        UsagePriceKey(profile.id, profile.model) to ModelUsagePrice(
            profile.inputUsdPerMillion.toDoubleOrNull(), profile.outputUsdPerMillion.toDoubleOrNull(),
        )
    }
    val estimated = usage.estimatedUsdByModel(modelPrices)
    val breakdown = usage.byProviderAndModel(modelPrices)
    Column(
        Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("用量统计", color = FuturePalette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("请求 ${usage.requestCount} 次 · 错误 ${usage.errorCount} 次 · Token 未知 ${usage.unknownTokenCount} 次", color = FuturePalette.Muted, fontSize = 11.sp)
        Text("已确认 Token：输入 ${usage.promptTokens} · 输出 ${usage.completionTokens}", color = FuturePalette.Muted, fontSize = 11.sp)
        Text("请求耗时合计：${usage.totalDurationMs} ms", color = FuturePalette.Muted, fontSize = 11.sp)
        breakdown.take(5).forEach { group ->
            Text(
                "${group.providerName} · ${group.model}：${group.requestCount} 次 / 错误 ${group.errorCount} 次 · Token 已知 ${group.knownUsageCount}，未知 ${group.unknownUsageCount}",
                color = FuturePalette.Text, fontSize = 10.sp, maxLines = 2,
            )
            Text(
                "输入 ${group.promptTokens} · 输出 ${group.completionTokens} · 费用估算 " +
                    (group.estimatedUsd?.let { "~${"%.6f".format(it)} USD" } ?: "未知"),
                color = FuturePalette.Muted, fontSize = 9.sp,
            )
        }
        Text(
        estimated?.let { "费用估算（USD）：~${"%.6f".format(it)}" }
                ?: "费用估算：未知（缺少完整 Token 用量或未配置单价）",
            color = FuturePalette.Cyan, fontSize = 11.sp,
        )
        val sessions = usage.bySession()
        if (sessions.isNotEmpty()) {
            Text("按会话统计（最近 10 个）", color = FuturePalette.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            sessions.take(10).forEach { group ->
                val label = group.sessionId?.let { sessionTitles[it] ?: "会话 ${it.take(8)}" } ?: "旧版记录（会话未知）"
                Text(
                    "$label：${group.requestCount} 次 / 错误 ${group.errorCount} 次 · 输入 ${group.promptTokens} / 输出 ${group.completionTokens} Token · ${group.totalDurationMs} ms",
                    color = FuturePalette.Muted, fontSize = 9.sp, maxLines = 2,
                )
            }
            if (sessions.size > 10) Text("另有 ${sessions.size - 10} 个会话，请使用时间筛选缩小范围。", color = FuturePalette.Muted, fontSize = 9.sp)
        }
        usage.records.takeLast(5).asReversed().forEach { record ->
            Text(
                "${record.providerName} · ${record.model} · ${record.durationMs} ms" +
                    (record.errorType?.let { " · $it" } ?: "") +
                    (record.tokenUsage?.let { " · ${it.promptTokens}+${it.completionTokens} tokens" } ?: " · Token 未知"),
                color = FuturePalette.Muted, fontSize = 9.sp, maxLines = 1,
            )
        }
    }
}

@Composable
private fun IntroPanel() {
    Row(
        modifier = Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(22.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x298C75F3)), contentAlignment = Alignment.Center) {
            FutureGlyph("cloud", tint = FuturePalette.VioletBright, size = 21.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("你的服务，你的对话", color = FuturePalette.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("设置兼容接口地址、模型与密钥", color = FuturePalette.Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SettingFieldLabel(label: String, hint: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = FuturePalette.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(hint, color = FuturePalette.Muted, fontSize = 10.sp)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = FuturePalette.Text,
    unfocusedTextColor = FuturePalette.Text,
    focusedContainerColor = Color(0x55101421),
    unfocusedContainerColor = Color(0x44101421),
    focusedBorderColor = FuturePalette.VioletBright,
    unfocusedBorderColor = FuturePalette.Edge,
    cursorColor = FuturePalette.VioletBright,
    focusedTrailingIconColor = FuturePalette.Muted,
    unfocusedTrailingIconColor = FuturePalette.Muted,
)

private fun ModelTask.label(): String = when (this) {
    ModelTask.GENERAL -> "普通对话"
    ModelTask.CODING -> "编程"
    ModelTask.REASONING -> "推理"
    ModelTask.LONG_CONTEXT -> "长文本"
    ModelTask.ANALYSIS -> "分析"
}

private fun CapabilityEvidence.label(): String = when (this) {
    CapabilityEvidence.VERIFIED -> "已验证"
    CapabilityEvidence.PROVIDER_DECLARED -> "服务商声明"
    CapabilityEvidence.UNKNOWN -> "未知"
}
