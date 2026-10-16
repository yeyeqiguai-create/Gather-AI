package com.tim.futureai

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import android.animation.ValueAnimator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.core.view.WindowCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.tim.futureai.core.security.SecureKeyStore
import com.tim.futureai.data.ai.OpenAiCompatProvider
import com.tim.futureai.data.chat.FileChatSessionsRepository
import com.tim.futureai.data.update.GitHubUpdateRepository
import com.tim.futureai.data.usage.FileUsageRepository
import com.tim.futureai.feature.update.UpdateCheckResult
import com.tim.futureai.feature.update.UpdateCheckGate
import com.tim.futureai.feature.update.UpdatePromptAction
import com.tim.futureai.feature.update.UpdatePromptReducer
import com.tim.futureai.feature.update.UpdatePromptState
import com.tim.futureai.feature.update.UpdateRelease
import com.tim.futureai.feature.update.StartupUpdateCheck
import com.tim.futureai.domain.ai.AiChatConfig
import com.tim.futureai.feature.chat.ChatScreen
import com.tim.futureai.feature.chat.ChatUiState
import com.tim.futureai.feature.chat.ChatViewModel
import com.tim.futureai.feature.settings.AiSettingsManager
import com.tim.futureai.feature.settings.AiSettingsScreen
import com.tim.futureai.feature.settings.SettingsSaveResult
import com.tim.futureai.domain.usage.UsageSnapshot
import com.tim.futureai.platform.files.AttachmentImporter
import com.tim.futureai.platform.speech.SpeechController
import com.tim.futureai.platform.speech.SpeechRecognitionMessages
import com.tim.futureai.ui.theme.FuturePalette
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FutureColors = darkColorScheme(
    primary = FuturePalette.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF43377D),
    onPrimaryContainer = FuturePalette.Text,
    background = FuturePalette.Night,
    onBackground = FuturePalette.Text,
    surface = Color(0xFF141827),
    onSurface = FuturePalette.Text,
    surfaceVariant = Color(0xFF20253A),
    onSurfaceVariant = FuturePalette.Muted,
    error = FuturePalette.Danger,
    errorContainer = Color(0xFF492638),
    onErrorContainer = FuturePalette.Text,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // 组装依赖：安全存储 → 配置读取 → Provider → ViewModel。
        val keyStore = SecureKeyStore(this)
        val settingsManager = AiSettingsManager(keyStore)
        val provider = OpenAiCompatProvider(configProvider = { settingsManager.configFor(null) })
        val sessionsRepository = FileChatSessionsRepository(
            File(filesDir, "chat-sessions.json"),
            File(filesDir, "chat-history.json"),
        )
        val usageRepository = FileUsageRepository(File(filesDir, "usage-stats.json"))
        val viewModel = ChatViewModel(
            lifecycleScope,
            provider,
            sessionsRepository = sessionsRepository,
            providerForProfileId = { profileId -> OpenAiCompatProvider({ settingsManager.configFor(profileId) }) },
            defaultModelProfileId = { settingsManager.load().takeIf { it.isConfigured }?.id },
            usageRepository = usageRepository,
        )
        viewModel.setModelChoices(settingsManager.profiles())
        val appInfo = packageManager.getPackageInfo(packageName, 0)
        val updateRepository = GitHubUpdateRepository(
            currentVersionName = appInfo.versionName.orEmpty(),
            currentVersionCode = if (Build.VERSION.SDK_INT >= 28) appInfo.longVersionCode.toInt() else appInfo.versionCode,
            isInternalBuild = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )

        setContent {
            MaterialTheme(colorScheme = FutureColors) {
                val initialSettings = remember { settingsManager.load() }
                var profiles by remember { mutableStateOf(settingsManager.profiles()) }
                var usage by remember { mutableStateOf(UsageSnapshot()) }
                var uiState by remember { mutableStateOf<ChatUiState>(ChatUiState()) }
                var showSettings by remember { mutableStateOf(false) }
                var settings by remember { mutableStateOf(initialSettings) }
                var isAiConfigured by remember { mutableStateOf(initialSettings.isConfigured) }
                var settingsMessage by remember { mutableStateOf<String?>(null) }
                var settingsMessageIsError by remember { mutableStateOf(false) }
                var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
                var updateChecking by remember { mutableStateOf(false) }
                var updateMessage by remember { mutableStateOf<String?>(null) }
                var downloadProgress by remember { mutableStateOf<Int?>(null) }
                var downloadedApk by remember { mutableStateOf<File?>(null) }
                var downloadJob by remember { mutableStateOf<Job?>(null) }
                var updatePrompt by remember { mutableStateOf<UpdatePromptState?>(null) }
                val updateCheckGate = remember { UpdateCheckGate() }
                val startupUpdateCheck = remember { StartupUpdateCheck() }
                var speakingMessageKey by remember { mutableStateOf<String?>(null) }
                val composeScope = rememberCoroutineScope()
                val screenTransitionMillis = remember {
                    val motionEnabled = if (Build.VERSION.SDK_INT >= 26) ValueAnimator.areAnimatorsEnabled()
                    else Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
                    if (motionEnabled) 180 else 0
                }
                val speechController = remember {
                    SpeechController(applicationContext,
                        onSpeakingChanged = { key -> composeScope.launch(Dispatchers.Main) { speakingMessageKey = key } },
                        onError = { message -> composeScope.launch(Dispatchers.Main) { viewModel.showNotice(message) } },
                    )
                }
                DisposableEffect(speechController) { onDispose { speechController.shutdown() } }
                val speechInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    val transcript = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
                    if (result.resultCode == RESULT_OK && transcript.isNotBlank()) {
                        viewModel.updateInput(listOf(uiState.inputText.trim(), transcript.trim()).filter(String::isNotBlank).joinToString(" "))
                    } else {
                        val errorCode = result.data?.getIntExtra("android.speech.extra.ERROR", -1)?.takeIf { it >= 0 }
                        viewModel.showNotice(SpeechRecognitionMessages.failure(errorCode))
                    }
                }
                var pendingExport by remember { mutableStateOf("") }
                val exportDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
                    if (uri != null) composeScope.launch(Dispatchers.IO) {
                        val saved = runCatching {
                            contentResolver.openOutputStream(uri)?.use { it.write(pendingExport.toByteArray(Charsets.UTF_8)) }
                                ?: error("无法写入所选位置")
                        }.isSuccess
                        withContext(Dispatchers.Main) {
                            viewModel.showNotice(if (saved) "会话已导出" else "导出失败，请重试")
                        }
                    }
                }
                val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                    composeScope.launch(Dispatchers.IO) {
                        val imported = uris.map { AttachmentImporter.import(this@MainActivity, it) }
                        withContext(Dispatchers.Main) { viewModel.addAttachments(imported) }
                    }
                }
                val checkForUpdates: () -> Unit = {
                    if (updateCheckGate.tryStart()) {
                        updateChecking = true
                        updateMessage = null
                        composeScope.launch {
                            try {
                                updateResult = updateRepository.check()
                                (updateResult as? UpdateCheckResult.Available)?.let { updatePrompt = UpdatePromptState(it.release) }
                            } finally {
                                updateCheckGate.finish()
                                updateChecking = false
                            }
                        }
                    }
                }
                LaunchedEffect(Unit) { if (startupUpdateCheck.shouldCheck()) checkForUpdates() }
                val installDownloadedApk: () -> Unit = {
                    val apk = downloadedApk
                    if (apk != null && Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                        updateMessage = "请允许 Gather AI 安装未知应用，返回后再次点击安装"
                        showSettings = true
                    } else if (apk != null) runCatching {
                        val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", apk)
                        startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { updateMessage = "无法启动系统安装器，请重试" }
                }
                fun startUpdateDownload(release: UpdateRelease, installWhenReady: Boolean) {
                    if (installWhenReady) {
                        settings = settingsManager.load()
                        profiles = settingsManager.profiles()
                        isAiConfigured = settings.isConfigured
                        composeScope.launch { usage = usageRepository.load() }
                        showSettings = true
                    }
                    downloadProgress = 0
                    updateMessage = "正在下载并校验 APK…"
                    downloadJob = composeScope.launch {
                        try {
                            downloadedApk = updateRepository.downloadAndVerify(this@MainActivity, release) { progress ->
                                composeScope.launch { downloadProgress = progress }
                            }
                            downloadProgress = 100
                            updateMessage = "SHA-256 与签名校验通过"
                            if (installWhenReady) installDownloadedApk()
                        } catch (_: CancellationException) {
                            downloadProgress = null
                            updateMessage = "下载已取消"
                        } catch (error: Exception) {
                            downloadProgress = null
                            updateMessage = error.message ?: "APK 下载或校验失败，可立即重试"
                            if (installWhenReady) showSettings = true
                        }
                    }
                }
                viewModel.onStateChanged = { uiState = it }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Crossfade(targetState = showSettings, animationSpec = tween(screenTransitionMillis), label = "main-settings-transition") { showingSettings ->
                    if (showingSettings) {
                        AiSettingsScreen(
                            settings = settings,
                            message = settingsMessage,
                            onSettingsChanged = { settings = it; settingsMessage = null },
                            onSave = {
                                when (val result = settingsManager.save(settings)) {
                                    SettingsSaveResult.Saved -> {
                                        settings = settings.copy(apiKey = "", hasSavedApiKey = true)
                                        profiles = settingsManager.profiles()
                                        viewModel.setModelChoices(profiles)
                                        settingsMessage = "配置已安全保存"
                                        settingsMessageIsError = false
                                        isAiConfigured = true
                                    }
                                    is SettingsSaveResult.Invalid -> {
                                        settingsMessage = result.message
                                        settingsMessageIsError = true
                                    }
                                    SettingsSaveResult.StorageError -> {
                                        settingsMessage = "保存失败，请重试"
                                        settingsMessageIsError = true
                                    }
                                }
                            },
                            onBack = { showSettings = false },
                            messageIsError = settingsMessageIsError,
                            updateResult = updateResult,
                            updateChecking = updateChecking,
                            updateMessage = updateMessage,
                            downloadProgress = downloadProgress,
                            onCheckUpdates = checkForUpdates,
                            onDownloadUpdate = {
                                val release = (updateResult as? UpdateCheckResult.Available)?.release ?: return@AiSettingsScreen
                                startUpdateDownload(release, installWhenReady = false)
                            },
                            onCancelDownload = { downloadJob?.cancel() },
                            onInstallUpdate = installDownloadedApk,
                            onViewUpdateNotes = {
                                (updateResult as? UpdateCheckResult.Available)?.let { updatePrompt = UpdatePromptState(it.release, showFullNotes = true) }
                            },
                            profiles = profiles,
                            onSelectProfile = { id ->
                                settingsManager.selectProfile(id)
                                settings = settingsManager.loadProfile(id) ?: settings
                                profiles = settingsManager.profiles()
                                viewModel.setModelChoices(profiles)
                                isAiConfigured = settings.isConfigured
                                settingsMessage = null
                            },
                            onCreateProfile = {
                                settings = com.tim.futureai.feature.settings.AiSettings(
                                    id = UUID.randomUUID().toString(), name = "新配置",
                                )
                                settingsMessage = null
                            },
                            onDeleteProfile = { id ->
                                if (settingsManager.deleteProfile(id)) {
                                    profiles = settingsManager.profiles()
                                    settings = settingsManager.load()
                                    isAiConfigured = settings.isConfigured
                                    viewModel.setModelChoices(profiles)
                                    settingsMessage = "配置已删除"
                                    settingsMessageIsError = false
                                }
                            },
                            onTestConnection = {
                                val saved = settingsManager.configFor(settings.id)
                                val testConfig = AiChatConfig(
                                    baseUrl = settings.baseUrl,
                                    model = settings.model,
                                    apiKey = settings.apiKey.ifBlank { saved?.apiKey.orEmpty() },
                                )
                                settingsMessage = "正在测试连接…"
                                settingsMessageIsError = false
                                composeScope.launch {
                                    when (val result = OpenAiCompatProvider({ testConfig }).testConnection()) {
                                        is com.tim.futureai.core.common.AppResult.Success -> {
                                            settingsMessage = "连接成功，服务商可访问"
                                            settingsMessageIsError = false
                                        }
                                        is com.tim.futureai.core.common.AppResult.Failure -> {
                                            settingsMessage = ChatViewModel.errorToUserMessage(result.error)
                                            settingsMessageIsError = true
                                        }
                                    }
                                }
                            },
                            usage = usage,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else ChatScreen(
                        state = uiState,
                        onInputChanged = viewModel::updateInput,
                        onSend = viewModel::send,
                        onDismissNotice = viewModel::dismissNotice,
                        onCancel = viewModel::cancel,
                        onRegenerate = viewModel::regenerate,
                        onRetry = viewModel::retry,
                        onNewSession = viewModel::newSession,
                        onSelectSession = viewModel::selectSession,
                        onRenameSession = viewModel::renameSession,
                        onToggleSessionPinned = viewModel::toggleSessionPinned,
                        onDeleteSession = viewModel::deleteSession,
                        onModelSelected = viewModel::selectModel,
                        onPickAttachments = {
                            attachmentPicker.launch(arrayOf(
                                "image/*", "text/*", "application/pdf",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            ))
                        },
                        onRemoveAttachment = { id ->
                            uiState.pendingAttachments.firstOrNull { it.id == id }?.let(AttachmentImporter::delete)
                            viewModel.removeAttachment(id)
                        },
                        onListen = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出要发送的内容")
                                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                            }
                            if (intent.resolveActivity(packageManager) == null) {
                                viewModel.showNotice("此设备没有可用的语音识别服务，请直接输入文字")
                            } else runCatching { speechInput.launch(intent) }
                                .onFailure { viewModel.showNotice("无法启动语音识别，请检查系统语音服务") }
                        },
                        onSpeak = { key, text ->
                            if (speakingMessageKey == key) speechController.stop() else speechController.speak(key, text)
                        },
                        speakingMessageKey = speakingMessageKey,
                        onEditMessage = viewModel::editUserMessage,
                        onShareMessage = { text ->
                            runCatching {
                                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }, "分享消息"))
                            }.onFailure { viewModel.showNotice("无法打开分享面板") }
                        },
                        onExportSession = {
                            pendingExport = buildString {
                                append("# ").append(uiState.sessionTitle).append("\n\n")
                                uiState.messages.forEach { message ->
                                    append(if (message.role == com.tim.futureai.domain.model.MessageRole.USER) "你" else "Gather AI")
                                        .append("\n").append(message.text).append("\n")
                                    message.attachments.forEach { append("附件：").append(it.name).append("\n") }
                                    append("\n")
                                }
                            }
                            val safeName = uiState.sessionTitle.replace(Regex("[^\\p{L}\\p{N}_-]"), "_").take(40).ifBlank { "Gather-AI" }
                            runCatching { exportDocument.launch("$safeName.txt") }
                                .onFailure { viewModel.showNotice("无法打开文件保存器") }
                        },
                        onExamplePrompt = viewModel::updateInput,
                        onSettingsClick = {
                            settings = settingsManager.load()
                            profiles = settingsManager.profiles()
                            composeScope.launch { usage = usageRepository.load() }
                            isAiConfigured = settings.isConfigured
                            settingsMessage = null
                            showSettings = true
                        },
                        isAiConfigured = isAiConfigured,
                        modifier = Modifier.fillMaxSize(),
                    )
                    }
                }
                updatePrompt?.let { prompt ->
                    AlertDialog(
                        onDismissRequest = { updatePrompt = UpdatePromptReducer.reduce(prompt, UpdatePromptAction.LATER) },
                        title = { Text(if (prompt.showFullNotes) "${prompt.release.versionName} 更新内容" else "发现新版本 ${prompt.release.versionName}") },
                        text = {
                            androidx.compose.foundation.layout.Column(
                                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                            ) {
                                Text("${prompt.release.channel.label} · ${prompt.release.publishedAt.take(10).ifBlank { "发布日期未知" }}")
                                if (prompt.showFullNotes) {
                                    if (prompt.release.notes.isBlank()) Text("此版本没有附带更新说明")
                                    else Text(prompt.release.notes)
                                } else {
                                    Text(prompt.release.notes.take(500).ifBlank { "此版本包含修复和改进" })
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                updatePrompt = UpdatePromptReducer.reduce(prompt, UpdatePromptAction.START_UPDATE)
                                startUpdateDownload(prompt.release, installWhenReady = true)
                            }) { Text("立即更新") }
                        },
                        dismissButton = {
                            androidx.compose.foundation.layout.Row {
                                TextButton(onClick = {
                                    updatePrompt = UpdatePromptReducer.reduce(prompt, UpdatePromptAction.VIEW_NOTES)
                                }) { Text(if (prompt.showFullNotes) "更新说明" else "查看更新内容") }
                                TextButton(onClick = { updatePrompt = UpdatePromptReducer.reduce(prompt, UpdatePromptAction.LATER) }) { Text("稍后") }
                            }
                        },
                    )
                }
            }
        }
    }
}
