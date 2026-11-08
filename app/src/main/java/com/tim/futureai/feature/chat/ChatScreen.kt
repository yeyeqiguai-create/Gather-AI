package com.tim.futureai.feature.chat

import com.tim.futureai.ui.components.GatherCheckBox
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.snap
import androidx.compose.runtime.key
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.shadow
import com.tim.futureai.ui.liquid.LocalGlassPrefs
import com.tim.futureai.ui.liquid.GlassMaterial
import com.tim.futureai.ui.liquid.glassSurface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import com.tim.futureai.ui.components.GatherButton as Button
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import com.tim.futureai.ui.theme.ContinuousCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.tim.futureai.ui.components.GatherCircularProgressIndicator as CircularProgressIndicator
import com.tim.futureai.ui.components.GatherAlertDialog as AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.tim.futureai.ui.components.GatherTextButton as TextButton
import com.tim.futureai.ui.components.GatherDropdownMenu as DropdownMenu
import com.tim.futureai.ui.components.GatherDropdownMenuItem as DropdownMenuItem
import com.tim.futureai.ui.components.GatherOutlinedTextField as OutlinedTextField
import com.tim.futureai.ui.components.GatherModalNavigationDrawer as ModalNavigationDrawer
import com.tim.futureai.ui.components.GatherModalDrawerSheet as ModalDrawerSheet
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import com.tim.futureai.ui.components.GatherIconButton as IconButton
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.SolidColor
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.futureai.domain.model.MessageRole
import com.tim.futureai.domain.model.MessageStatus
import com.tim.futureai.domain.model.ChatSession
import com.tim.futureai.ui.liquid.LiquidStage
import com.tim.futureai.ui.liquid.LocalLiquidSpec
import com.tim.futureai.ui.liquid.softSurface
import com.tim.futureai.ui.theme.FutureBackdrop
import com.tim.futureai.ui.theme.FutureGlyph
import com.tim.futureai.ui.theme.FuturePalette
import com.tim.futureai.ui.theme.glassPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.tim.futureai.domain.model.ChatAttachment
import com.tim.futureai.domain.chat.BuiltInPromptLibrary
import com.tim.futureai.domain.chat.PromptTemplate
import com.tim.futureai.domain.chat.SystemInstructionTemplate
import com.tim.futureai.feature.instructions.InstructionTemplateLibraryDialog
import java.util.UUID

/** Bubble width derives from the real window width; never exceeds 330dp. */
internal fun chatBubbleMaxWidthDp(screenWidthDp: Float): Float =
    (screenWidthDp * 0.86f).coerceAtMost(330f)

@Composable
fun ChatScreen(
    state: ChatUiState,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onDismissNotice: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onTaskCenterClick: () -> Unit = {},
    onHealthClick: () -> Unit = {},
    onArenaClick: () -> Unit = {},
    onLinkClick: () -> Unit = {},
    onCancel: () -> Unit = {},
    onExamplePrompt: (String) -> Unit = onInputChanged,
    onRegenerate: (Int) -> Unit = {},
    onRetry: (Int) -> Unit = {},
    onNewSession: () -> Unit = {},
    onSelectSession: (String) -> Unit = {},
    onSearchSessionContent: (String) -> Map<String, String> = { emptyMap() },
    onRenameSession: (String, String) -> Boolean = { _, _ -> false },
    onToggleSessionPinned: (String) -> Boolean = { false },
    onArchiveSession: (String, Boolean) -> Boolean = { _, _ -> false },
    onDeleteSessions: (Set<String>) -> Int = { 0 },
    onArchiveSessions: (Set<String>, Boolean) -> Int = { _, _ -> 0 },
    onShareSessions: (Set<String>) -> Unit = {},
    onExportSessions: (Set<String>) -> Unit = {},
    onDeleteSession: (String) -> Boolean = { false },
    onModelSelected: (String?) -> Unit = {},
    onUpdateSessionInstruction: (String) -> Unit = {},
    instructionTemplates: List<SystemInstructionTemplate> = emptyList(),
    onSaveInstructionTemplate: (SystemInstructionTemplate) -> Boolean = { false },
    onDeleteInstructionTemplate: (String) -> Boolean = { false },
    onImportInstructionTemplates: (String) -> String = { "导入不可用" },
    onExportInstructionTemplates: (String) -> Unit = {},
    onNextMessageModelSelected: (String) -> Unit = {},
    onPickAttachments: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    onListen: () -> Unit = {},
    onSpeak: (String, String) -> Unit = { _, _ -> },
    speakingMessageKey: String? = null,
    onEditMessage: (Int) -> Unit = {},
    onShareMessage: (String) -> Unit = {},
    onExportSession: () -> Unit = {},
    isAiConfigured: Boolean = true,
    showModelNames: Boolean = true,
    showRoutingReason: Boolean = true,
    compactSpacing: Boolean = false,
    customPrompts: List<PromptTemplate> = emptyList(),
    onSaveCustomPrompt: (PromptTemplate) -> Boolean = { false },
    onDeleteCustomPrompt: (String) -> Boolean = { false },
    onImportCustomPrompts: () -> Unit = {},
    onExportCustomPrompts: () -> Unit = {},
    onToggleBookmark: (Int) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    var showSessionManager by remember { mutableStateOf(false) }
    var showPromptLibrary by remember { mutableStateOf(false) }
    var showSavedMessages by remember { mutableStateOf(false) }
    var showSessionInstruction by remember { mutableStateOf(false) }
    var showInstructionLibrary by remember { mutableStateOf(false) }
    var editedSessionInstruction by remember { mutableStateOf("") }
    var jumpToMessage by remember { mutableStateOf<Int?>(null) }
    val insertPrompt: (String) -> Unit = { prompt ->
        val current = state.inputText
        onInputChanged(if (current.isBlank()) prompt else "$current\n\n$prompt")
    }
    val retryNoticeIndex = state.messages.indexOfLast { it.role == MessageRole.USER }.takeIf { index ->
        index >= 0 && state.messages[index].status in setOf(MessageStatus.FAILED, MessageStatus.CANCELLED)
    }
    val spec = LocalLiquidSpec.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val shellKeyboard = LocalSoftwareKeyboardController.current
    val shellFocus = androidx.compose.ui.platform.LocalFocusManager.current
    val shellScope = rememberCoroutineScope()
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            shellKeyboard?.hide()
            shellFocus.clearFocus()
        }
    }
    BackHandler(enabled = drawerState.isOpen) { shellScope.launch { drawerState.close() } }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = spec.ambientMiddle, modifier = Modifier.width(320.dp)
                .then(if (drawerState.isClosed) Modifier.clearAndSetSemantics { } else Modifier)) {
                ConversationDrawer(
                    sessions = state.sessions, draftPreviews = state.draftPreviews,
                    activeSessionId = state.activeSessionId,
                    onNew = { onNewSession(); shellScope.launch { drawerState.close() } },
                    onSelect = { onSelectSession(it); shellScope.launch { drawerState.close() } },
                    onManage = { showSessionManager = true; shellScope.launch { drawerState.close() } },
                    onLinkClick = { shellScope.launch { drawerState.close() }; onLinkClick() },
                    onTaskClick = { shellScope.launch { drawerState.close() }; onTaskCenterClick() },
                    onSettingsClick = { shellScope.launch { drawerState.close() }; onSettingsClick() },
                    onSearchContent = onSearchSessionContent,
                    onRename = onRenameSession,
                    onTogglePinned = onToggleSessionPinned,
                    onArchive = { onArchiveSession(it, true) },
                    mutationsBlocked = state.isLoading || state.isHistoryLoading,
                    onClose = { shellScope.launch { drawerState.close() } },
                )
            }
        },
    ) {
        val density = LocalDensity.current
        val reduced = LocalGlassPrefs.current.reduceMotion
        var headerHeight by remember { mutableStateOf(88.dp) }
        var footerHeight by remember { mutableStateOf(148.dp) }
        LiquidStage(modifier = modifier.fillMaxSize().imePadding(), source = {
            key(state.activeSessionId) {
                if (state.messages.isEmpty()) {
                    WelcomeContent(isAiConfigured, onSettingsClick, insertPrompt,
                        Modifier.fillMaxSize().padding(top = headerHeight, bottom = footerHeight))
                } else Conversation(
                    messages = state.messages, onRegenerate = onRegenerate, onRetry = onRetry,
                    speakingMessageKey = speakingMessageKey, onSpeak = onSpeak, onEditMessage = onEditMessage,
                    onShareMessage = onShareMessage, compactSpacing = compactSpacing, onToggleBookmark = onToggleBookmark,
                    jumpToMessage = jumpToMessage, onJumpHandled = { jumpToMessage = null },
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = headerHeight + 16.dp, bottom = footerHeight + 16.dp),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }, overlay = {
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                .glassSurface(GlassMaterial.FROSTED, RectangleShape).statusBarsPadding()) {
            BrandBar(
                sessionTitle = state.sessionTitle, modelChoices = state.modelChoices,
                selectedModelId = state.selectedModelId, onModelSelected = onModelSelected,
                onNextMessageModelSelected = onNextMessageModelSelected, onExportSession = onExportSession,
                onSessionInstructionClick = {
                    editedSessionInstruction = state.sessions.firstOrNull { it.id == state.activeSessionId }?.systemInstruction.orEmpty()
                    showSessionInstruction = true
                },
                onSessionsClick = { shellKeyboard?.hide(); shellFocus.clearFocus(); shellScope.launch { drawerState.open() } },
                onSettingsClick = onSettingsClick, onNewSession = onNewSession,
                activeModelExplanation = state.activeModelExplanation, showModelNames = showModelNames,
                showRoutingReason = showRoutingReason, onTaskCenterClick = onTaskCenterClick,
                onHealthClick = onHealthClick, onArenaClick = onArenaClick, onLinkClick = onLinkClick,
                onSavedMessagesClick = { showSavedMessages = true }, savedMessagesCount = state.messages.count { it.isBookmarked },
            )
            }
            if (state.isHistoryLoading) CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), color = spec.text, strokeWidth = 2.dp)
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { footerHeight = with(density) { it.height.toDp() } }
                .background(Brush.verticalGradient(listOf(Color.Transparent, spec.ambientMiddle.copy(alpha = 0.96f))))) {
                AnimatedVisibility(state.isLoading,
                    enter = if (reduced) androidx.compose.animation.EnterTransition.None else fadeIn(tween(160)) + expandVertically(tween(220)),
                    exit = if (reduced) androidx.compose.animation.ExitTransition.None else fadeOut(tween(120)) + shrinkVertically(tween(180))) {
                    RequestStatusCard(state.requestStatus, Modifier.padding(horizontal = 18.dp))
                }
                state.notice?.let { NoticeCard(it, onDismissNotice, retryNoticeIndex?.let { index -> { onRetry(index) } }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            Composer(
                text = state.inputText, isLoading = state.isLoading, isHistoryLoading = state.isHistoryLoading,
                canSend = state.canSend, onTextChanged = onInputChanged, onSend = onSend, onCancel = onCancel,
                attachments = state.pendingAttachments, onPickAttachments = onPickAttachments,
                onRemoveAttachment = onRemoveAttachment, onListen = onListen,
                onOpenPromptLibrary = { showPromptLibrary = true },
            )            }
        })
    }

    if (showPromptLibrary) PromptLibraryDialog(
        customPrompts = customPrompts,
        onInsert = { insertPrompt(it); showPromptLibrary = false },
        onSave = onSaveCustomPrompt,
        onDelete = onDeleteCustomPrompt,
        onImport = onImportCustomPrompts,
        onExport = onExportCustomPrompts,
        onDismiss = { showPromptLibrary = false },
    )
    if (showSessionInstruction) SessionInstructionDialog(
        value = editedSessionInstruction,
        onValueChange = { editedSessionInstruction = it.take(10_000) },
        onDismiss = { showSessionInstruction = false },
        onSave = { onUpdateSessionInstruction(editedSessionInstruction); showSessionInstruction = false },
        onOpenLibrary = { showInstructionLibrary = true },
    )
    if (showInstructionLibrary) InstructionTemplateLibraryDialog(
        templates = instructionTemplates,
        initialInstruction = editedSessionInstruction,
        onApply = { prompt, append ->
            editedSessionInstruction = if (!append || editedSessionInstruction.isBlank()) prompt else "$editedSessionInstruction\n\n$prompt".take(10_000)
        },
        onSave = onSaveInstructionTemplate,
        onDelete = onDeleteInstructionTemplate,
        onImport = onImportInstructionTemplates,
        onExport = onExportInstructionTemplates,
        onDismiss = { showInstructionLibrary = false },
    )
    if (showSavedMessages) SavedMessagesDialog(
        messages = state.messages.mapIndexedNotNull { index, message -> if (message.isBookmarked) index to message else null },
        onJump = { index -> showSavedMessages = false; jumpToMessage = index },
        onRemove = { index -> onToggleBookmark(index) },
        onShare = { saved ->
            val text = saved.joinToString("\n\n---\n\n") { (index, message) -> "${index + 1}. ${message.role.name}\n\n${message.text}" }
            onShareMessage(text)
        },
        onDismiss = { showSavedMessages = false },
    )
    if (showSessionManager) SessionManagerDialog(
        sessions = state.sessions,
        draftPreviews = state.draftPreviews,
        searchSessionContent = onSearchSessionContent,
        activeSessionId = state.activeSessionId,
        onDismiss = { showSessionManager = false },
        onNew = { onNewSession(); showSessionManager = false },
        onSelect = { onSelectSession(it); showSessionManager = false },
        onRename = onRenameSession,
        onTogglePinned = onToggleSessionPinned,
        onArchive = onArchiveSession,
        onDeleteMany = onDeleteSessions,
        onArchiveMany = onArchiveSessions,
        onShareMany = onShareSessions,
        onExportMany = onExportSessions,
        onDelete = onDeleteSession,
    )
}

@Composable
private fun BrandBar(
    sessionTitle: String, modelChoices: List<ChatModelChoice>, selectedModelId: String?,
    onModelSelected: (String?) -> Unit, onNextMessageModelSelected: (String) -> Unit,
    onExportSession: () -> Unit, onSessionsClick: () -> Unit, onSessionInstructionClick: () -> Unit,
    onSettingsClick: () -> Unit, onNewSession: () -> Unit,
    activeModelExplanation: String? = null, showModelNames: Boolean = true, showRoutingReason: Boolean = true,
    onTaskCenterClick: () -> Unit = {}, onHealthClick: () -> Unit = {}, onArenaClick: () -> Unit = {},
    onLinkClick: () -> Unit = {}, onSavedMessagesClick: () -> Unit = {}, savedMessagesCount: Int = 0,
) {
    var modelsOpen by remember { mutableStateOf(false) }
    var actionsOpen by remember { mutableStateOf(false) }
    val spec = LocalLiquidSpec.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSessionsClick, modifier = Modifier.semantics { contentDescription = "打开会话列表" }) { FutureGlyph("menu", size = 23.dp) }
            Box(Modifier.weight(1f)) {
                Column(Modifier.clip(ContinuousCornerShape(10.dp)).clickable { modelsOpen = true }.padding(horizontal = 8.dp, vertical = 7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Gather AI", color = spec.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
                        Text("⌄", color = spec.muted, fontSize = 19.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    if (showModelNames) Text(modelChoices.firstOrNull { it.id == selectedModelId }?.displayName ?: "选择模型", color = spec.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = modelsOpen, onDismissRequest = { modelsOpen = false }, containerColor = spec.surfaceTint) {
                    if (modelChoices.isEmpty()) DropdownMenuItem(text = { Text("添加 AI 服务商") }, onClick = { modelsOpen = false; onSettingsClick() })
                    modelChoices.forEach { choice ->
                        DropdownMenuItem(text = { Text((if (selectedModelId == choice.id) "✓  " else "") + choice.displayName) }, onClick = { onModelSelected(choice.id); modelsOpen = false })
                        if (!choice.isAutomatic) DropdownMenuItem(text = { Text("仅下一条 · ${choice.displayName}", fontSize = 12.sp) }, onClick = { onNextMessageModelSelected(choice.id); modelsOpen = false })
                    }
                }
            }
            IconButton(onClick = onNewSession, modifier = Modifier.semantics { contentDescription = "新建对话" }) { FutureGlyph("edit", size = 22.dp) }
            Box {
                IconButton(onClick = { actionsOpen = true }, modifier = Modifier.semantics { contentDescription = "更多功能" }) { Text("···", color = spec.text, fontSize = 23.sp) }
                DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }, containerColor = spec.surfaceTint) {
                    listOf("本会话指令" to onSessionInstructionClick, "导出会话" to onExportSession,
                        "收藏消息 · $savedMessagesCount" to onSavedMessagesClick, "设备互联" to onLinkClick,
                        "任务中心" to onTaskCenterClick, "模型对比" to onArenaClick,
                        "健康与诊断" to onHealthClick, "设置" to onSettingsClick).forEach { (title, action) ->
                        DropdownMenuItem(text = { Text(title) }, onClick = { actionsOpen = false; action() })
                    }
                }
            }
        }
        if (showRoutingReason && !activeModelExplanation.isNullOrBlank()) Text(activeModelExplanation, color = spec.muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
    }
}

@Composable
private fun SessionManagerDialog(
    sessions: List<ChatSession>,
    draftPreviews: Map<String, String>,
    searchSessionContent: (String) -> Map<String, String>,
    activeSessionId: String,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onSelect: (String) -> Unit,
    onRename: (String, String) -> Boolean,
    onTogglePinned: (String) -> Boolean,
    onArchive: (String, Boolean) -> Boolean,
    onDeleteMany: (Set<String>) -> Int,
    onArchiveMany: (Set<String>, Boolean) -> Int,
    onShareMany: (Set<String>) -> Unit,
    onExportMany: (Set<String>) -> Unit,
    onDelete: (String) -> Boolean,
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ChatSession?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(SessionSortOrder.RECENT_ACTIVITY) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var batchMode by remember { mutableStateOf(false) }
    val selected = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    val spec = LocalLiquidSpec.current
    val contentMatches = searchSessionContent(searchQuery)
    val visibleSessions = sortAndFilterSessions(sessions.filter { it.isArchived == showArchived }, searchQuery, sortOrder, contentMatches.keys)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).widthIn(max = 440.dp).glassPanel(ContinuousCornerShape(28.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("你的对话", color = FuturePalette.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("会话彼此独立保存在本机", color = FuturePalette.Muted, fontSize = 11.sp)
                }
                TextButton(onClick = onNew) { FutureGlyph("plus", tint = FuturePalette.VioletBright, size = 18.dp); Spacer(Modifier.width(5.dp)); Text("新建", color = FuturePalette.VioletBright) }
            }
            Row(
                Modifier.fillMaxWidth().clip(ContinuousCornerShape(15.dp)).background(spec.surfaceTint.copy(alpha = 0.55f))
                    .border(1.dp, spec.surfaceEdge, ContinuousCornerShape(15.dp)).padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FutureGlyph("search", tint = spec.muted, size = 17.dp)
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it.take(100) },
                    modifier = Modifier.weight(1f).padding(horizontal = 9.dp, vertical = 13.dp)
                        .semantics { contentDescription = "搜索会话" },
                    textStyle = TextStyle(color = FuturePalette.Text, fontSize = 13.sp),
                    singleLine = true,
                    decorationBox = { inner -> Box { if (searchQuery.isEmpty()) Text("搜索会话、消息或草稿", color = FuturePalette.Muted, fontSize = 13.sp); inner() } },
                )
                if (searchQuery.isNotEmpty()) {
                    TextButton(onClick = { searchQuery = "" }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("清空", color = FuturePalette.VioletBright, fontSize = 11.sp)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { showArchived = false; batchMode = false; selected.clear() }) { Text("会话", color = if (!showArchived) FuturePalette.Cyan else FuturePalette.Muted, fontSize = 11.sp) }
                TextButton(onClick = { showArchived = true; batchMode = false; selected.clear() }) { Text("归档", color = if (showArchived) FuturePalette.Cyan else FuturePalette.Muted, fontSize = 11.sp) }
                TextButton(onClick = { batchMode = !batchMode; selected.clear() }) { Text(if (batchMode) "退出批量" else "批量选择", color = FuturePalette.VioletBright, fontSize = 11.sp) }
            }
            if (batchMode) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    val selectableVisibleIds = visibleSessions.asSequence().map { it.id }.filter { it != activeSessionId }.toSet()
                    val allVisibleSelected = selectableVisibleIds.isNotEmpty() && selected.containsAll(selectableVisibleIds)
                    TextButton(
                        enabled = selectableVisibleIds.isNotEmpty(),
                        onClick = {
                            val updatedSelection = toggleVisibleSessionSelection(selected.toSet(), visibleSessions, activeSessionId)
                            selected.clear()
                            selected.addAll(updatedSelection)
                        },
                    ) { Text(if (allVisibleSelected) "取消全选" else "全选可见", color = FuturePalette.Cyan, fontSize = 11.sp) }
                    Text("已选 " + selected.size, color = FuturePalette.Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { if (selected.isNotEmpty()) { onArchiveMany(selected.toSet(), !showArchived); selected.clear() } }) { Text(if (showArchived) "恢复所选" else "归档所选", color = FuturePalette.Cyan, fontSize = 11.sp) }
                        TextButton(onClick = { if (selected.isNotEmpty()) onShareMany(selected.toSet()) }) { Text("分享", color = FuturePalette.Cyan, fontSize = 11.sp) }
                        TextButton(onClick = { if (selected.isNotEmpty()) onExportMany(selected.toSet()) }) { Text("导出", color = FuturePalette.Cyan, fontSize = 11.sp) }
                        TextButton(onClick = { if (selected.isNotEmpty()) confirmBatchDelete = true }) { Text("删除所选", color = FuturePalette.Danger, fontSize = 11.sp) }
                    }
                }
            }
            Box {
                TextButton(onClick = { showSortMenu = true }) {
                    Text("排序：${sortOrder.label}", color = FuturePalette.VioletBright, fontSize = 11.sp)
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    SessionSortOrder.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { sortOrder = option; showSortMenu = false },
                        )
                    }
                }
            }
            Column(Modifier.height(340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (visibleSessions.isEmpty()) {
                    Text(
                        if (sessions.isEmpty()) "还没有会话，点击右上角新建" else "没有找到匹配的会话",
                        color = FuturePalette.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    )
                }
                visibleSessions.forEach { session ->
                    Row(
                        Modifier.fillMaxWidth().clip(ContinuousCornerShape(16.dp))
                            .background(if (session.id == activeSessionId) spec.accent.copy(alpha = 0.20f) else spec.surfaceTint.copy(alpha = 0.55f))
                            .border(1.dp, if (session.id == activeSessionId) spec.accentBright.copy(alpha = 0.5f) else spec.surfaceEdge, ContinuousCornerShape(16.dp))
                            .clickable(enabled = editingId != session.id && (!batchMode || session.id != activeSessionId)) {
                                if (batchMode) {
                                    val updatedSelection = toggleSessionSelection(selected.toSet(), session.id, activeSessionId)
                                    selected.clear()
                                    selected.addAll(updatedSelection)
                                } else onSelect(session.id)
                            }
                            .padding(start = 12.dp, end = 5.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (editingId == session.id) {
                            BasicTextField(
                                value = editText, onValueChange = { editText = it.take(60) },
                                modifier = Modifier.weight(1f).padding(vertical = 9.dp),
                                textStyle = TextStyle(color = FuturePalette.Text, fontSize = 13.sp), singleLine = true,
                                decorationBox = { inner -> Box { if (editText.isEmpty()) Text("会话名称", color = FuturePalette.Muted); inner() } },
                            )
                            TextButton(onClick = { if (onRename(session.id, editText)) editingId = null }, enabled = editText.isNotBlank()) { Text("保存", color = FuturePalette.Cyan, fontSize = 11.sp) }
                            TextButton(onClick = { editingId = null }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("取消", color = FuturePalette.Muted, fontSize = 11.sp) }
                        } else {
                            if (batchMode) {
                                GatherCheckBox(
                                    checked = session.id in selected,
                                    enabled = session.id != activeSessionId,
                                    onCheckedChange = {
                                        val updatedSelection = toggleSessionSelection(selected.toSet(), session.id, activeSessionId)
                                        selected.clear()
                                        selected.addAll(updatedSelection)
                                    },
                                )
                            }
                            Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(session.title, color = FuturePalette.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (session.isPinned) Text(" 置顶", color = FuturePalette.Cyan, fontSize = 10.sp)
                                }
                                Text(if (session.id == activeSessionId) "当前会话" else session.updatedAt.toString().take(10), color = FuturePalette.Muted, fontSize = 10.sp)
                                draftPreviews[session.id]?.let { DraftPreview(it) }
                                contentMatches[session.id]?.let { match ->
                                    Text(match, color = spec.cyan, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (!batchMode) {
                                IconButton(onClick = { onTogglePinned(session.id) }) {
                                    FutureGlyph(if (session.isPinned) "pin_filled" else "pin", tint = if (session.isPinned) FuturePalette.Cyan else FuturePalette.Muted, size = 17.dp)
                                }
                                IconButton(onClick = { editingId = session.id; editText = session.title }) { FutureGlyph("edit", tint = FuturePalette.Muted, size = 17.dp) }
                                TextButton(onClick = { onArchive(session.id, !session.isArchived) }) { Text(if (session.isArchived) "恢复" else "归档", color = FuturePalette.Cyan, fontSize = 10.sp) }
                                IconButton(onClick = { deleteTarget = session }) { FutureGlyph("delete", tint = FuturePalette.Danger, size = 17.dp) }
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("完成", color = FuturePalette.VioletBright) }
        }
    }
    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("删除所选会话？") },
            text = { Text("将删除 " + selected.size + " 个会话及其消息，此操作不可撤销。") },
            confirmButton = { TextButton(onClick = { onDeleteMany(selected.toSet()); selected.clear(); confirmBatchDelete = false }) { Text("删除", color = FuturePalette.Danger) } },
            dismissButton = { TextButton(onClick = { confirmBatchDelete = false }) { Text("取消") } },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这个会话？") },
            text = { Text("“${target.title}”及其中的消息会从本机删除，此操作无法撤销。") },
            confirmButton = { TextButton(onClick = { onDelete(target.id); deleteTarget = null }) { Text("删除", color = FuturePalette.Danger) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun WelcomeContent(configured: Boolean, onSettingsClick: () -> Unit, onExamplePrompt: (String) -> Unit, modifier: Modifier = Modifier) {
    val spec = LocalLiquidSpec.current
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        FutureGlyph("spark", tint = spec.text, size = 36.dp)
        Spacer(Modifier.height(24.dp))
        Text("有什么可以帮你？", color = spec.text, fontSize = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp)
        Spacer(Modifier.height(12.dp))
        Text("把想法变成下一步。", color = spec.muted, fontSize = 14.sp)
        Spacer(Modifier.height(32.dp))
        listOf("写点什么" to "write-structure", "学习新知" to "learn-simple", "帮我编程" to "code-review", "理清思路" to "work-plan").chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, id) ->
                    Text(label, color = spec.text, fontSize = 13.sp, modifier = Modifier.weight(1f).clip(ContinuousCornerShape(16.dp))
                        .border(1.dp, spec.surfaceEdge, ContinuousCornerShape(16.dp)).clickable { BuiltInPromptLibrary.prompts.firstOrNull { it.id == id }?.let { onExamplePrompt(it.prompt) } }.padding(horizontal = 14.dp, vertical = 16.dp))
                }
            }
        }
        if (!configured) TextButton(onClick = onSettingsClick, modifier = Modifier.padding(top = 16.dp)) { Text("连接 AI 服务，开始对话  →", color = spec.muted, fontSize = 12.sp) }
    }
}

@Composable
private fun HeroCard(configured: Boolean) {
    val spec = LocalLiquidSpec.current
    Box(
        modifier = Modifier.fillMaxWidth().glassPanel(ContinuousCornerShape(30.dp)).padding(horizontal = 22.dp, vertical = 23.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.78f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.clip(CircleShape).background(spec.cyan.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(spec.cyan))
                Spacer(Modifier.width(7.dp))
                Text(if (configured) "AI 助手已连接" else "开启你的 AI 空间", color = spec.cyan, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
            Text("让想法\n自然向前。", color = FuturePalette.Text, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp)
            Text("提问、写作、规划，或只是把脑海里的念头说出来。", color = FuturePalette.Muted, fontSize = 13.sp, lineHeight = 20.sp)
        }
        Box(
            modifier = Modifier.align(Alignment.CenterEnd).size(74.dp).clip(CircleShape)
                .background(spec.surfaceTint)
                .border(1.dp, spec.surfaceEdge, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FutureGlyph("spark", tint = spec.accentBright, size = 24.dp)
        }
    }
}

@Composable
private fun SetupStatusCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spec = LocalLiquidSpec.current
    Row(
        modifier = modifier.fillMaxWidth().glassPanel(ContinuousCornerShape(22.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(spec.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            FutureGlyph("cloud", tint = FuturePalette.VioletBright, size = 20.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("连接你的 AI 服务", color = FuturePalette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("添加兼容接口配置后即可开始对话", color = FuturePalette.Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Text("配置", color = FuturePalette.VioletBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PromptCard(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().glassPanel(ContinuousCornerShape(19.dp)).clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FutureGlyph("spark", tint = FuturePalette.Cyan, size = 17.dp)
        Spacer(Modifier.width(11.dp))
        Text(text, color = FuturePalette.Text.copy(alpha = 0.92f), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        Text("›", color = FuturePalette.Muted, fontSize = 22.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun PromptLibraryDialog(
    customPrompts: List<PromptTemplate>,
    onInsert: (String) -> Unit,
    onSave: (PromptTemplate) -> Boolean,
    onDelete: (String) -> Boolean,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("全部") }
    var editing by remember { mutableStateOf<PromptTemplate?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PromptTemplate?>(null) }
    var moreMenu by remember { mutableStateOf(false) }
    val filtered = (BuiltInPromptLibrary.prompts + customPrompts).filter { prompt ->
        (category == "全部" || prompt.category == category) &&
            (query.isBlank() || "${prompt.title} ${prompt.prompt}".contains(query.trim(), ignoreCase = true))
    }
    val spec = LocalLiquidSpec.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f).clip(ContinuousCornerShape(24.dp))
                .background(spec.surfaceTint).border(1.dp, spec.surfaceEdge, ContinuousCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("提示词库", color = FuturePalette.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("选择模板后会追加到当前草稿", color = FuturePalette.Muted, fontSize = 11.sp)
                }
                Box {
                    TextButton(onClick = { moreMenu = true }) { Text("更多", color = FuturePalette.Muted) }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(text = { Text("导入提示词") }, onClick = { moreMenu = false; onImport() })
                        DropdownMenuItem(text = { Text("导出自定义提示词") }, onClick = { moreMenu = false; onExport() }, enabled = customPrompts.isNotEmpty())
                    }
                }
                TextButton(onClick = { creating = true; editing = null }) { Text("＋ 自定义", color = FuturePalette.VioletBright) }
                TextButton(onClick = onDismiss) { Text("关闭", color = FuturePalette.Muted) }
            }
            BasicTextField(
                value = query, onValueChange = { query = it.take(80) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().clip(ContinuousCornerShape(13.dp)).background(spec.ambientMiddle)
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                textStyle = TextStyle(color = FuturePalette.Text, fontSize = 13.sp),
                decorationBox = { inner -> Box {
                    if (query.isEmpty()) Text("搜索模板内容或名称", color = FuturePalette.Muted, fontSize = 13.sp)
                    inner()
                } },
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                (BuiltInPromptLibrary.categories + "自定义").forEach { item ->
                    val selected = category == item
                    Box(
                        Modifier.clip(CircleShape).background(if (selected) spec.accent.copy(alpha = 0.18f) else spec.surfaceTint)
                            .border(1.dp, if (selected) spec.accent.copy(alpha = 0.45f) else spec.surfaceEdge, CircleShape)
                            .clickable { category = item }.padding(horizontal = 12.dp, vertical = 7.dp),
                    ) { Text(item, color = if (selected) spec.accentBright else FuturePalette.Muted, fontSize = 11.sp) }
                }
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("没有匹配的模板", color = FuturePalette.Muted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(filtered, key = { it.id }) { prompt ->
                        PromptLibraryItem(prompt, { onInsert(prompt.prompt) }, { editing = prompt; creating = true }, { deleteTarget = prompt })
                    }
                }
            }
            Text("模板只保存在本机；插入不会自动发送。", color = FuturePalette.Muted, fontSize = 10.sp)
        }
    }

    if (creating) PromptEditorDialog(
        initial = editing,
        onDismiss = { creating = false; editing = null },
        onSave = { title, prompt ->
            val item = PromptTemplate(editing?.id ?: "custom-${UUID.randomUUID()}", title, prompt, "自定义")
            if (onSave(item)) { creating = false; editing = null; true } else false
        },
    )
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null }, title = { Text("删除自定义模板？") },
            text = { Text("“${target.title}”将从本机提示词库移除。") },
            confirmButton = { TextButton(onClick = { if (onDelete(target.id)) deleteTarget = null }) { Text("删除", color = FuturePalette.Danger) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun PromptLibraryItem(prompt: PromptTemplate, onInsert: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(ContinuousCornerShape(16.dp)).softSurface(ContinuousCornerShape(16.dp))
            .clickable(onClick = onInsert).padding(horizontal = 13.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(prompt.title, color = FuturePalette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(prompt.category, color = FuturePalette.Muted, fontSize = 10.sp)
            if (prompt.category == "自定义") {
                TextButton(onClick = onEdit, contentPadding = PaddingValues(start = 8.dp, end = 4.dp)) { Text("编辑", color = FuturePalette.VioletBright, fontSize = 10.sp) }
                TextButton(onClick = onDelete, contentPadding = PaddingValues(start = 4.dp, end = 0.dp)) { Text("删除", color = FuturePalette.Danger, fontSize = 10.sp) }
            }
        }
        Text(prompt.prompt, color = FuturePalette.Muted, fontSize = 10.sp, lineHeight = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PromptEditorDialog(initial: PromptTemplate?, onDismiss: () -> Unit, onSave: (String, String) -> Boolean) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var prompt by remember(initial?.id) { mutableStateOf(initial?.prompt.orEmpty()) }
    var saveFailed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建提示词" else "编辑提示词") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("名称", color = FuturePalette.Muted, fontSize = 11.sp)
                BasicTextField(
                    title, { title = it.take(48); saveFailed = false }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().clip(ContinuousCornerShape(10.dp)).background(FuturePalette.Panel).padding(11.dp),
                    textStyle = TextStyle(color = FuturePalette.Text, fontSize = 13.sp),
                )
                Text("提示内容", color = FuturePalette.Muted, fontSize = 11.sp)
                BasicTextField(
                    prompt, { prompt = it.take(4_000); saveFailed = false },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 220.dp).clip(ContinuousCornerShape(10.dp))
                        .background(FuturePalette.Panel).padding(11.dp),
                    textStyle = TextStyle(color = FuturePalette.Text, fontSize = 12.sp, lineHeight = 18.sp),
                    decorationBox = { inner -> Box {
                        if (prompt.isEmpty()) Text("描述希望 AI 如何协助你…", color = FuturePalette.Muted, fontSize = 12.sp)
                        inner()
                    } },
                )
                if (saveFailed) Text("保存失败，请重试。", color = FuturePalette.Danger, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = {
            val safeTitle = title.trim()
            val safePrompt = prompt.trim()
            if (safeTitle.isBlank() || safePrompt.isBlank()) saveFailed = true else saveFailed = !onSave(safeTitle, safePrompt)
        }, enabled = title.isNotBlank() && prompt.isNotBlank()) { Text("保存", color = FuturePalette.VioletBright) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = FuturePalette.Muted) } },
    )
}

@Composable
private fun SessionInstructionDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val templates = listOf(
        "简洁" to "请先给结论，再用简洁清晰的中文说明。遇到不确定的信息请明确指出。",
        "学习" to "请循序渐进地讲解概念，先给直观例子，再解释原理。指出常见误区，并在结尾给一个简短练习。",
        "编程" to "请先分析需求和约束，再提供可维护的代码方案。说明关键取舍；不确定的 API 或行为不要编造。",
        "会议" to "请把讨论整理为目标、关键结论、待办事项、负责人和期限；没有明确的信息标注为未指定。",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本会话指令") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("只影响当前对话，并会随会话保存在本机；最多 10,000 字符。", color = FuturePalette.Muted, fontSize = 11.sp)
                templates.forEach { (title, prompt) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, color = FuturePalette.Text, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onValueChange(prompt) }) { Text("覆盖", color = FuturePalette.Cyan, fontSize = 10.sp) }
                        TextButton(onClick = { onValueChange(listOf(value.trim(), prompt).filter(String::isNotBlank).joinToString("\n\n").take(10_000)) }) {
                            Text("追加", color = FuturePalette.Cyan, fontSize = 10.sp)
                        }
                    }
                }
                TextButton(onClick = onOpenLibrary) { Text("打开模板库", color = FuturePalette.Cyan) }
                OutlinedTextField(value = value, onValueChange = { onValueChange(it.take(10_000)) }, minLines = 4, maxLines = 8, label = { Text("指令") })
                Text("${value.length} / 10000", color = FuturePalette.Muted, fontSize = 10.sp)
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("保存") } },
        dismissButton = { Row { TextButton(onClick = { onValueChange("") }) { Text("清空") }; TextButton(onClick = onDismiss) { Text("取消") } } },
    )
}

@Composable
private fun SavedMessagesDialog(
    messages: List<Pair<Int, ChatBubble>>,
    onJump: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onShare: (List<Pair<Int, ChatBubble>>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = messages.filter { (_, message) -> query.isBlank() || message.text.contains(query.trim(), ignoreCase = true) }
    val spec = LocalLiquidSpec.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.80f).clip(ContinuousCornerShape(24.dp))
                .background(spec.surfaceTint).border(1.dp, spec.surfaceEdge, ContinuousCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("收藏的消息", color = FuturePalette.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("当前会话 · ${messages.size} 条", color = FuturePalette.Muted, fontSize = 11.sp)
                }
                TextButton(onClick = onDismiss) { Text("关闭", color = FuturePalette.Muted) }
            }
            BasicTextField(
                value = query, onValueChange = { query = it.take(100) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().clip(ContinuousCornerShape(12.dp)).background(spec.ambientMiddle).padding(12.dp),
                textStyle = TextStyle(color = FuturePalette.Text, fontSize = 12.sp),
                decorationBox = { inner -> Box {
                    if (query.isEmpty()) Text("搜索收藏内容", color = FuturePalette.Muted, fontSize = 12.sp)
                    inner()
                } },
            )
            if (filtered.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (messages.isEmpty()) "点聊天消息下方的“收藏”即可保存重点内容" else "没有匹配的收藏", color = FuturePalette.Muted, fontSize = 12.sp)
                }
            } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.first }) { (index, message) ->
                    Column(
                        Modifier.fillMaxWidth().clip(ContinuousCornerShape(15.dp)).softSurface(ContinuousCornerShape(15.dp)).padding(11.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (message.role == MessageRole.USER) "你" else "Gather AI", color = FuturePalette.VioletBright, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onJump(index) }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("定位", fontSize = 10.sp) }
                            TextButton(onClick = { onRemove(index) }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("取消收藏", color = FuturePalette.Muted, fontSize = 10.sp) }
                        }
                        Text(message.text, color = FuturePalette.Text, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (messages.isNotEmpty()) {
                TextButton(onClick = { onShare(filtered) }, enabled = filtered.isNotEmpty(), modifier = Modifier.align(Alignment.End)) {
                    Text("分享筛选结果", color = FuturePalette.VioletBright)
                }
            }
        }
    }
}

@Composable
private fun Conversation(
    messages: List<ChatBubble>, onRegenerate: (Int) -> Unit, onRetry: (Int) -> Unit,
    speakingMessageKey: String?, onSpeak: (String, String) -> Unit,
    onEditMessage: (Int) -> Unit, onShareMessage: (String) -> Unit,
    onToggleBookmark: (Int) -> Boolean, jumpToMessage: Int?, onJumpHandled: () -> Unit,
    compactSpacing: Boolean = false, modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var followLatest by remember { mutableStateOf(true) }
    val retryUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    val viewportHeight by remember { androidx.compose.runtime.derivedStateOf { listState.layoutInfo.viewportSize.height } }
    val reduced = LocalGlassPrefs.current.reduceMotion
    LaunchedEffect(dragged) { if (dragged) followLatest = false }
    LaunchedEffect(retryUserIndex) { followLatest = true }
    LaunchedEffect(listState) {
        snapshotFlow { !listState.isScrollInProgress && !listState.canScrollForward }.collect { atBottom -> if (atBottom) followLatest = true }
    }
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length, contentPadding, viewportHeight) {
        // Text, IME and the animated footer must be measured before locating the
        // end; otherwise a completed reply can remain beneath the composer.
        androidx.compose.runtime.withFrameNanos { }
        if (followLatest && messages.isNotEmpty()) listState.scrollToItem(messages.size)
    }
    LaunchedEffect(jumpToMessage) {
        jumpToMessage?.let { index -> followLatest = false; if (index in messages.indices) listState.animateScrollToItem(index); onJumpHandled() }
    }
    Box(modifier) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(if (compactSpacing) 16.dp else 28.dp)) {
            itemsIndexed(messages) { index, message ->
                val key = "$index:${message.text.hashCode()}"
                Box(Modifier.animateItem(fadeInSpec = if (reduced) snap() else tween(180), placementSpec = null, fadeOutSpec = null)) {
                    MessageBubble(message, index, index == retryUserIndex, onRegenerate, onRetry, key, speakingMessageKey == key, onSpeak, onEditMessage, onShareMessage, onToggleBookmark)
                }
            }
            item(key = "conversation-tail") { Spacer(Modifier.height(1.dp)) }
        }
        if (!followLatest && listState.canScrollForward) IconButton(onClick = { followLatest = true; scope.launch { if (reduced) listState.scrollToItem(messages.size) else listState.animateScrollToItem(messages.size) } }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding()).size(44.dp).clip(CircleShape).background(LocalLiquidSpec.current.surfaceTint).border(1.dp, FuturePalette.Edge, CircleShape).semantics { contentDescription = "回到最新消息" }) { Text("↓", color = FuturePalette.Text, fontSize = 24.sp) }
    }
}

@Composable
private fun MessageBubble(
    message: ChatBubble, index: Int, canRetry: Boolean, onRegenerate: (Int) -> Unit, onRetry: (Int) -> Unit,
    messageKey: String, isSpeaking: Boolean, onSpeak: (String, String) -> Unit,
    onEditMessage: (Int) -> Unit, onShareMessage: (String) -> Unit, onToggleBookmark: (Int) -> Boolean,
) {
    val isUser = message.role == MessageRole.USER
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spec = LocalLiquidSpec.current
    var copied by remember(message.text) { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    val copy: (String) -> Unit = { value ->
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Gather AI", value))
        copied = true; scope.launch { delay(1400); copied = false }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Column(Modifier.then(if (isUser) Modifier.widthIn(max = 340.dp).clip(ContinuousCornerShape(24.dp)).background(spec.surfaceTint).padding(horizontal = 18.dp, vertical = 13.dp) else Modifier.fillMaxWidth()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isUser) {
                message.attachments.forEach { attachment -> Text("附件 · ${attachment.name}", color = spec.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                SelectionContainer { Text(message.text, color = spec.text, fontSize = 16.sp, lineHeight = 25.sp) }
            } else SelectionContainer { MarkdownMessage(message.text, onCopyCode = copy) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { copy(message.text) }, modifier = Modifier.size(38.dp).semantics { contentDescription = "复制消息" }) { FutureGlyph(if (copied) "check" else "copy", tint = spec.muted, size = 16.dp) }
            if (!isUser) {
                IconButton(onClick = { onSpeak(messageKey, message.text) }, modifier = Modifier.size(38.dp).semantics { contentDescription = if (isSpeaking) "停止朗读" else "朗读消息" }) { FutureGlyph(if (isSpeaking) "stop" else "play", tint = spec.muted, size = 16.dp) }
                IconButton(onClick = { onRegenerate(index) }, enabled = message.status != MessageStatus.GENERATING, modifier = Modifier.size(38.dp).semantics { contentDescription = "重新生成" }) { FutureGlyph("retry", tint = spec.muted, size = 16.dp) }
            }
            Box {
                IconButton(onClick = { moreOpen = true }, modifier = Modifier.size(38.dp).semantics { contentDescription = "消息操作" }) { Text("···", color = spec.muted, fontSize = 19.sp) }
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }, containerColor = spec.surfaceTint) {
                    DropdownMenuItem(text = { Text(if (message.isBookmarked) "取消收藏" else "收藏消息") }, onClick = { onToggleBookmark(index); moreOpen = false })
                    DropdownMenuItem(text = { Text("分享") }, onClick = { onShareMessage(message.text); moreOpen = false })
                    if (isUser) DropdownMenuItem(text = { Text("编辑并重新发送") }, onClick = { onEditMessage(index); moreOpen = false })
                }
            }
            if (copied) Text("已复制", color = spec.muted, fontSize = 10.sp)
            if (message.isBookmarked) Text("★", color = spec.muted, fontSize = 11.sp)
            if (isUser && canRetry && message.status in setOf(MessageStatus.FAILED, MessageStatus.CANCELLED)) TextButton(onClick = { onRetry(index) }) { Text("重试") }
        }
        if (message.status == MessageStatus.FAILED) Text("发送失败 · 可重试", color = spec.danger, fontSize = 11.sp)
        if (message.status == MessageStatus.CANCELLED) Text("已停止", color = spec.muted, fontSize = 11.sp)
    }
}

@Composable
private fun MarkdownMessage(text: String, onCopyCode: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        parseChatMarkdown(text).forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> Text(inlineMarkdown(block.text), color = FuturePalette.Text, fontSize = 16.sp, lineHeight = 27.sp)
                is MarkdownBlock.Heading -> Text(
                    inlineMarkdown(block.text), color = FuturePalette.Text,
                    fontSize = when (block.level) { 1 -> 21.sp; 2 -> 19.sp; 3 -> 17.sp; else -> 15.sp },
                    lineHeight = 26.sp, fontWeight = FontWeight.SemiBold,
                )
                is MarkdownBlock.ListItem -> Row(verticalAlignment = Alignment.Top) {
                    Text(if (block.marker.firstOrNull()?.isDigit() == true) "${block.marker} " else "• ", color = FuturePalette.Cyan, fontSize = 14.sp, lineHeight = 23.sp)
                    Text(inlineMarkdown(block.text), color = FuturePalette.Text, fontSize = 14.sp, lineHeight = 23.sp, modifier = Modifier.weight(1f))
                }
                is MarkdownBlock.Quote -> Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(end = 10.dp).width(3.dp).heightIn(min = 34.dp).background(FuturePalette.VioletBright.copy(alpha = 0.8f), ContinuousCornerShape(2.dp)))
                    Text(inlineMarkdown(block.text), color = FuturePalette.Muted, fontSize = 13.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
                }
                is MarkdownBlock.Code -> CodeBlock(block.source, block.language, onCopyCode)
                is MarkdownBlock.Table -> MarkdownTable(block)
                MarkdownBlock.Divider -> Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(FuturePalette.Edge))
            }
        }
    }
}

@Composable
private fun CodeBlock(source: String, language: String, onCopy: (String) -> Unit) {
    val spec = LocalLiquidSpec.current
    Column(
        Modifier.fillMaxWidth().clip(ContinuousCornerShape(14.dp))
            .background(spec.codeBlockBackground).border(1.dp, FuturePalette.Edge, ContinuousCornerShape(14.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().background(spec.codeBlockHeader).padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(language.ifBlank { "代码" }, color = spec.codeText.copy(alpha = 0.72f), fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { onCopy(source) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                FutureGlyph("copy", tint = spec.codeText, size = 14.dp)
                Spacer(Modifier.width(4.dp))
                Text("复制代码", color = spec.codeText, fontSize = 11.sp)
            }
        }
        Box(
            Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()).padding(12.dp),
        ) {
            Text(source, color = spec.codeText, fontSize = 12.sp, lineHeight = 18.sp, softWrap = false, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}

@Composable
private fun MarkdownTable(table: MarkdownBlock.Table) {
    val spec = LocalLiquidSpec.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().clip(ContinuousCornerShape(14.dp)).border(1.dp, spec.surfaceEdge, ContinuousCornerShape(14.dp))) {
      val cellWidth = maxOf(156.dp, maxWidth / table.headers.size)
      Column(Modifier.horizontalScroll(rememberScrollState())) {
        (listOf(table.headers) + table.rows).forEachIndexed { rowIndex, cells ->
            Row(Modifier.background(if (rowIndex == 0) spec.surfaceTint else Color.Transparent)) {
                cells.forEachIndexed { index, value ->
                    Text(inlineMarkdown(value), color = spec.text, fontSize = 14.sp, lineHeight = 22.sp,
                        fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = when (table.alignment[index]) {
                            MarkdownAlignment.START -> androidx.compose.ui.text.style.TextAlign.Start
                            MarkdownAlignment.CENTER -> androidx.compose.ui.text.style.TextAlign.Center
                            MarkdownAlignment.END -> androidx.compose.ui.text.style.TextAlign.End
                        }, modifier = Modifier.width(cellWidth).padding(horizontal = 14.dp, vertical = 12.dp))
                }
            }
            if (rowIndex < table.rows.size) Box(Modifier.width(cellWidth * table.headers.size).height(1.dp).background(spec.surfaceEdge.copy(alpha = 0.6f)))
        }
      }
    }
}

@Composable
private fun inlineMarkdown(source: String): AnnotatedString {
    val spec = LocalLiquidSpec.current
    val result = AnnotatedString.Builder()
    val pattern = Regex("""`([^`\n]+)`|\[([^]\n]+)]\((https?://[^\s)]+)\)|\*\*(.+?)\*\*|__(.+?)__|\*([^*\n]+?)\*|_([^_\n]+?)_""")
    var cursor = 0
    pattern.findAll(source).forEach { match ->
        result.append(source.substring(cursor, match.range.first))
        val g = match.groupValues
        when {
            g[1].isNotEmpty() -> {
                result.pushStyle(SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, background = spec.surfaceTint))
                result.append(g[1]); result.pop()
            }
            g[2].isNotEmpty() -> {
                result.pushLink(androidx.compose.ui.text.LinkAnnotation.Url(g[3], androidx.compose.ui.text.TextLinkStyles(
                    style = SpanStyle(color = spec.accent, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))))
                result.append(g[2]); result.pop()
            }
            else -> {
                val bold = g[4].isNotEmpty() || g[5].isNotEmpty()
                result.pushStyle(SpanStyle(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, fontStyle = if (bold) FontStyle.Normal else FontStyle.Italic))
                result.append(g.drop(4).first { it.isNotEmpty() }); result.pop()
            }
        }
        cursor = match.range.last + 1
    }
    result.append(source.substring(cursor))
    return result.toAnnotatedString()
}

@Composable
private fun RequestStatusCard(status: ChatRequestStatus, modifier: Modifier = Modifier) {
    val running = status in setOf(ChatRequestStatus.PREPARING, ChatRequestStatus.REQUESTING, ChatRequestStatus.RECEIVING_REPLY)
    val accent = when (status) {
        ChatRequestStatus.FAILED -> FuturePalette.Danger
        ChatRequestStatus.CANCELLED -> FuturePalette.Muted
        else -> FuturePalette.Cyan
    }
    val (title, detail) = when (status) {
        ChatRequestStatus.IDLE -> "" to ""
        ChatRequestStatus.PREPARING -> "准备中" to "正在整理当前会话内容"
        ChatRequestStatus.REQUESTING -> "请求中" to "正在连接 AI 服务"
        ChatRequestStatus.RECEIVING_REPLY -> "接收回复中" to "AI 服务正在返回内容"
        ChatRequestStatus.COMPLETED -> "请求完成" to "回复已加入当前会话"
        ChatRequestStatus.FAILED -> "请求失败" to "查看下方原因，可重试"
        ChatRequestStatus.CANCELLED -> "已取消" to "请求已停止，可重新发送"
    }
    Row(
        modifier.fillMaxWidth().glassPanel(ContinuousCornerShape(15.dp)).padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running) CircularProgressIndicator(Modifier.size(15.dp), color = accent, strokeWidth = 2.dp)
        else Text(if (status == ChatRequestStatus.COMPLETED) "✓" else if (status == ChatRequestStatus.FAILED) "!" else "Ⅱ", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(9.dp))
        Column {
            Text(title, color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = FuturePalette.Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun NoticeCard(text: String, onDismiss: () -> Unit, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().glassPanel(ContinuousCornerShape(18.dp)).padding(start = 15.dp, top = 9.dp, bottom = 9.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = FuturePalette.Danger, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
        if (onRetry != null) TextButton(onClick = onRetry) { Text("重试", color = FuturePalette.Cyan, fontSize = 11.sp) }
        TextButton(onClick = onDismiss) { Text("知道了", color = FuturePalette.VioletBright, fontSize = 11.sp) }
    }
}

@Composable
private fun AttachmentPreview(attachment: ChatAttachment, onRemove: () -> Unit) {
    val thumbnail = remember(attachment.previewPath) {
        attachment.previewPath.takeIf { it.isNotBlank() }?.let(BitmapFactory::decodeFile)?.asImageBitmap()
    }
    Row(
        modifier = Modifier.widthIn(min = 145.dp, max = 220.dp).clip(ContinuousCornerShape(15.dp))
            .softSurface(ContinuousCornerShape(15.dp)).padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumbnail != null) Image(thumbnail, contentDescription = attachment.name, modifier = Modifier.size(40.dp).clip(ContinuousCornerShape(9.dp)), contentScale = ContentScale.Crop)
        else Text("📎", modifier = Modifier.padding(horizontal = 8.dp), color = FuturePalette.Cyan, fontSize = 18.sp)
        Column(Modifier.weight(1f)) {
            Text(attachment.name, color = FuturePalette.Text, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(attachment.error ?: attachment.mimeType, color = if (attachment.error == null) FuturePalette.Muted else FuturePalette.Danger, fontSize = 10.sp, maxLines = 2)
        }
        TextButton(onClick = onRemove, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) { Text("×", color = FuturePalette.Muted) }
    }
}

@Composable
private fun Composer(
    text: String,
    isLoading: Boolean,
    isHistoryLoading: Boolean,
    canSend: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    attachments: List<ChatAttachment>,
    onPickAttachments: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onListen: () -> Unit,
    onOpenPromptLibrary: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val submit = { if (canSend) { keyboard?.hide(); focus.clearFocus(); onSend() } }
    val spec = LocalLiquidSpec.current
    var expandedEditorOpen by remember { mutableStateOf(false) }
    var expandedText by remember { mutableStateOf("") }
    var confirmClearText by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (attachments.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            attachments.forEach { attachment -> AttachmentPreview(attachment) { onRemoveAttachment(attachment.id) } }
        }
        Column(Modifier.fillMaxWidth()
            .animateContentSize(animationSpec = if (LocalGlassPrefs.current.reduceMotion) snap() else tween(180))
            .shadow(6.dp, ContinuousCornerShape(28.dp), ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.06f))
            .glassSurface(GlassMaterial.LIQUID, ContinuousCornerShape(28.dp), opticalLens = true)
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
            BasicTextField(
                value = text, enabled = !isHistoryLoading, onValueChange = onTextChanged,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 12.dp, vertical = 14.dp).semantics { contentDescription = "消息输入框" },
                textStyle = TextStyle(color = spec.text, fontSize = 16.sp, lineHeight = 24.sp), cursorBrush = SolidColor(spec.text),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }), maxLines = 6,
                decorationBox = { inner -> Box { if (text.isBlank()) Text("向 Gather AI 发送消息", color = spec.muted, fontSize = 15.sp); inner() } },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPickAttachments, enabled = !isLoading && !isHistoryLoading, modifier = Modifier.semantics { contentDescription = "添加附件" }) { FutureGlyph("plus", tint = spec.text, size = 22.dp) }
                IconButton(onClick = onOpenPromptLibrary, modifier = Modifier.semantics { contentDescription = "打开提示词库" }) { FutureGlyph("spark", tint = spec.text, size = 20.dp) }
                IconButton(onClick = { expandedText = text; expandedEditorOpen = true }, modifier = Modifier.semantics { contentDescription = "展开编辑" }) { FutureGlyph("edit", tint = spec.muted, size = 18.dp) }
                if (text.length > 120) Text("${text.length}", color = spec.muted, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                if (text.isNotEmpty()) IconButton(onClick = { confirmClearText = true }, modifier = Modifier.size(36.dp).semantics { contentDescription = "清空草稿" }) { FutureGlyph("delete", tint = spec.muted, size = 16.dp) }
                IconButton(onClick = onListen, enabled = !isLoading && !isHistoryLoading, modifier = Modifier.semantics { contentDescription = "语音输入" }) { FutureGlyph("mic", tint = spec.text, size = 21.dp) }
                IconButton(onClick = if (isLoading) onCancel else submit, enabled = isLoading || canSend,
                    modifier = Modifier.padding(end = 4.dp).size(38.dp).clip(CircleShape).background(if (isLoading || canSend) spec.text else spec.muted.copy(alpha = 0.14f)).semantics { contentDescription = if (isLoading) "停止生成" else "发送消息" }) {
                    val reduced = LocalGlassPrefs.current.reduceMotion
                    AnimatedContent(isLoading, transitionSpec = {
                        if (reduced) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                        else (fadeIn(tween(140)) + scaleIn(tween(180), initialScale = 0.7f)) togetherWith (fadeOut(tween(90)) + scaleOut(tween(120), targetScale = 0.7f))
                    }, label = "send-stop") { loading ->
                        FutureGlyph(if (loading) "stop" else "arrow_up", tint = if (loading || canSend) spec.ambientMiddle else spec.muted, size = 20.dp)
                    }
                }
            }
        }
        Text("AI 也可能会犯错，请核实重要信息。", color = spec.muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
    if (confirmClearText) AlertDialog(
        onDismissRequest = { confirmClearText = false },
        title = { Text("清空文字草稿？") },
        text = { Text("只会清空输入文字；已选择的附件会保留。") },
        confirmButton = { TextButton(onClick = { confirmClearText = false; onTextChanged("") }) { Text("清空", color = FuturePalette.Danger) } },
        dismissButton = { TextButton(onClick = { confirmClearText = false }) { Text("取消") } },
    )
    if (expandedEditorOpen) Dialog(
        onDismissRequest = { expandedEditorOpen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f).imePadding()
                .clip(ContinuousCornerShape(24.dp)).background(spec.surfaceTint)
                .border(1.dp, spec.surfaceEdge, ContinuousCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("编辑草稿", color = FuturePalette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("保存后仍是草稿，不会自动发送", color = FuturePalette.Muted, fontSize = 11.sp)
                }
                TextButton(onClick = { expandedEditorOpen = false }) { Text("取消", color = FuturePalette.Muted) }
            }
            BasicTextField(
                value = expandedText,
                onValueChange = { expandedText = it },
                modifier = Modifier.fillMaxWidth().weight(1f).clip(ContinuousCornerShape(15.dp))
                    .background(spec.ambientMiddle).padding(14.dp).semantics { contentDescription = "展开的草稿编辑框" },
                textStyle = TextStyle(color = FuturePalette.Text, fontSize = 15.sp, lineHeight = 23.sp),
                cursorBrush = Brush.verticalGradient(listOf(FuturePalette.VioletBright, FuturePalette.Cyan)),
                decorationBox = { inner -> Box {
                    if (expandedText.isEmpty()) Text("继续输入…", color = FuturePalette.Muted, fontSize = 14.sp)
                    inner()
                } },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${expandedText.codePointCount(0, expandedText.length)} 字", color = FuturePalette.Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { onTextChanged(expandedText); expandedEditorOpen = false }) {
                    Text("保存草稿", color = FuturePalette.VioletBright)
                }
            }
        }
    }
}


@Composable
private fun ConversationDrawer(
    sessions: List<ChatSession>, draftPreviews: Map<String, String>, activeSessionId: String,
    onNew: () -> Unit, onSelect: (String) -> Unit, onManage: () -> Unit, onLinkClick: () -> Unit,
    onTaskClick: () -> Unit, onSettingsClick: () -> Unit, onSearchContent: (String) -> Map<String, String>,
    onRename: (String, String) -> Boolean, onTogglePinned: (String) -> Boolean,
    onArchive: (String) -> Boolean, mutationsBlocked: Boolean,
    onClose: () -> Unit, modifier: Modifier = Modifier,
) {
    val spec = LocalLiquidSpec.current
    var query by remember { mutableStateOf("") }
    val contentMatches = remember(query, sessions, draftPreviews) { if (query.isBlank()) emptyMap() else onSearchContent(query) }
    val visible = sessions.filter { session ->
        !session.isArchived && (query.isBlank() || session.title.contains(query, true) || session.id in contentMatches ||
            draftPreviews[session.id]?.contains(query.trim(), true) == true)
    }
        .sortedWith(compareByDescending<ChatSession> { it.isPinned }.thenByDescending { it.updatedAt })
    val drawerListState = rememberLazyListState()
    var actionSessionId by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<ChatSession?>(null) }
    var renameText by remember { mutableStateOf("") }
    val activeIndex = visible.indexOfFirst { it.id == activeSessionId }
    LaunchedEffect(activeSessionId, activeIndex, query) {
        if (visible.isNotEmpty()) drawerListState.scrollToItem(if (query.isBlank()) activeIndex.coerceAtLeast(0) else 0)
    }
    Column(modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Gather AI", color = spec.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
            IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "关闭侧栏" }) { FutureGlyph("back", size = 20.dp) }
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("搜索对话（含消息与草稿）", fontSize = 14.sp) }, leadingIcon = { FutureGlyph("search", size = 18.dp) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }, modifier = Modifier.semantics { contentDescription = "清除对话搜索" }) { Text("×", fontSize = 20.sp) } },
            shape = ContinuousCornerShape(16.dp))
        TextButton(onClick = onNew, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "侧栏新建对话" }) { FutureGlyph("edit", size = 18.dp); Spacer(Modifier.width(12.dp)); Text("新对话", modifier = Modifier.weight(1f), color = spec.text, fontSize = 15.sp) }
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onLinkClick, modifier = Modifier.weight(1f)) { Text("设备互联", color = spec.text, fontSize = 13.sp) }
            TextButton(onClick = onTaskClick, modifier = Modifier.weight(1f)) { Text("任务中心", color = spec.text, fontSize = 13.sp) }
        }
        Text("对话", color = spec.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 4.dp))
        LazyColumn(Modifier.weight(1f), state = drawerListState, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (visible.isEmpty()) item { Text(if (query.isBlank()) "开始你的第一个对话" else "没有找到相关对话", color = spec.muted, fontSize = 13.sp, modifier = Modifier.padding(14.dp)) }
            items(visible, key = { it.id }) { session ->
                Row(Modifier.fillMaxWidth().clip(ContinuousCornerShape(16.dp)).background(if (session.id == activeSessionId) spec.surfaceTint else Color.Transparent), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { onSelect(session.id) }.padding(start = 14.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text((if (session.isPinned) "⌑  " else "") + session.title, color = spec.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        draftPreviews[session.id]?.let { DraftPreview(it) }
                        contentMatches[session.id]?.let { Text(it, color = spec.muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                    Box {
                        IconButton(onClick = { actionSessionId = session.id }, enabled = !mutationsBlocked,
                            modifier = Modifier.semantics { contentDescription = "对话操作：${session.title}" }) { Text("···", color = spec.muted, fontSize = 18.sp) }
                        DropdownMenu(expanded = actionSessionId == session.id, onDismissRequest = { actionSessionId = null }) {
                            DropdownMenuItem(text = { Text(if (session.isPinned) "取消置顶" else "置顶对话") }, onClick = {
                                actionSessionId = null; onTogglePinned(session.id)
                            })
                            DropdownMenuItem(text = { Text("重命名对话") }, onClick = {
                                actionSessionId = null; renameTarget = session; renameText = session.title
                            })
                            DropdownMenuItem(text = { Text("归档对话") }, enabled = session.id != activeSessionId, onClick = {
                                actionSessionId = null; onArchive(session.id)
                            })
                        }
                    }
                }
            }
        }
        TextButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) { Text("管理与归档对话", color = spec.muted, fontSize = 13.sp) }
        Row(Modifier.fillMaxWidth().clip(ContinuousCornerShape(14.dp)).clickable(onClick = onSettingsClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            FutureGlyph("settings", size = 21.dp); Spacer(Modifier.width(12.dp)); Text("设置", color = spec.text, fontSize = 15.sp)
        }
        Spacer(Modifier.navigationBarsPadding())
    }
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名对话") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it.take(60) }, singleLine = true, label = { Text("对话名称") }, modifier = Modifier.semantics { contentDescription = "重命名对话输入框" }) },
            confirmButton = { TextButton(enabled = renameText.isNotBlank(), onClick = { if (onRename(target.id, renameText)) renameTarget = null }) { Text("保存名称") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun DraftPreview(preview: String) {
    Text("草稿 · $preview", color = LocalLiquidSpec.current.cyan, fontSize = 10.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}
