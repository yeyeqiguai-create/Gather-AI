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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.futureai.domain.model.MessageRole
import com.tim.futureai.ui.theme.FutureBackdrop
import com.tim.futureai.ui.theme.FutureGlyph
import com.tim.futureai.ui.theme.FuturePalette
import com.tim.futureai.ui.theme.glassPanel

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
    isAiConfigured: Boolean = true,
    modifier: Modifier = Modifier,
) {
    FutureBackdrop(modifier) {
        androidx.compose.material3.Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            topBar = { BrandBar(onSettingsClick) },
            bottomBar = {
                Composer(
                    text = state.inputText,
                    isLoading = state.isLoading,
                    canSend = state.canSend,
                    onTextChanged = onInputChanged,
                    onSend = onSend,
                    onCancel = onCancel,
                )
            },
        ) { insets ->
            Column(
                modifier = Modifier.fillMaxSize().padding(insets),
            ) {
                if (state.messages.isEmpty()) {
                    WelcomeContent(
                        configured = isAiConfigured,
                        onSettingsClick = onSettingsClick,
                        onExamplePrompt = onExamplePrompt,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    if (!isAiConfigured) {
                        SetupStatusCard(onClick = onSettingsClick, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                    }
                    state.notice?.let { NoticeCard(it, onDismissNotice, Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) }
                    Conversation(
                        messages = state.messages,
                        isLoading = state.isLoading,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandBar(onSettingsClick: () -> Unit) {
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
            Text("YOUR THOUGHT PARTNER", color = FuturePalette.Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
        }
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).glassPanel(CircleShape).clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center,
        ) { FutureGlyph("settings", tint = FuturePalette.VioletBright, size = 21.dp) }
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
private fun Conversation(messages: List<ChatBubble>, isLoading: Boolean, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val count = messages.size + if (isLoading) 1 else 0
    LaunchedEffect(count) { if (count > 0) listState.animateScrollToItem(count - 1) }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(messages) { _, message -> MessageBubble(message) }
        if (isLoading) item { ThinkingBubble() }
    }
}

@Composable
private fun MessageBubble(message: ChatBubble) {
    val isUser = message.role == MessageRole.USER
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
            Text(message.text, color = FuturePalette.Text, fontSize = 14.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 36.dp)) {
        CircularProgressIndicator(Modifier.size(15.dp), color = FuturePalette.VioletBright, strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text("正在思考…", color = FuturePalette.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun NoticeCard(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().glassPanel(RoundedCornerShape(18.dp)).padding(start = 15.dp, top = 9.dp, bottom = 9.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = FuturePalette.Danger, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text("知道了", color = FuturePalette.VioletBright, fontSize = 11.sp) }
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
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
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
            Spacer(Modifier.width(10.dp))
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
