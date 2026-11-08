package com.tim.futureai.feature.settings

import com.tim.futureai.ui.liquid.GlassMaterial
import com.tim.futureai.ui.liquid.glassSurface
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import com.tim.futureai.ui.theme.ContinuousCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.tim.futureai.domain.ai.ProviderProtocol
import com.tim.futureai.domain.chat.SystemInstructionTemplate
import com.tim.futureai.feature.instructions.InstructionTemplateLibraryDialog

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
    onDuplicateProfile: (String) -> Unit = {},
    onExportProfile: (String) -> Unit = {},
    onExportAllProfiles: () -> Unit = {},
    onImportProfile: (String) -> Boolean = { false },
    onTestConnection: () -> Unit = {},
    onFetchModels: () -> Unit = {},
    modelsLoading: Boolean = false,
    usage: UsageSnapshot = UsageSnapshot(),
    includeUsageAndUpdates: Boolean = true,
    sessionReferences: Map<String, Int> = emptyMap(),
    instructionTemplates: List<SystemInstructionTemplate> = emptyList(),
    onSaveInstructionTemplate: (SystemInstructionTemplate) -> Boolean = { false },
    onDeleteInstructionTemplate: (String) -> Boolean = { false },
    onImportInstructionTemplates: (String) -> String = { "导入不可用" },
    onExportInstructionTemplates: (String) -> Unit = {},
) {
    var showApiKey by remember { mutableStateOf(false) }
    var evidenceMenuExpanded by remember { mutableStateOf(false) }
    var protocolMenuExpanded by remember { mutableStateOf(false) }
    var modelPickerOpen by remember { mutableStateOf(false) }
    var modelFilter by remember { mutableStateOf("") }
    var temperaturePresetExpanded by remember { mutableStateOf(false) }
    var outputPresetExpanded by remember { mutableStateOf(false) }
    var profileQuery by remember { mutableStateOf("") }
    var importProfileOpen by remember { mutableStateOf(false) }
    var importProfileText by remember { mutableStateOf("") }
    var deleteProfileId by remember { mutableStateOf<String?>(null) }
    var showInstructionLibrary by remember { mutableStateOf(false) }
    val spec = LocalLiquidSpec.current
    val frameDensity = LocalDensity.current
    var frameHeader by remember { mutableStateOf(88.dp) }
    LiquidStage(
        modifier = modifier.imePadding(),
        overlay = {
            Column(Modifier.fillMaxWidth()
                    .onSizeChanged { frameHeader = with(frameDensity) { it.height.toDp() } }
                    .glassSurface(GlassMaterial.FROSTED, RectangleShape).statusBarsPadding().padding(start = 18.dp, end = 20.dp, top = 10.dp, bottom = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).glassPanel(CircleShape).clickable(onClick = onBack),
                            contentAlignment = Alignment.Center,
                        ) { FutureGlyph("back", tint = FuturePalette.Text, size = 20.dp) }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI 服务", color = FuturePalette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text("连接 ${settings.providerProtocol.label()}", color = FuturePalette.Muted, fontSize = 11.sp)
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
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = frameHeader + 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(17.dp),
            ) {
                Text("连接你信赖的模型", color = FuturePalette.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth().glassPanel(ContinuousCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("启用此服务商参与选模", color = FuturePalette.Text, fontSize = 12.sp)
                        Text("停用后不会自动路由；手动发送也会被阻止", color = FuturePalette.Muted, fontSize = 10.sp)
                    }
                    Checkbox(checked = settings.enabled, onCheckedChange = { onSettingsChanged(settings.copy(enabled = it)) })
                }
                Column(
                    modifier = Modifier.fillMaxWidth().glassPanel(ContinuousCornerShape(22.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("服务商", color = FuturePalette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { importProfileOpen = true }) { Text("导入") }
                        TextButton(onClick = onCreateProfile) { Text("新增") }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        Text("本地预设", color = FuturePalette.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp, end = 8.dp))
                        com.tim.futureai.domain.ai.LocalModelTier.entries.forEach { tier -> TextButton(onClick = { onCreateLocalPreset(tier) }) { Text(tier.label, fontSize = 11.sp) } }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${profiles.size} 个服务商配置", color = FuturePalette.Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = onExportAllProfiles, enabled = profiles.isNotEmpty()) {
                            Text("导出全部 JSON", color = FuturePalette.Cyan, fontSize = 10.sp)
                        }
                    }
                    if (profiles.size > 3) {
                        OutlinedTextField(
                            value = profileQuery,
                            onValueChange = { profileQuery = it },
                            placeholder = { Text("搜索配置名称、协议或模型", color = FuturePalette.Muted) },
                            singleLine = true,
                            shape = ContinuousCornerShape(14.dp),
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val visibleProfiles = profiles.filter { profile ->
                        val query = profileQuery.trim()
                        query.isBlank() || profile.name.contains(query, ignoreCase = true) ||
                            profile.model.contains(query, ignoreCase = true) || profile.providerProtocol.name.contains(query, ignoreCase = true)
                    }
                    visibleProfiles.forEach { profile ->
                        Column(Modifier.fillMaxWidth().clip(ContinuousCornerShape(14.dp)).background(spec.ambientMiddle).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(profile.name, color = spec.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Text("${if (profile.enabled) "已启用" else "已停用"} · ${profile.model}", color = spec.muted, fontSize = 11.sp, maxLines = 2)
                                }
                                TextButton(onClick = { onSelectProfile(profile.id) }) { Text(if (profile.id == settings.id) "编辑中" else "编辑", fontSize = 12.sp) }
                            }
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                TextButton(onClick = { onExportProfile(profile.id) }) { Text("导出", fontSize = 11.sp) }
                                TextButton(onClick = { onDuplicateProfile(profile.id) }) { Text("复制", fontSize = 11.sp) }
                                TextButton(onClick = { deleteProfileId = profile.id }) { Text("删除", color = spec.danger, fontSize = 11.sp) }
                            }
                        }
                    }
                    if (visibleProfiles.isEmpty()) Text("没有匹配的服务商配置", color = FuturePalette.Muted, fontSize = 10.sp)
                    Text("复制配置会在加密存储中保留密钥；副本可独立修改模型、协议和能力。每个会话仍可单独选模。", color = FuturePalette.Muted, fontSize = 10.sp)
                    Text("JSON 导出只包含接口和模型设置，不包含 API Key、配置 ID 或连接测试历史。批量导入会停用副本并跳过重复配置。", color = FuturePalette.Muted, fontSize = 10.sp)
                }
                Column(
                    modifier = Modifier.fillMaxWidth().glassPanel(ContinuousCornerShape(26.dp)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    Text("连接信息", color = FuturePalette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("接口协议", color = FuturePalette.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Box {
                        TextButton(onClick = { protocolMenuExpanded = true }) {
                            Text(settings.providerProtocol.label() + "  ▾", color = FuturePalette.Cyan, fontSize = 12.sp)
                        }
                        DropdownMenu(expanded = protocolMenuExpanded, onDismissRequest = { protocolMenuExpanded = false }) {
                            listOf(
                                ProviderProtocol.OPENAI_COMPATIBLE,
                                ProviderProtocol.ANTHROPIC_MESSAGES,
                                ProviderProtocol.GEMINI_INTERACTIONS,
                                ProviderProtocol.GEMINI_GENERATE_CONTENT,
                            ).forEach { protocol ->
                                DropdownMenuItem(
                                    text = { Text(protocol.label()) },
                                    onClick = {
                                        onSettingsChanged(settings.copy(providerProtocol = protocol))
                                        protocolMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        when (settings.providerProtocol) {
                            ProviderProtocol.OPENAI_COMPATIBLE -> "Base URL 填到 /v1；请求会使用 Chat Completions 格式。"
                            ProviderProtocol.ANTHROPIC_MESSAGES -> "Base URL 填到 /v1；使用 Anthropic Messages 原生协议与 x-api-key 认证。"
                            ProviderProtocol.GEMINI_INTERACTIONS -> "Base URL 填到 /v1beta；使用 Google AI Studio 的 x-goog-api-key。请求采用无服务器历史存储模式。"
                            ProviderProtocol.GEMINI_GENERATE_CONTENT -> "Base URL 填到 /v1beta；使用 Gemini GenerateContent 兼容旧版项目配置。"
                        },
                        color = FuturePalette.Muted, fontSize = 10.sp, lineHeight = 15.sp,
                    )
                    SettingFieldLabel("服务商系统指令", "此指令会附加到使用该配置的每次请求中")
                    TextButton(onClick = { showInstructionLibrary = true }) {
                        Text("模板库 · ${instructionTemplates.size}", color = FuturePalette.Cyan, fontSize = 11.sp)
                    }
                    val instructionTemplates = listOf(
                        "简洁助手" to "请直接回答问题，优先给出结论。使用清晰、简洁的中文；复杂内容再用分点说明。遇到不确定的信息，请明确说明。",
                        "编程助手" to "你是一名严谨的编程助手。先理解现有项目结构和约束，再给出可执行、易维护的方案。涉及代码时说明关键改动；不确定时先指出假设，不要编造 API。",
                        "研究分析" to "请把事实、推断和建议区分开。优先使用可靠且较新的信息；涉及争议或不确定结论时说明依据与局限。结构化呈现重点，避免重复。",
                        "翻译助手" to "请忠实保留原文含义、语气和格式进行翻译。默认只输出译文；遇到有歧义的术语时，简短标注可能的含义。",
                    )
                    Text("快速模板", color = FuturePalette.Muted, fontSize = 10.sp)
                    instructionTemplates.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (title, prompt) ->
                                Column(
                                    Modifier.weight(1f).clip(ContinuousCornerShape(12.dp))
                                        .background(spec.surfaceTint.copy(alpha = 0.45f)).padding(9.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(title, color = FuturePalette.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        TextButton(onClick = { onSettingsChanged(settings.copy(systemInstruction = prompt)) }) {
                                            Text("覆盖", color = FuturePalette.Cyan, fontSize = 10.sp)
                                        }
                                        TextButton(onClick = {
                                            val current = settings.systemInstruction.trim()
                                            val combined = listOf(current, prompt).filter(String::isNotBlank).joinToString("\n\n")
                                            onSettingsChanged(settings.copy(systemInstruction = combined.take(10_000)))
                                        }) {
                                            Text("追加", color = FuturePalette.VioletBright, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    OutlinedTextField(
                        value = settings.systemInstruction,
                        onValueChange = { onSettingsChanged(settings.copy(systemInstruction = it.take(10_000))) },
                        placeholder = { Text("例如：请用简洁、清晰的中文回答。", color = FuturePalette.Muted.copy(alpha = 0.75f)) },
                        minLines = 3,
                        maxLines = 7,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = ContinuousCornerShape(16.dp),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${settings.systemInstruction.length} / 10000 字符 · 不建议放入密钥或隐私资料", color = FuturePalette.Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onSettingsChanged(settings.copy(systemInstruction = "")) }, enabled = settings.systemInstruction.isNotEmpty()) {
                            Text("清空", color = FuturePalette.Cyan, fontSize = 10.sp)
                        }
                    }
                    SettingFieldLabel("配置名称", "用于区分服务商")
                    OutlinedTextField(
                        value = settings.name,
                        onValueChange = { onSettingsChanged(settings.copy(name = it)) },
                        singleLine = true,
                        shape = ContinuousCornerShape(16.dp),
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
                        shape = ContinuousCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = settings.contextWindowTokens,
                        onValueChange = { onSettingsChanged(settings.copy(contextWindowTokens = it.filter(Char::isDigit).take(9))) },
                        label = { Text("上下文容量（tokens；未知留空）") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = ContinuousCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
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
                    Text("“已验证”仅供未来由可复现测试确认后记录；当前不会伪造模型成绩或容量。", color = FuturePalette.Muted, fontSize = 10.sp)
                    OutlinedTextField(
                        value = settings.preference.toString(),
                        onValueChange = { value -> value.toIntOrNull()?.let { onSettingsChanged(settings.copy(preference = it.coerceIn(-20, 20))) } },
                        label = { Text("自动选模偏好（-20 到 20）") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = ContinuousCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                    )
                    SettingFieldLabel("API Base URL", when (settings.providerProtocol) {
                        ProviderProtocol.ANTHROPIC_MESSAGES -> "例如 https://api.anthropic.com/v1"
                        ProviderProtocol.GEMINI_INTERACTIONS -> "例如 https://generativelanguage.googleapis.com/v1beta"
                        else -> "OpenAI-compatible 接口地址"
                    })
                    OutlinedTextField(
                        value = settings.baseUrl,
                        onValueChange = { onSettingsChanged(settings.copy(baseUrl = it)) },
                        placeholder = { Text("https://api.example.com/v1", color = FuturePalette.Muted.copy(alpha = 0.75f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None),
                        shape = ContinuousCornerShape(16.dp),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingFieldLabel("Model", "填写服务商提供的模型 ID", Modifier.weight(1f))
                        TextButton(onClick = onFetchModels, enabled = !modelsLoading) {
                            Text(if (modelsLoading) "读取中…" else "读取模型目录", color = FuturePalette.Cyan, fontSize = 10.sp)
                        }
                        val modelIds = (listOf(settings.model) + settings.availableModels.split(Regex("[,\\r\\n]+")))
                            .map(String::trim).filter(String::isNotBlank).distinct()
                        run {
                            TextButton(onClick = { modelPickerOpen = true }, enabled = modelIds.isNotEmpty()) {
                                Text("选择", color = FuturePalette.VioletBright, fontSize = 10.sp)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = settings.model,
                        onValueChange = { onSettingsChanged(settings.copy(model = it)) },
                        placeholder = { Text("例如：gpt-4o-mini", color = FuturePalette.Muted.copy(alpha = 0.75f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        shape = ContinuousCornerShape(16.dp),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (settings.providerProtocol == ProviderProtocol.ANTHROPIC_MESSAGES)
                            "生成参数（Claude 温度范围 0–1；较新的模型可能拒绝非默认温度）"
                        else "生成参数（留空使用协议默认值；参数可能受具体模型限制）",
                        color = FuturePalette.Muted, fontSize = 10.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = settings.temperature,
                            onValueChange = { value ->
                                val cleaned = value.filter { it.isDigit() || it == '.' }.take(8)
                                onSettingsChanged(settings.copy(temperature = cleaned))
                            },
                            label = { Text(if (settings.providerProtocol == ProviderProtocol.ANTHROPIC_MESSAGES) "温度（0–1）" else "温度（0–2）") },
                            placeholder = { Text("默认") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = ContinuousCornerShape(14.dp),
                            colors = fieldColors(),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = settings.maxOutputTokens,
                            onValueChange = { value ->
                                onSettingsChanged(settings.copy(maxOutputTokens = value.filter(Char::isDigit).take(9)))
                            },
                            label = { Text("最大输出 Token") },
                            placeholder = { Text(if (settings.providerProtocol == ProviderProtocol.ANTHROPIC_MESSAGES) "4096" else "默认") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = ContinuousCornerShape(14.dp),
                            colors = fieldColors(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            TextButton(onClick = { temperaturePresetExpanded = true }) { Text("温度预设 ▾", color = FuturePalette.Cyan, fontSize = 10.sp) }
                            DropdownMenu(expanded = temperaturePresetExpanded, onDismissRequest = { temperaturePresetExpanded = false }) {
                                listOf("默认" to "", "精确 0.2" to "0.2", "平衡 0.7" to "0.7", "创意 1.0" to "1.0").forEach { (label, value) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = { onSettingsChanged(settings.copy(temperature = value)); temperaturePresetExpanded = false },
                                    )
                                }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            TextButton(onClick = { outputPresetExpanded = true }) { Text("输出上限预设 ▾", color = FuturePalette.Cyan, fontSize = 10.sp) }
                            DropdownMenu(expanded = outputPresetExpanded, onDismissRequest = { outputPresetExpanded = false }) {
                                listOf("协议默认" to "", "短回复 512" to "512", "标准 2048" to "2048", "长回复 4096" to "4096").forEach { (label, value) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = { onSettingsChanged(settings.copy(maxOutputTokens = value)); outputPresetExpanded = false },
                                    )
                                }
                            }
                        }
                    }
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = ContinuousCornerShape(14.dp),
                            colors = fieldColors(), modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = settings.outputUsdPerMillion,
                            onValueChange = { onSettingsChanged(settings.copy(outputUsdPerMillion = it)) },
                            label = { Text("输出") }, placeholder = { Text("例如 0.60") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = ContinuousCornerShape(14.dp),
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
                        shape = ContinuousCornerShape(16.dp),
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
                    SettingFieldLabel("自定义请求头", "每行一个 Name: value；适用于租户、网关等额外认证")
                    OutlinedTextField(
                        value = settings.customHeaders,
                        onValueChange = { onSettingsChanged(settings.copy(customHeaders = it.take(8_192))) },
                        placeholder = { Text("X-Tenant-ID: team-123\nX-API-Version: 2025-01-01", color = FuturePalette.Muted.copy(alpha = 0.75f)) },
                        minLines = 2, maxLines = 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        shape = ContinuousCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                    )
                    Text("最多 20 项。请求头与 API Key 一样加密保存；克隆配置会保留，JSON 导出不会包含这些值。协议认证头和内容协商头由应用管理。", color = FuturePalette.Muted, fontSize = 10.sp)
                    if (settings.providerProtocol == ProviderProtocol.OPENAI_COMPATIBLE) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = settings.connectTimeoutSeconds,
                                onValueChange = { onSettingsChanged(settings.copy(connectTimeoutSeconds = it.filter(Char::isDigit).take(3))) },
                                label = { Text("连接超时（秒）") }, placeholder = { Text("15") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = ContinuousCornerShape(14.dp),
                                colors = fieldColors(), modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = settings.readTimeoutSeconds,
                                onValueChange = { onSettingsChanged(settings.copy(readTimeoutSeconds = it.filter(Char::isDigit).take(3))) },
                                label = { Text("响应空闲（秒）") }, placeholder = { Text("60") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = ContinuousCornerShape(14.dp),
                                colors = fieldColors(), modifier = Modifier.weight(1f),
                            )
                        }
                        Text("连接 1–120 秒；响应空闲 1–600 秒。响应空闲是流式输出两段数据之间允许等待的时间，不是总生成时长。留空使用默认值。", color = FuturePalette.Muted, fontSize = 10.sp)
                    }
                    if (message != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            FutureGlyph(if (messageIsError) "cloud" else "check", tint = if (messageIsError) FuturePalette.Danger else FuturePalette.Success, size = 17.dp)
                            Text(message, color = if (messageIsError) FuturePalette.Danger else FuturePalette.Success, fontSize = 12.sp)
                        }
                    }
                    Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(52.dp), shape = ContinuousCornerShape(16.dp)) {
                        Text("保存配置", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                    modifier = Modifier.fillMaxWidth().glassPanel(ContinuousCornerShape(18.dp)).padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    FutureGlyph("spark", tint = FuturePalette.Cyan, size = 17.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("支持 OpenAI-compatible、Anthropic Messages 与 Gemini Interactions / GenerateContent。连接测试只读模型目录，不生成回复。", color = FuturePalette.Muted, fontSize = 11.sp, lineHeight = 17.sp)
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
    if (modelPickerOpen) {
        val candidates = (listOf(settings.model) + settings.availableModels.split(Regex("[,\\r\\n]+")))
            .map(String::trim).filter(String::isNotBlank).distinct()
            .filter { it.contains(modelFilter.trim(), ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { modelPickerOpen = false; modelFilter = "" },
            title = { Text("选择默认模型") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = modelFilter,
                        onValueChange = { modelFilter = it },
                        placeholder = { Text("搜索模型 ID") },
                        singleLine = true,
                        shape = ContinuousCornerShape(12.dp),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(candidates) { id ->
                            TextButton(
                                onClick = {
                                    val allIds = (listOf(settings.model) + settings.availableModels.split(Regex("[,\\r\\n]+")))
                                        .map(String::trim).filter(String::isNotBlank).distinct()
                                    onSettingsChanged(settings.copy(model = id, availableModels = allIds.filterNot { it == id }.joinToString("\n")))
                                    modelPickerOpen = false
                                    modelFilter = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(id, color = FuturePalette.Text, maxLines = 1) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { modelPickerOpen = false; modelFilter = "" }) { Text("完成") } },
        )
    }
    if (importProfileOpen) {
        AlertDialog(
            onDismissRequest = { importProfileOpen = false; importProfileText = "" },
            title = { Text("导入服务商配置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("粘贴单配置或批量配置包 JSON。单配置会载入可编辑草稿；批量配置会新增为停用项、跳过重复项，不覆盖现有配置。所有导入配置都需要重新输入 API Key。", color = FuturePalette.Muted, fontSize = 11.sp)
                    OutlinedTextField(
                        value = importProfileText,
                        onValueChange = { importProfileText = it.take(1_000_000) },
                        placeholder = { Text("粘贴配置 JSON") },
                        minLines = 5,
                        maxLines = 9,
                        shape = ContinuousCornerShape(14.dp),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onImportProfile(importProfileText)) {
                        importProfileOpen = false
                        importProfileText = ""
                    }
                }) { Text("导入草稿") }
            },
            dismissButton = { TextButton(onClick = { importProfileOpen = false; importProfileText = "" }) { Text("取消") } },
        )
    }
    if (showInstructionLibrary) InstructionTemplateLibraryDialog(
        templates = instructionTemplates,
        initialInstruction = settings.systemInstruction,
        onApply = { prompt, append ->
            val next = if (!append || settings.systemInstruction.isBlank()) prompt
            else "${settings.systemInstruction.trim()}\n\n$prompt"
            onSettingsChanged(settings.copy(systemInstruction = next.take(10_000)))
        },
        onSave = onSaveInstructionTemplate,
        onDelete = onDeleteInstructionTemplate,
        onImport = onImportInstructionTemplates,
        onExport = onExportInstructionTemplates,
        onDismiss = { showInstructionLibrary = false },
    )
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
        Modifier.fillMaxWidth().glassPanel(ContinuousCornerShape(22.dp)).padding(16.dp),
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
                color = FuturePalette.Muted, fontSize = 10.sp,
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
                    color = FuturePalette.Muted, fontSize = 10.sp, maxLines = 2,
                )
            }
            if (sessions.size > 10) Text("另有 ${sessions.size - 10} 个会话，请使用时间筛选缩小范围。", color = FuturePalette.Muted, fontSize = 10.sp)
        }
        usage.records.takeLast(5).asReversed().forEach { record ->
            Text(
                "${record.providerName} · ${record.model} · ${record.durationMs} ms" +
                    (record.errorType?.let { " · $it" } ?: "") +
                    (record.tokenUsage?.let { " · ${it.promptTokens}+${it.completionTokens} tokens" } ?: " · Token 未知"),
                color = FuturePalette.Muted, fontSize = 10.sp, maxLines = 1,
            )
        }
    }
}

@Composable
private fun IntroPanel() {
    Row(
        modifier = Modifier.fillMaxWidth().glassPanel(ContinuousCornerShape(22.dp)).padding(16.dp),
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

private fun ProviderProtocol.label(): String = when (this) {
    ProviderProtocol.OPENAI_COMPATIBLE -> "OpenAI-compatible"
    ProviderProtocol.ANTHROPIC_MESSAGES -> "Anthropic Messages"
    ProviderProtocol.GEMINI_INTERACTIONS -> "Gemini Interactions"
    ProviderProtocol.GEMINI_GENERATE_CONTENT -> "Gemini GenerateContent（兼容）"
}
