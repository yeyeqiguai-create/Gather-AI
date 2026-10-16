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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.tim.futureai.ui.theme.FutureBackdrop
import com.tim.futureai.ui.theme.FutureGlyph
import com.tim.futureai.ui.theme.FuturePalette
import com.tim.futureai.ui.theme.glassPanel
import com.tim.futureai.feature.update.UpdateCheckResult
import com.tim.futureai.feature.update.UpdateSection

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
) {
    var showApiKey by remember { mutableStateOf(false) }
    FutureBackdrop(modifier) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            topBar = {
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
                            modifier = Modifier.clip(CircleShape).background(if (configured) Color(0x1F8AE0C2) else Color(0x1FA6AAC0)).padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(if (configured) FuturePalette.Success else FuturePalette.Muted))
                            Spacer(Modifier.width(6.dp))
                            Text(if (configured) "已配置" else "未配置", color = if (configured) FuturePalette.Success else FuturePalette.Muted, fontSize = 10.sp)
                        }
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).imePadding().navigationBarsPadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(17.dp),
            ) {
                IntroPanel()
                Column(
                    modifier = Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(26.dp)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    Text("连接信息", color = FuturePalette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                            Modifier.fillMaxSize().clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(Color(0xFF8B73EA), Color(0xFF5B4AAE)))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                FutureGlyph("check", tint = Color.White, size = 17.dp)
                                Text("保存配置", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                UpdateSection(
                    result = updateResult,
                    checking = updateChecking,
                    message = updateMessage,
                    downloadProgress = downloadProgress,
                    onCheck = onCheckUpdates,
                    onDownload = onDownloadUpdate,
                    onCancel = onCancelDownload,
                    onInstall = onInstallUpdate,
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
