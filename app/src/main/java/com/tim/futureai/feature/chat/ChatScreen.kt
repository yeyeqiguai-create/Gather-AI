package com.tim.futureai.feature.chat

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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

private val ExamplePrompts = listOf(
    "把复杂概念讲得简单一点",
    "帮我整理今天的想法",
    "为周末计划一次轻松短途旅行",
)

@Composable
fun ChatScreen(
    state: ChatUiState,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onDismissNotice: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onCancel: () -> Unit = {},
    onExamplePrompt: (String) -> Unit = onInputChanged,
    onRegenerate: (Int) -> Unit = {},
    onRetry: (Int) -> Unit = {},
    onNewSession: () -> Unit = {},
    onSelectSession: (String) -> Unit = {},
    onRenameSession: (String, String) -> Boolean = { _, _ -> false },
    onToggleSessionPinned: (String) -> Boolean = { false },
    onDeleteSession: (String) -> Boolean = { false },
    onModelSelected: (String?) -> Unit = {},
    onPickAttachments: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    onListen: () -> Unit = {},
    onSpeak: (String, String) -> Unit = { _, _ -> },
    speakingMessageKey: String? = null,
    onEditMessage: (Int) -> Unit = {},
    onShareMessage: (String) -> Unit = {},
    onExportSession: () -> Unit = {},
    isAiConfigured: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var showSessionManager by remember { mutableStateOf(false) }
    FutureBackdrop(modifier) {
        androidx.compose.material3.Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            topBar = { BrandBar(state.sessionTitle, state.modelChoices, state.selectedModelId, onModelSelected, onExportSession, { showSessionManager = true }, onSettingsClick) },
            bottomBar = {
                Composer(
                    text = state.inputText,
                    isLoading = state.isLoading,
                    canSend = state.canSend,
                    onTextChanged = onInputChanged,
                    onSend = onSend,
                    onCancel = onCancel,
                    attachments = state.pendingAttachments,
                    onPickAttachments = onPickAttachments,
                    onRemoveAttachment = onRemoveAttachment,
                    onListen = onListen,
                )
            },
        ) { insets ->
            Column(
                modifier = Modifier.fillMaxSize().padding(insets),
            ) {
                if (state.messages.isEmpty()) {
                    Column(Modifier.fillMaxSize()) {
                        WelcomeContent(
                            configured = isAiConfigured,
                            onSettingsClick = onSettingsClick,
                            onExamplePrompt = onExamplePrompt,
                            modifier = Modifier.weight(1f),
                        )
                        state.notice?.let { NoticeCard(it, onDismissNotice, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) }
                    }
                } else {
                    if (!isAiConfigured) {
                        SetupStatusCard(onClick = onSettingsClick, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                    }
                    if (state.requestStatus != ChatRequestStatus.IDLE) {
                        RequestStatusCard(state.requestStatus, Modifier.padding(horizontal = 18.dp, vertical = 5.dp))
                    }
                    val retryIndex = state.messages.indexOfLast { it.role == MessageRole.USER }.takeIf { index ->
                        index >= 0 && state.messages[index].status in setOf(MessageStatus.FAILED, MessageStatus.CANCELLED)
                    }
                    state.notice?.let {
                        NoticeCard(it, onDismissNotice, retryIndex?.let { index -> { onRetry(index) } }, Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                    }
                    Conversation(
                        messages = state.messages,
                        onRegenerate = onRegenerate,
                        onRetry = onRetry,
                        speakingMessageKey = speakingMessageKey,
                        onSpeak = onSpeak,
                        onEditMessage = onEditMessage,
                        onShareMessage = onShareMessage,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
        if (showSessionManager) SessionManagerDialog(
            sessions = state.sessions,
            activeSessionId = state.activeSessionId,
            onDismiss = { showSessionManager = false },
            onNew = { onNewSession(); showSessionManager = false },
            onSelect = { onSelectSession(it); showSessionManager = false },
            onRename = onRenameSession,
            onTogglePinned = onToggleSessionPinned,
            onDelete = onDeleteSession,
        )
    }
}

@Composable
private fun BrandBar(
    sessionTitle: String,
    modelChoices: List<ChatModelChoice>,
    selectedModelId: String?,
    onModelSelected: (String?) -> Unit,
    onExportSession: () -> Unit,
    onSessionsClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    var modelMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp, start = 22.dp, end = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(15.dp)).background(
                Brush.linearGradient(listOf(Color(0xFF8D78F2), Color(0xFF5145A9), Color(0xFF253B78))),
            ),
            contentAlignment = Alignment.Center,
        ) { FutureGlyph("spark", tint = Color.White, size = 22.dp) }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Gather AI", color = FuturePalette.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
            Text(sessionTitle, color = FuturePalette.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (modelChoices.isNotEmpty()) {
                val selectedModel = modelChoices.firstOrNull { it.id == selectedModelId }
                Box {
                    Text(
                        "模型 · ${selectedModel?.displayName ?: "选择模型"}  ▾",
                        color = FuturePalette.Cyan, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { modelMenuExpanded = true },
                    )
                    DropdownMenu(expanded = modelMenuExpanded, onDismissRequest = { modelMenuExpanded = false }) {
                        modelChoices.forEach { choice -> DropdownMenuItem(
                            text = { Text(choice.displayName) },
                            onClick = { onModelSelected(choice.id); modelMenuExpanded = false },
                        ) }
                    }
                }
            }
        }
        Box(Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onExportSession), contentAlignment = Alignment.Center) {
            Text("⇩", color = FuturePalette.Cyan, fontSize = 20.sp)
        }
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).glassPanel(CircleShape).clickable(onClick = onSessionsClick),
            contentAlignment = Alignment.Center,
        ) { FutureGlyph("sessions", tint = FuturePalette.VioletBright, size = 21.dp) }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).glassPanel(CircleShape).clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center,
        ) { FutureGlyph("settings", tint = FuturePalette.VioletBright, size = 21.dp) }
    }
}

@Composable
private fun SessionManagerDialog(
    sessions: List<ChatSession>,
    activeSessionId: String,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onSelect: (String) -> Unit,
    onRename: (String, String) -> Boolean,
    onTogglePinned: (String) -> Boolean,
    onDelete: (String) -> Boolean,
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ChatSession?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(SessionSortOrder.RECENT_ACTIVITY) }
    var showSortMenu by remember { mutableStateOf(false) }
    val visibleSessions = sortAndFilterSessions(sessions, searchQuery, sortOrder)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).widthIn(max = 440.dp).glassPanel(RoundedCornerShape(28.dp)).padding(20.dp),
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
                Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0x241A2030))
                    .border(1.dp, FuturePalette.Edge, RoundedCornerShape(15.dp)).padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FutureGlyph("search", tint = FuturePalette.Muted, size = 17.dp)
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 9.dp, vertical = 13.dp),
                    textStyle = TextStyle(color = FuturePalette.Text, fontSize = 13.sp),
                    singleLine = true,
                    decorationBox = { inner -> Box { if (searchQuery.isEmpty()) Text("搜索会话名称", color = FuturePalette.Muted, fontSize = 13.sp); inner() } },
                )
                if (searchQuery.isNotEmpty()) {
                    TextButton(onClick = { searchQuery = "" }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("清空", color = FuturePalette.VioletBright, fontSize = 11.sp)
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
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (session.id == activeSessionId) Color(0x333F3971) else Color(0x171A2030))
                            .border(1.dp, if (session.id == activeSessionId) Color(0x557D6ED4) else FuturePalette.Edge, RoundedCornerShape(16.dp))
                            .clickable(enabled = editingId != session.id) { onSelect(session.id) }
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
                            Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(session.title, color = FuturePalette.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (session.isPinned) Text(" 置顶", color = FuturePalette.Cyan, fontSize = 9.sp)
                                }
                                Text(if (session.id == activeSessionId) "当前会话" else session.updatedAt.toString().take(10), color = FuturePalette.Muted, fontSize = 10.sp)
                            }
                            androidx.compose.material3.IconButton(onClick = { onTogglePinned(session.id) }) {
                                FutureGlyph(if (session.isPinned) "pin_filled" else "pin", tint = if (session.isPinned) FuturePalette.Cyan else FuturePalette.Muted, size = 17.dp)
                            }
                            androidx.compose.material3.IconButton(onClick = { editingId = session.id; editText = session.title }) { FutureGlyph("edit", tint = FuturePalette.Muted, size = 17.dp) }
                            androidx.compose.material3.IconButton(onClick = { deleteTarget = session }) { FutureGlyph("delete", tint = FuturePalette.Danger, size = 17.dp) }
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("完成", color = FuturePalette.VioletBright) }
        }
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
private fun WelcomeContent(
    configured: Boolean,
    onSettingsClick: () -> Unit,
    onExamplePrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 12 }) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                HeroCard(configured)
                if (!configured) SetupStatusCard(onClick = onSettingsClick)
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("从这里开始", color = FuturePalette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text("灵感提示", color = FuturePalette.Muted, fontSize = 11.sp)
                    }
                    ExamplePrompts.forEachIndexed { index, prompt ->
                        PromptCard(prompt, index, onClick = { onExamplePrompt(prompt) })
                    }
                }
                Text(
                    "回答由 AI 生成 · 重要信息请核实",
                    color = FuturePalette.Muted.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroCard(configured: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(30.dp)).padding(horizontal = 22.dp, vertical = 23.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.78f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.clip(CircleShape).background(Color(0x267BD9F5)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(FuturePalette.Cyan))
                Spacer(Modifier.width(7.dp))
                Text(if (configured) "AI 助手已连接" else "开启你的 AI 空间", color = FuturePalette.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
            Text("让想法\n自然向前。", color = FuturePalette.Text, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp)
            Text("提问、写作、规划，或只是把脑海里的念头说出来。", color = FuturePalette.Muted, fontSize = 13.sp, lineHeight = 20.sp)
        }
        Box(
            modifier = Modifier.align(Alignment.CenterEnd).size(74.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0x668C75F3), Color(0x226D74D7), Color.Transparent)))
                .border(1.dp, Color(0x44D8CFFF), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFE2DBFF), Color(0xFF907AFF), Color(0xFF514A9C)))))
            FutureGlyph("spark", tint = Color.White, size = 20.dp)
        }
    }
}

@Composable
private fun SetupStatusCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().glassPanel(RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x26A68AFF)), contentAlignment = Alignment.Center) {
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
private fun PromptCard(text: String, index: Int, onClick: () -> Unit) {
    val accents = listOf(FuturePalette.VioletBright, FuturePalette.Cyan, FuturePalette.Success)
    Row(
        modifier = Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(19.dp)).clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FutureGlyph("spark", tint = accents[index % accents.size], size = 17.dp)
        Spacer(Modifier.width(11.dp))
        Text(text, color = FuturePalette.Text.copy(alpha = 0.92f), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        Text("›", color = FuturePalette.Muted, fontSize = 22.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun Conversation(
    messages: List<ChatBubble>, onRegenerate: (Int) -> Unit, onRetry: (Int) -> Unit,
    speakingMessageKey: String?, onSpeak: (String, String) -> Unit,
    onEditMessage: (Int) -> Unit, onShareMessage: (String) -> Unit, modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val retryUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(messages) { index, message ->
            val key = "$index:${message.text.hashCode()}"
            MessageBubble(message, index, index == retryUserIndex, onRegenerate, onRetry, key, speakingMessageKey == key, onSpeak, onEditMessage, onShareMessage)
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatBubble, index: Int, canRetry: Boolean, onRegenerate: (Int) -> Unit, onRetry: (Int) -> Unit,
    messageKey: String, isSpeaking: Boolean, onSpeak: (String, String) -> Unit,
    onEditMessage: (Int) -> Unit, onShareMessage: (String) -> Unit,
) {
    val isUser = message.role == MessageRole.USER
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember(message.text) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isUser) {
            Box(Modifier.padding(end = 8.dp).size(28.dp).clip(CircleShape).background(Color(0x338D78F2)), contentAlignment = Alignment.Center) {
                FutureGlyph("spark", tint = FuturePalette.VioletBright, size = 14.dp)
            }
        }
        Column(
            modifier = Modifier.widthIn(max = 330.dp).fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = if (isUser) 7.dp else 22.dp, bottomStart = if (isUser) 22.dp else 7.dp))
                .background(if (isUser) Brush.linearGradient(listOf(Color(0xFF705CC5), Color(0xFF51489B))) else Brush.verticalGradient(listOf(Color(0xD61B2032), Color(0xC5121728))))
                .border(1.dp, if (isUser) Color(0x44D7CFFF) else FuturePalette.Edge, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = if (isUser) 7.dp else 22.dp, bottomStart = if (isUser) 22.dp else 7.dp))
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(if (isUser) "你" else "GATHER AI", color = if (isUser) Color(0xFFDCD5FF) else FuturePalette.Cyan, fontSize = 9.sp, letterSpacing = 1.15.sp, fontWeight = FontWeight.SemiBold)
            if (isUser) {
                message.attachments.forEach { attachment ->
                    Text("📎 ${attachment.name}", color = FuturePalette.Cyan, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(message.text, color = FuturePalette.Text, fontSize = 14.sp, lineHeight = 23.sp)
            } else {
                MarkdownMessage(message.text, onCopyCode = { code ->
                    copyMessageText(code, { text ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Gather AI code", text))
                    }) {}
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    copyMessageText(message.text, { text ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Gather AI message", text))
                    }) {
                        copied = true
                        scope.launch { delay(1400); copied = false }
                    }
                }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    FutureGlyph("copy", tint = FuturePalette.Muted, size = 14.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(if (copied) "已复制" else "复制", color = if (copied) FuturePalette.Cyan else FuturePalette.Muted, fontSize = 10.sp)
                }
                TextButton(onClick = { onShareMessage(message.text) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    Text("分享", color = FuturePalette.Muted, fontSize = 10.sp)
                }
                if (isUser) TextButton(onClick = { onEditMessage(index) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    Text("编辑", color = FuturePalette.VioletBright, fontSize = 10.sp)
                }
                if (!isUser) TextButton(onClick = { onSpeak(messageKey, message.text) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    Text(if (isSpeaking) "停止朗读" else "朗读", color = if (isSpeaking) FuturePalette.Cyan else FuturePalette.Muted, fontSize = 10.sp)
                }
                if (!isUser) TextButton(onClick = { onRegenerate(index) }, enabled = message.status != MessageStatus.GENERATING, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    FutureGlyph("retry", tint = FuturePalette.VioletBright, size = 14.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("重新生成", color = FuturePalette.VioletBright, fontSize = 10.sp)
                }
                if (isUser && canRetry && message.status in setOf(MessageStatus.FAILED, MessageStatus.CANCELLED)) TextButton(onClick = { onRetry(index) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    Text("重试", color = FuturePalette.VioletBright, fontSize = 10.sp)
                }
            }
            when (message.status) {
                MessageStatus.GENERATING -> Text("发送中…", color = FuturePalette.Cyan, fontSize = 10.sp)
                MessageStatus.FAILED -> Text("发送失败", color = FuturePalette.Danger, fontSize = 10.sp)
                MessageStatus.CANCELLED -> Text("已停止", color = FuturePalette.Muted, fontSize = 10.sp)
                MessageStatus.SENT -> Unit
            }
        }
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
                is MarkdownBlock.Paragraph -> Text(inlineMarkdown(block.text), color = FuturePalette.Text, fontSize = 14.sp, lineHeight = 24.sp)
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
                    Box(Modifier.padding(end = 10.dp).width(3.dp).heightIn(min = 34.dp).background(FuturePalette.VioletBright.copy(alpha = 0.8f), RoundedCornerShape(2.dp)))
                    Text(inlineMarkdown(block.text), color = FuturePalette.Muted, fontSize = 13.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
                }
                is MarkdownBlock.Code -> CodeBlock(block.source, onCopyCode)
                MarkdownBlock.Divider -> Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(FuturePalette.Edge))
            }
        }
    }
}

@Composable
private fun CodeBlock(source: String, onCopy: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF10131D)).border(1.dp, FuturePalette.Edge, RoundedCornerShape(14.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1B2030)).padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("代码", color = FuturePalette.Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { onCopy(source) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                FutureGlyph("copy", tint = FuturePalette.VioletBright, size = 13.dp)
                Spacer(Modifier.width(4.dp))
                Text("复制代码", color = FuturePalette.VioletBright, fontSize = 10.sp)
            }
        }
        Box(
            Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()).padding(12.dp),
        ) {
            Text(source, color = Color(0xFFD8E6FF), fontSize = 12.sp, lineHeight = 18.sp, softWrap = false, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}

private fun inlineMarkdown(source: String): AnnotatedString {
    val result = AnnotatedString.Builder()
    val pattern = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__|\\*([^*\\n]+?)\\*|_([^_\\n]+?)_")
    var cursor = 0
    pattern.findAll(source).forEach { match ->
        result.append(source.substring(cursor, match.range.first))
        val bold = match.groups[1]?.value ?: match.groups[2]?.value
        val italic = match.groups[3]?.value ?: match.groups[4]?.value
        val content = bold ?: italic.orEmpty()
        val style = if (bold != null) SpanStyle(fontWeight = FontWeight.Bold) else SpanStyle(fontStyle = FontStyle.Italic)
        result.pushStyle(style)
        result.append(content)
        result.pop()
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
        modifier.fillMaxWidth().glassPanel(RoundedCornerShape(15.dp)).padding(horizontal = 13.dp, vertical = 9.dp),
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
        modifier = modifier.fillMaxWidth().glassPanel(RoundedCornerShape(18.dp)).padding(start = 15.dp, top = 9.dp, bottom = 9.dp, end = 7.dp),
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
        modifier = Modifier.widthIn(min = 145.dp, max = 220.dp).clip(RoundedCornerShape(15.dp))
            .background(Color(0x44101421)).padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumbnail != null) Image(thumbnail, contentDescription = attachment.name, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
        else Text("📎", modifier = Modifier.padding(horizontal = 8.dp), color = FuturePalette.Cyan, fontSize = 18.sp)
        Column(Modifier.weight(1f)) {
            Text(attachment.name, color = FuturePalette.Text, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(attachment.error ?: attachment.mimeType, color = if (attachment.error == null) FuturePalette.Muted else FuturePalette.Danger, fontSize = 8.sp, maxLines = 2)
        }
        TextButton(onClick = onRemove, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) { Text("×", color = FuturePalette.Muted) }
    }
}

@Composable
private fun Composer(
    text: String,
    isLoading: Boolean,
    canSend: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    attachments: List<ChatAttachment>,
    onPickAttachments: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onListen: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (attachments.isNotEmpty()) Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { attachments.forEach { attachment -> AttachmentPreview(attachment) { onRemoveAttachment(attachment.id) } } }
        Row(
            modifier = Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(27.dp)).padding(start = 17.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                textStyle = TextStyle(color = FuturePalette.Text, fontSize = 14.sp, lineHeight = 20.sp),
                cursorBrush = Brush.verticalGradient(listOf(FuturePalette.VioletBright, FuturePalette.Cyan)),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) { onSend(); keyboard?.hide() } }),
                maxLines = 4,
                decorationBox = { inner ->
                    Box {
                        if (text.isBlank()) Text("说说你在想什么…", color = FuturePalette.Muted, fontSize = 13.sp)
                        inner()
                    }
                },
            )
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).clickable(enabled = !isLoading, onClick = onPickAttachments),
                contentAlignment = Alignment.Center,
            ) { Text("＋", color = FuturePalette.Cyan, fontSize = 23.sp) }
            Box(Modifier.size(36.dp).clip(CircleShape).clickable(enabled = !isLoading, onClick = onListen), contentAlignment = Alignment.Center) {
                Text("🎙", fontSize = 17.sp)
            }
            Spacer(Modifier.width(3.dp))
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape)
            .background(if (isLoading) Brush.linearGradient(listOf(Color(0xFF3C345A), Color(0xFF28243B))) else if (canSend) Brush.linearGradient(listOf(FuturePalette.Violet, Color(0xFF6351B5))) else Brush.linearGradient(listOf(Color(0xFF292C3A), Color(0xFF222532))))
                    .border(1.dp, if (canSend || isLoading) Color(0x55D7CBFF) else Color(0x1AFFFFFF), CircleShape)
                    .clickable(enabled = isLoading || canSend, onClick = if (isLoading) onCancel else { { onSend(); keyboard?.hide(); Unit } }),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) FutureGlyph("stop", tint = FuturePalette.VioletBright, size = 18.dp)
                else FutureGlyph("send", tint = if (canSend) Color.White else FuturePalette.Muted.copy(alpha = 0.55f), size = 19.dp)
            }
        }
        Text("AI 生成内容可能不准确", color = FuturePalette.Muted.copy(alpha = 0.7f), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}
