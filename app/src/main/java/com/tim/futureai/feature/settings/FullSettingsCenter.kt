package com.tim.futureai.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.futureai.domain.ai.AiSettings
import com.tim.futureai.domain.ai.CapabilityEvidence
import com.tim.futureai.domain.settings.AppPreferences
import com.tim.futureai.domain.settings.RoutingMode
import com.tim.futureai.domain.settings.SettingsSection
import com.tim.futureai.domain.settings.SettingsSearchEntry
import com.tim.futureai.domain.settings.SettingsSearchRegistry
import com.tim.futureai.domain.settings.ThemeMode
import com.tim.futureai.domain.settings.VisualTheme
import com.tim.futureai.domain.task.TaskAuthorizationPolicy
import com.tim.futureai.domain.usage.UsageSnapshot
import com.tim.futureai.domain.usage.UsageRange
import com.tim.futureai.feature.update.UpdateCheckResult
import com.tim.futureai.ui.liquid.LiquidStage
import com.tim.futureai.ui.liquid.LocalLiquidSpec
import com.tim.futureai.ui.liquid.spec
import com.tim.futureai.ui.theme.FutureGlyph
import com.tim.futureai.ui.theme.FuturePalette
import com.tim.futureai.ui.theme.glassPanel

/** Single registry drives the settings home and search navigation; category pages expose only real features. */
@Composable
fun FullSettingsCenter(
    preferences: AppPreferences,
    onPreferencesChanged: (AppPreferences) -> Unit,
    versionName: String,
    versionCode: Int,
    buildInfo: String,
    defaultModel: String,
    updateStatus: String,
    providerCount: Int,
    enabledProviderCount: Int,
    profiles: List<AiSettings>,
    usage: UsageSnapshot,
    sessionCount: Int,
    taskCount: Int,
    historyBytes: Long,
    cacheBytes: Long,
    migrationStatus: String,
    voiceInputAvailable: Boolean,
    ttsSettingsAvailable: Boolean,
    operationMessage: String?,
    latestErrorType: String?,
    animationsEnabledBySystem: Boolean,
    onResetPreferences: () -> Unit,
    onClearHistory: () -> Unit,
    onClearUsage: () -> Unit,
    onRefreshUsage: () -> Unit,
    onClearCache: () -> Unit,
    onClearTasks: () -> Unit,
    onCopyVersion: () -> Unit,
    onCopyDiagnostics: () -> Unit,
    onOpenGitHub: () -> Unit,
    onRequestVoiceInput: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onBack: () -> Unit,
    providerContent: @Composable (onBack: () -> Unit) -> Unit,
    usageContent: @Composable (UsageSnapshot) -> Unit,
    updatesContent: @Composable () -> Unit,
    healthContent: @Composable (onBack: () -> Unit) -> Unit,
    arenaContent: @Composable (onBack: () -> Unit) -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(SettingsSection.GENERAL) }
    // Edge swipe / system back: subpage returns to the category home first,
    // the category home then leaves the settings center (handled by MainActivity).
    BackHandler(enabled = section != SettingsSection.GENERAL) { section = SettingsSection.GENERAL }
    var query by rememberSaveable { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<SettingsSection?>(null) }
    var showPrototype by rememberSaveable { mutableStateOf(false) }
    val stateHolder = rememberSaveableStateHolder()
    val transitionMillis = if (preferences.animationsEnabled && animationsEnabledBySystem) 160 else 0

    if (showPrototype) {
        LiquidPrototypeScreen(onBack = { showPrototype = false })
        return
    }

    AnimatedContent(section, transitionSpec = { fadeIn(tween(transitionMillis)) togetherWith fadeOut(tween(transitionMillis)) }, label = "settings-category") { target ->
            stateHolder.SaveableStateProvider(target.name) {
                if (target == SettingsSection.PROVIDERS) {
                    providerContent { section = SettingsSection.GENERAL }
                } else if (target == SettingsSection.HEALTH) {
                    healthContent { section = SettingsSection.GENERAL }
                } else if (target == SettingsSection.ARENA) {
                    arenaContent { section = SettingsSection.GENERAL }
                } else {
                    SettingsPageFrame(
                        title = if (target == SettingsSection.GENERAL) "设置中心" else target.title,
                        onBack = { if (target == SettingsSection.GENERAL) onBack() else section = SettingsSection.GENERAL },
                    ) {
                    when (target) {
                        SettingsSection.GENERAL -> GeneralPage(
                            versionName, versionCode, buildInfo, defaultModel, updateStatus, query,
                            onQuery = { query = it }, onOpen = { section = it }, onCopyVersion = onCopyVersion,
                            onReset = { confirmation = SettingsSection.GENERAL }, providerCount = providerCount, profiles = profiles,
                        )
                        SettingsSection.APPEARANCE -> AppearancePage(preferences, onPreferencesChanged, onOpenPrototype = { showPrototype = true })
                        SettingsSection.CHAT -> ChatPage(preferences, onPreferencesChanged, sessionCount,
                            onClearHistory = { confirmation = SettingsSection.CHAT })
                        SettingsSection.MODEL_ROUTING -> RoutingPage(preferences, onPreferencesChanged, profiles)
                        SettingsSection.TASKS -> TaskEngineSettingsPage(preferences, onPreferencesChanged, taskCount, operationMessage) {
                            confirmation = SettingsSection.TASKS
                        }
                        SettingsSection.USAGE -> UsagePage(usage, onRefreshUsage, { confirmation = SettingsSection.USAGE }, { section = SettingsSection.PROVIDERS }, usageContent)
                        SettingsSection.ATTACHMENTS -> AttachmentsPage(operationMessage) { confirmation = SettingsSection.STORAGE }
                        SettingsSection.VOICE -> VoicePage(preferences, onPreferencesChanged, voiceInputAvailable, ttsSettingsAvailable, onRequestVoiceInput, onOpenTtsSettings)
                        SettingsSection.UPDATES -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PreferenceSwitch("每次启动自动检查", "关闭后仍可在本页无限次手动检查", preferences.startupUpdateCheck) {
                                onPreferencesChanged(preferences.copy(startupUpdateCheck = it))
                            }
                            updatesContent()
                        }
                        SettingsSection.SECURITY -> SecurityPage(providerCount, enabledProviderCount, onOpenProviders = { section = SettingsSection.PROVIDERS }, onCopyDiagnostics = onCopyDiagnostics)
                        SettingsSection.STORAGE -> StoragePage(sessionCount, historyBytes, cacheBytes, migrationStatus, operationMessage,
                            onClearHistory = { confirmation = SettingsSection.CHAT }, onClearCache = { confirmation = SettingsSection.STORAGE })
                        SettingsSection.DIAGNOSTICS -> DiagnosticsPage(versionName, versionCode, buildInfo, providerCount, enabledProviderCount,
                            updateStatus, operationMessage, latestErrorType, onCopyDiagnostics, onOpenGitHub, onOpenUpdates = { section = SettingsSection.UPDATES })
                        SettingsSection.PROVIDERS, SettingsSection.HEALTH, SettingsSection.ARENA -> Unit
                    }
                    }
                }
            }
        }

    confirmation?.let { action ->
        val (title, body, button) = when (action) {
            SettingsSection.GENERAL -> Triple("重置应用设置？", "仅恢复外观、路由、语音和启动检查偏好。服务商密钥、聊天历史与用量统计不会删除。", "重置偏好")
            SettingsSection.CHAT -> Triple("清除全部聊天历史？", "所有会话、消息及其引用的附件文件将从本机删除；服务商配置与用量统计保留。此操作不可撤销。", "清除历史")
            SettingsSection.USAGE -> Triple("清除用量统计？", "本机请求记录和 Token 统计将被删除；聊天内容与服务商配置保留。", "清除统计")
            SettingsSection.STORAGE -> Triple("清理临时缓存？", "仅删除应用 cache 目录中的临时文件，不删除聊天附件、会话或已验证更新 APK。", "清理缓存")
            SettingsSection.TASKS -> Triple("清理已结束任务？", "只删除已验证完成、失败或取消的任务及其审计历史；正在运行、等待授权或暂停的任务会保留。", "清理任务历史")
            else -> Triple("确认操作？", "请确认影响范围。", "确认")
        }
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(title) }, text = { Text(body) },
            confirmButton = { TextButton(onClick = {
                when (action) {
                    SettingsSection.GENERAL -> onResetPreferences()
                    SettingsSection.CHAT -> onClearHistory()
                    SettingsSection.USAGE -> onClearUsage()
                    SettingsSection.STORAGE -> onClearCache()
                    SettingsSection.TASKS -> onClearTasks()
                    else -> Unit
                }
                confirmation = null
            }) { Text(button, color = if (action == SettingsSection.GENERAL) FuturePalette.VioletBright else FuturePalette.Danger) } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SettingsPageFrame(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    LiquidStage(
        overlay = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).clip(CircleShape).glassPanel(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    FutureGlyph("back", tint = FuturePalette.Text, size = 19.dp)
                }
                Column(Modifier.weight(1f).padding(start = 13.dp)) {
                    Text(title, color = FuturePalette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Gather AI · Liquid Reality", color = FuturePalette.Muted, fontSize = 10.sp)
                }
            }
        },
        source = {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 104.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
                Spacer(Modifier.height(10.dp))
            }
        },
    )
}

@Composable
private fun GeneralPage(
    version: String, code: Int, build: String, defaultModel: String, update: String, query: String,
    onQuery: (String) -> Unit, onOpen: (SettingsSection) -> Unit, onCopyVersion: () -> Unit,
    onReset: () -> Unit, providerCount: Int,
    profiles: List<AiSettings>,
) {
    Column(Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("$version · v$code · $build", color = FuturePalette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("默认模型：$defaultModel", color = FuturePalette.Cyan, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("更新：$update · 服务商 $providerCount", color = FuturePalette.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("复制版本信息", color = FuturePalette.VioletBright, fontSize = 10.sp, modifier = Modifier.clickable(onClick = onCopyVersion).padding(horizontal = 8.dp, vertical = 4.dp))
            Text("检查更新", color = FuturePalette.VioletBright, fontSize = 10.sp, modifier = Modifier.clickable { onOpen(SettingsSection.UPDATES) }.padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
    SearchField(query, onQuery)
    val searchableProviders = profiles.map { profile ->
        profile.name to (listOf(profile.model) + profile.availableModels.lines().map(String::trim).filter(String::isNotBlank))
    }
    val results = remember(query, profiles) { SettingsSearchRegistry.search(query, searchableProviders) }
    if (query.isNotBlank()) {
        if (results.isEmpty()) Text("没有匹配的设置项", color = FuturePalette.Muted, modifier = Modifier.padding(10.dp))
        results.forEach { SearchResultCard(it, onClick = { onOpen(it.section) }) }
    } else {
        SettingsSection.registered.forEach { SectionCard(it, onClick = { onOpen(it) }) }
    }
    InfoCard("默认启动行为", "启动后进入最近的聊天界面；更新检查由“更新管理”中的开关控制。")
    InfoCard("语言", "当前界面使用简体中文；应用未附带其他完整语言资源，因此不提供无效切换项。")
    ActionButton("重置应用偏好", danger = true, onClick = onReset)
}

@Composable
private fun TaskEngineSettingsPage(
    preferences: AppPreferences,
    onPreferencesChanged: (AppPreferences) -> Unit,
    taskCount: Int,
    operationMessage: String?,
    onClearTasks: () -> Unit,
) {
    PreferenceSwitch("允许任务模式", "关闭后不能创建新任务；已保存任务和审计记录仍可查看。", preferences.taskModeEnabled) {
        onPreferencesChanged(preferences.copy(taskModeEnabled = it))
    }
    SettingChoice(
        title = "默认授权策略（不覆盖工具自身的强制确认）",
        options = listOf(
            "仅允许无副作用工具" to TaskAuthorizationPolicy.SAFE_ONLY,
            "副作用工具逐次确认" to TaskAuthorizationPolicy.CONFIRM_SIDE_EFFECTS,
            "每次工具调用都确认" to TaskAuthorizationPolicy.CONFIRM_EACH_TOOL,
        ),
        selected = preferences.taskAuthorizationPolicy,
        onSelect = { onPreferencesChanged(preferences.copy(taskAuthorizationPolicy = it)) },
    )
    SettingSlider("单任务最大步骤", preferences.taskMaxSteps.toFloat(), 1f..30f, "${preferences.taskMaxSteps}") {
        onPreferencesChanged(preferences.copy(taskMaxSteps = it.toInt()))
    }
    SettingSlider("最大重试次数", preferences.taskMaxRetries.toFloat(), 0f..3f, "${preferences.taskMaxRetries}") {
        onPreferencesChanged(preferences.copy(taskMaxRetries = it.toInt()))
    }
    SettingSlider("单步骤超时", preferences.taskStepTimeoutMs / 1_000f, 5f..120f, "${preferences.taskStepTimeoutMs / 1_000} 秒") {
        onPreferencesChanged(preferences.copy(taskStepTimeoutMs = it.toLong() * 1_000))
    }
    SettingSlider("任务总时限", preferences.taskTotalTimeoutMs / 1_000f, 10f..900f, "${preferences.taskTotalTimeoutMs / 1_000} 秒") {
        onPreferencesChanged(preferences.copy(taskTotalTimeoutMs = it.toLong() * 1_000))
    }
    PreferenceSwitch("执行前检查计划", "开启后每个新计划都停在待执行状态，必须由你手动开始。", preferences.requireTaskPlanReview) {
        onPreferencesChanged(preferences.copy(requireTaskPlanReview = it))
    }
    PreferenceSwitch("保留任务历史", "关闭后任务结束时会清除其本机快照；运行中任务仍会保留。", preferences.retainTaskHistory) {
        onPreferencesChanged(preferences.copy(retainTaskHistory = it))
    }
    InfoCard("任务存储", "当前保存 $taskCount 个任务。任务文件位于应用私有目录；清理只删除已结束任务，不会触及暂停或待授权任务。")
    if (!operationMessage.isNullOrBlank()) InfoCard("操作状态", operationMessage)
    ActionButton("清理已结束任务历史", danger = true, onClick = onClearTasks)
}

@Composable
private fun SettingSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = FuturePalette.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(label, color = FuturePalette.Cyan, fontSize = 11.sp)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(FuturePalette.Panel).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        FutureGlyph("search", tint = FuturePalette.Muted, size = 18.dp)
        Spacer(Modifier.width(9.dp))
        BasicTextField(value = query, onValueChange = onChange, singleLine = true, modifier = Modifier.weight(1f), textStyle = androidx.compose.ui.text.TextStyle(color = FuturePalette.Text, fontSize = 14.sp), decorationBox = { inner ->
            if (query.isEmpty()) Text("搜索服务商、主题、语音、更新…", color = FuturePalette.Muted, fontSize = 13.sp)
            inner()
        })
        if (query.isNotEmpty()) Text("清除", color = FuturePalette.Cyan, modifier = Modifier.clickable { onChange("") }.padding(start = 8.dp))
    }
}

@Composable
private fun SectionCard(section: SettingsSection, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(FuturePalette.Violet.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            FutureGlyph(glyphFor(section), tint = FuturePalette.VioletBright, size = 17.dp)
        }
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(section.title, color = FuturePalette.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(section.description, color = FuturePalette.Muted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = FuturePalette.Cyan, fontSize = 22.sp)
    }
}

@Composable
private fun SearchResultCard(entry: SettingsSearchEntry, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = FuturePalette.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(entry.section.title, color = FuturePalette.Cyan, fontSize = 10.sp)
        }
        Text("›", color = FuturePalette.Cyan, fontSize = 22.sp)
    }
}

@Composable
private fun AppearancePage(p: AppPreferences, change: (AppPreferences) -> Unit, onOpenPrototype: () -> Unit) {
    InfoCard("外观预览", "主题、材质和动效设置立即应用并加密持久化；所有页面共享同一套 Liquid 设计令牌。")
    ThemePicker(p.visualTheme) { change(p.copy(visualTheme = it)) }
    Column(Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("玻璃材质强度", color = FuturePalette.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("${(p.glassIntensity * 100).toInt()}%", color = FuturePalette.Cyan, fontSize = 11.sp)
        }
        Slider(value = p.glassIntensity, onValueChange = { change(p.copy(glassIntensity = it)) }, valueRange = 0f..1f)
        Text("控制模糊半径与边缘高光；0% 时玻璃退化为不透明的纯净表面。", color = FuturePalette.Muted, fontSize = 10.sp, lineHeight = 15.sp)
    }
    PreferenceSwitch("低性能模式", "停用实时模糊与折射，保留完整主题配色；卡顿设备或省电时使用。", p.lowPerformanceMode) { change(p.copy(lowPerformanceMode = it)) }
    PreferenceSwitch("动态背景", "极光等主题的光斑将缓慢漂移；关闭后为静态渲染。", p.dynamicBackground) { change(p.copy(dynamicBackground = it)) }
    PreferenceSwitch("紧凑聊天间距", "减少消息列表纵向留白", p.compactChat) { change(p.copy(compactChat = it)) }
    PreferenceSwitch("界面过渡动画", "遵循系统关闭动画设置", p.animationsEnabled) { change(p.copy(animationsEnabled = it)) }
    Column(Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text("字体大小 · ${(p.fontScale * 100).toInt()}%", color = FuturePalette.Text, fontSize = 13.sp)
        Slider(value = p.fontScale, onValueChange = { change(p.copy(fontScale = it)) }, valueRange = 0.85f..1.25f)
        Text("控件触控区域保持不变；超长文本仍可滚动。", color = FuturePalette.Muted, fontSize = 10.sp)
    }
    ActionButton("液态玻璃原型 · 材质验收", onClick = onOpenPrototype)
}

@Composable
private fun ThemePicker(selected: VisualTheme, onSelect: (VisualTheme) -> Unit) {
    Column(Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("主题", color = FuturePalette.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        VisualTheme.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { theme -> ThemePreviewCard(theme, theme == selected, Modifier.weight(1f)) { onSelect(theme) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(theme: VisualTheme, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val spec = theme.spec()
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(spec.ambientTop, spec.ambientMiddle, spec.ambientBottom)))
            .border(
                width = if (selected) 2.dp else 1.dp,
                brush = Brush.linearGradient(listOf(if (selected) spec.accentBright else spec.surfaceEdge, spec.surfaceEdge)),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(spec.accent.copy(alpha = 0.85f)))
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(spec.cyan.copy(alpha = 0.7f)))
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(spec.surfaceTint.copy(alpha = 0.95f)).border(1.dp, spec.surfaceEdge, RoundedCornerShape(7.dp)))
        }
        Column {
            Text(spec.label, color = spec.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(spec.description, color = spec.muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChatPage(p: AppPreferences, change: (AppPreferences) -> Unit, sessionCount: Int, onClearHistory: () -> Unit) {
    SettingChoice("默认路由模式", listOf("手动" to RoutingMode.MANUAL, "自动选模" to RoutingMode.AUTOMATIC), p.routingMode) { change(p.copy(routingMode = it)) }
    PreferenceSwitch("新会话继承全局默认模型", "关闭时新会话默认使用自动选模", p.inheritGlobalModel) { change(p.copy(inheritGlobalModel = it)) }
    PreferenceSwitch("显示模型名称", "控制聊天页当前模型标签", p.showModelNames) { change(p.copy(showModelNames = it)) }
    PreferenceSwitch("显示选模原因", "自动选择后显示实际模型与原因", p.showRoutingReason) { change(p.copy(showRoutingReason = it)) }
    InfoCard("消息发送行为", "输入法的“发送”键提交消息；聊天框保留多行输入。当前行为固定，无额外无效开关。")
    InfoCard("流式输出与请求控制", "回复通过 SSE 逐段显示。生成期间可停止；失败消息可重试，已收到的部分回复会保留。")
    InfoCard("会话模型优先级", "单条消息覆盖 > 当前会话选择 > 全局默认。手动指定的模型不会被自动替换。")
    SettingChoice("最多保留会话数", listOf("不限" to 0, "100" to 100, "500" to 500), p.historyLimit) { change(p.copy(historyLimit = it)) }
    InfoCard("当前会话", "$sessionCount 个。达到上限时保留当前会话和最近更新的会话。")
    ActionButton("清除全部聊天历史", danger = true, onClick = onClearHistory)
}

@Composable
private fun RoutingPage(p: AppPreferences, change: (AppPreferences) -> Unit, profiles: List<AiSettings>) {
    SettingChoice("默认选模模式", listOf("手动" to RoutingMode.MANUAL, "自动" to RoutingMode.AUTOMATIC), p.routingMode) { change(p.copy(routingMode = it)) }
    PreferenceSwitch("自动显示选择原因", "不会改变模型路由，仅控制解释文本显示", p.showRoutingReason) { change(p.copy(showRoutingReason = it)) }
    InfoCard("任务分类", "引擎识别普通对话、编程、推理、长文本与分析，并检查图片能力、上下文需求和服务商健康状态。")
    InfoCard("回退与数据边界", "单次请求只调用一个模型。失败后不会跨服务商自动转发；用户手动重试时才重新选模。")
    if (profiles.isEmpty()) InfoCard("模型能力", "尚无已配置模型；添加 OpenAI-compatible 服务商后即可查看登记信息。")
    profiles.forEach { profile ->
        val models = (listOf(profile.model) + profile.availableModels.lines().map(String::trim).filter(String::isNotBlank)).distinct()
        models.forEach { model ->
            InfoCard("${profile.name} · $model", "能力来源：${profile.capabilityEvidence.label()}；上下文：${profile.contextWindowTokens.takeIf(String::isNotBlank) ?: "未知"}；图片：${if (profile.supportsImages) "声明支持" else "未声明支持"}。偏好 ${profile.preference}。")
        }
    }
}

@Composable
private fun UsagePage(
    usage: UsageSnapshot,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onPrices: () -> Unit,
    content: @Composable (UsageSnapshot) -> Unit,
) {
    var range by rememberSaveable { mutableStateOf(UsageRange.ALL) }
    SettingChoice("统计时间范围", listOf("7 天" to UsageRange.LAST_7_DAYS, "30 天" to UsageRange.LAST_30_DAYS, "全部" to UsageRange.ALL), range) { range = it }
    val visible = usage.within(range)
    content(visible)
    InfoCard("统计口径", "优先显示服务商返回的真实 Token；未返回时保留未知。费用根据服务商/模型登记价格估算，不代表账单。")
    ActionButton("刷新统计", onClick = onRefresh)
    ActionButton("配置模型价格", onClick = onPrices)
    ActionButton("清除用量统计", danger = true, onClick = onClear)
}

@Composable
private fun AttachmentsPage(message: String?, onClearCache: () -> Unit) {
    InfoCard("图片", "Android 文件选择器导入；图片存放于应用私有附件目录。仅启用图片能力的模型可发送，内容会随请求发送给所选服务商。")
    InfoCard("文档", "当前提取 TXT、MD、CSV、JSON、XML 与 DOCX 文本；PDF 和其他未实现格式会明确提示不支持，不会伪称已分析。")
    InfoCard("限制与权限", "单个文件最大 10 MB，每次最多 5 个附件；通过系统文件选择器授权，不申请全盘文件访问权限。")
    message?.let { InfoCard("最近处理/清理状态", it) }
    InfoCard("临时文件", "已导入附件由会话引用；清理缓存不会删除聊天引用的附件。")
    ActionButton("清理临时缓存", onClick = onClearCache)
}

@Composable
private fun VoicePage(p: AppPreferences, change: (AppPreferences) -> Unit, inputAvailable: Boolean, ttsSettingsAvailable: Boolean, onTryInput: () -> Unit, onOpenTtsSettings: () -> Unit) {
    PreferenceSwitch("启用语音输入入口", "通过 Android 系统语音识别 Intent 转写；识别服务和麦克风权限由系统管理。", p.voiceInputEnabled) { change(p.copy(voiceInputEnabled = it)) }
    PreferenceSwitch("启用文字转语音", "使用 Android 默认 TTS；播放按钮可停止当前朗读。", p.textToSpeechEnabled) { change(p.copy(textToSpeechEnabled = it)) }
    InfoCard("语音识别状态", if (inputAvailable) "设备报告有语音识别 Activity；实际权限由系统识别服务请求。" else "设备未发现可用识别 Activity，可继续使用文字输入。")
    if (inputAvailable && p.voiceInputEnabled) ActionButton("试用语音输入（由系统处理授权）", onClick = onTryInput)
    if (p.textToSpeechEnabled && ttsSettingsAvailable) ActionButton("打开系统文字转语音设置", onClick = onOpenTtsSettings)
    else if (p.textToSpeechEnabled) InfoCard("TTS 引擎设置", "系统未提供可直接打开的引擎设置页，将继续使用系统默认 TTS 引擎。")
    InfoCard("隐私", "语音识别交由系统所选识别服务处理；Gather AI 不录制或保存原始音频。")
}

@Composable
private fun SecurityPage(providers: Int, enabled: Int, onOpenProviders: () -> Unit, onCopyDiagnostics: () -> Unit) {
    InfoCard("API Key", "使用 Android Keystore AES-GCM 加密保存。界面默认脱敏；不要把密钥放入 Base URL、诊断信息或反馈。")
    InfoCard("数据传输", "聊天文本及所选附件会发送到当前服务商；不同服务商有各自的数据政策。应用不会声称 AI 请求完全在本地处理。")
    InfoCard("本地数据", "会话和用量文件存于 Android 应用私有目录。删除服务商会移除该配置和加密密钥，但不会删除已保存的聊天历史。")
    InfoCard("当前 Provider", "$providers 个已配置，$enabled 个启用。仅 OpenAI-compatible 协议已实现。")
    ActionButton("管理密钥与服务商", onClick = onOpenProviders)
    ActionButton("复制脱敏诊断", onClick = onCopyDiagnostics)
}

@Composable
private fun StoragePage(sessions: Int, historyBytes: Long, cacheBytes: Long, migrationStatus: String, message: String?, onClearHistory: () -> Unit, onClearCache: () -> Unit) {
    InfoCard("本地会话", "$sessions 个会话 · ${formatBytes(historyBytes)}。清除会话与清理缓存是两个独立操作。")
    InfoCard("临时缓存", formatBytes(cacheBytes) + "；只清理 cache 目录，不影响附件、会话、API Key 或已校验更新包。")
    InfoCard("配置迁移", migrationStatus)
    InfoCard("数据导出", "当前支持从聊天页导出会话文本；不提供完整应用数据库导出。")
    message?.let { InfoCard("操作结果", it) }
    ActionButton("清理临时缓存", onClick = onClearCache)
    ActionButton("删除所有聊天历史", danger = true, onClick = onClearHistory)
}

@Composable
private fun DiagnosticsPage(version: String, code: Int, build: String, providers: Int, enabled: Int, update: String, message: String?, latestErrorType: String?, onCopy: () -> Unit, onGitHub: () -> Unit, onOpenUpdates: () -> Unit) {
    InfoCard("应用构建", "$version · versionCode $code · $build")
    InfoCard("服务商状态", "$providers 个已配置，$enabled 个启用；API Key、Base URL 和请求正文不会写入诊断摘要。")
    InfoCard("更新状态", update + (message?.let { " · $it" } ?: ""))
    InfoCard("最近错误类别", latestErrorType ?: "无已记录错误")
    InfoCard("连接测试", "在“AI 服务商”中选中配置后可测试；失败只展示脱敏错误类别与说明。")
    ActionButton("复制脱敏诊断信息", onClick = onCopy)
    ActionButton("检查更新", onClick = onOpenUpdates)
    ActionButton("打开 GitHub 仓库", onClick = onGitHub)
    InfoCard("开源许可", "仓库当前未包含可展示的许可证文件。")
}

@Composable
private fun PreferenceSwitch(title: String, detail: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = FuturePalette.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = FuturePalette.Muted, fontSize = 10.sp, lineHeight = 15.sp)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> SettingChoice(title: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = FuturePalette.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        options.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSelect(value) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected == value, onCheckedChange = { if (it) onSelect(value) })
                Text(label, color = if (selected == value) FuturePalette.Cyan else FuturePalette.Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(17.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = FuturePalette.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = FuturePalette.Muted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun ActionButton(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
        Text(label, color = if (danger) Color(0xFFFFDDE3) else Color.White)
    }
}

private fun CapabilityEvidence.label() = when (this) {
    CapabilityEvidence.VERIFIED -> "已验证"
    CapabilityEvidence.PROVIDER_DECLARED -> "服务商声明"
    CapabilityEvidence.UNKNOWN -> "未知"
}

private fun glyphFor(section: SettingsSection): String = when (section) {
    SettingsSection.GENERAL -> "settings"
    SettingsSection.APPEARANCE -> "theme"
    SettingsSection.CHAT -> "chat"
    SettingsSection.PROVIDERS -> "cloud"
    SettingsSection.MODEL_ROUTING -> "route"
    SettingsSection.TASKS -> "taskbox"
    SettingsSection.USAGE -> "chart"
    SettingsSection.ATTACHMENTS -> "attach"
    SettingsSection.VOICE -> "mic"
    SettingsSection.UPDATES -> "download"
    SettingsSection.SECURITY -> "shield"
    SettingsSection.STORAGE -> "database"
    SettingsSection.DIAGNOSTICS -> "pulse"
    SettingsSection.HEALTH -> "pulse"
    SettingsSection.ARENA -> "star"
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
