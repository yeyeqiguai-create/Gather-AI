package com.tim.futureai

import android.content.Intent
import android.content.ClipData
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.os.Build
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import android.animation.ValueAnimator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.core.view.WindowCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.tim.futureai.core.security.SecureKeyStore
import com.tim.futureai.data.ai.AiProviderRegistry
import com.tim.futureai.data.chat.FileChatSessionsRepository
import com.tim.futureai.data.update.GitHubUpdateRepository
import com.tim.futureai.data.usage.FileUsageRepository
import com.tim.futureai.data.task.FileTaskRepository
import com.tim.futureai.data.task.AiTaskPlanSourceFactory
import com.tim.futureai.domain.task.BuiltInTaskToolRegistry
import com.tim.futureai.domain.task.TaskEngine
import com.tim.futureai.domain.task.toTaskEnginePolicy
import com.tim.futureai.feature.update.UpdateCheckResult
import com.tim.futureai.feature.update.UpdateCheckGate
import com.tim.futureai.feature.update.UpdatePromptAction
import com.tim.futureai.feature.update.UpdatePromptReducer
import com.tim.futureai.feature.update.UpdatePromptState
import com.tim.futureai.feature.update.UpdateRelease
import com.tim.futureai.feature.update.UpdateInstallFlow
import com.tim.futureai.feature.update.StartupUpdateCheck
import com.tim.futureai.domain.ai.AiChatConfig
import com.tim.futureai.domain.ai.AiSettings
import com.tim.futureai.domain.ai.ProviderProtocol
import com.tim.futureai.domain.ai.SettingsSaveResult
import com.tim.futureai.feature.chat.ChatScreen
import com.tim.futureai.feature.chat.ChatUiState
import com.tim.futureai.feature.chat.ChatViewModel
import com.tim.futureai.data.settings.AiSettingsManager
import com.tim.futureai.data.settings.AppPreferencesManager
import com.tim.futureai.feature.settings.AiSettingsScreen
import com.tim.futureai.feature.settings.FullSettingsCenter
import com.tim.futureai.feature.settings.UsageCard
import com.tim.futureai.feature.task.TaskCenterScreen
import com.tim.futureai.feature.update.UpdateSection
import com.tim.futureai.domain.settings.AppPreferences
import com.tim.futureai.domain.settings.SettingsSection
import com.tim.futureai.domain.usage.UsageSnapshot
import com.tim.futureai.platform.files.AttachmentImporter
import com.tim.futureai.platform.speech.SpeechController
import com.tim.futureai.platform.speech.SpeechRecognitionMessages
import com.tim.futureai.ui.theme.FuturePalette
import com.tim.futureai.ui.liquid.GatherLiquidTheme
import com.tim.futureai.ui.liquid.GlassRenderPrefs
import com.tim.futureai.ui.liquid.LiquidVisualTheme
import com.tim.futureai.domain.settings.VisualTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val UPDATE_INSTALL_PREFS = "update_install_flow"
private const val KEY_UPDATE_APK_PATH = "apk_path"
private const val KEY_UPDATE_APK_SHA256 = "apk_sha256"
private const val KEY_UPDATE_VERSION_NAME = "version_name"
private const val KEY_UPDATE_VERSION_CODE = "version_code"
private const val KEY_UPDATE_PHASE = "phase"
private const val KEY_UPDATE_PREVIOUS_CODE = "previous_version_code"
private const val KEY_UPDATE_PREVIOUS_TIME = "previous_last_update_time"
private const val INSTALL_PHASE_NONE = 0
private const val INSTALL_PHASE_AUTHORIZATION = 1
private const val INSTALL_PHASE_SYSTEM_UI = 2

class MainActivity : ComponentActivity() {
    private fun createUpdateRepository(): GitHubUpdateRepository {
        val appInfo = packageManager.getPackageInfo(packageName, 0)
        return GitHubUpdateRepository(
            currentVersionName = appInfo.versionName.orEmpty(),
            currentVersionCode = if (Build.VERSION.SDK_INT >= 28) appInfo.longVersionCode.toInt() else appInfo.versionCode,
            isInternalBuild = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )
    }

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
        val appPreferencesManager = AppPreferencesManager(keyStore)
        val providerRegistry = AiProviderRegistry()
        val provider = providerRegistry.create(ProviderProtocol.OPENAI_COMPATIBLE) { settingsManager.configFor(null) }
        val sessionsRepository = FileChatSessionsRepository(
            File(filesDir, "chat-sessions.json"),
            File(filesDir, "chat-history.json"),
        )
        val usageRepository = FileUsageRepository(File(filesDir, "usage-stats.json"))
        val taskRepository = FileTaskRepository(File(filesDir, "verified-tasks-v1.json"))
        val taskToolRegistry = BuiltInTaskToolRegistry(taskRepository)
        val taskEngine = TaskEngine(taskRepository, taskToolRegistry, (application as FutureAiApplication).taskScope)
        val taskPlanSourceFactory = AiTaskPlanSourceFactory(settingsManager, providerRegistry, usageRepository)
        val viewModel = ChatViewModel(
            lifecycleScope,
            provider,
            sessionsRepository = sessionsRepository,
            providerForProfileId = { profileId ->
                val protocol = settingsManager.loadProfile(profileId ?: settingsManager.load().id)?.providerProtocol ?: ProviderProtocol.OPENAI_COMPATIBLE
                providerRegistry.create(protocol) { settingsManager.configFor(profileId) }
            },
            providerForModel = { profileId, modelId ->
                val protocol = settingsManager.loadProfile(profileId)?.providerProtocol ?: ProviderProtocol.OPENAI_COMPATIBLE
                providerRegistry.create(protocol) { settingsManager.configFor(profileId, modelId) }
            },
            defaultModelProfileId = {
                val preferences = appPreferencesManager.load()
                if (preferences.usesAutomaticModelSelection()) {
                    com.tim.futureai.feature.chat.ChatModelChoice.AUTO_ID
                } else settingsManager.load().takeIf { it.isAvailableForRouting }?.id
            },
            usageRepository = usageRepository,
        )
        viewModel.setModelChoices(settingsManager.profiles())
        viewModel.setHistoryLimit(appPreferencesManager.load().historyLimit)
        setContent {
            var appPreferences by remember { mutableStateOf(appPreferencesManager.load()) }
            val systemAnimationsEnabled = remember {
                val motionEnabled = if (Build.VERSION.SDK_INT >= 26) ValueAnimator.areAnimatorsEnabled()
                else Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
                motionEnabled
            }
            val reducedMotion = !appPreferences.animationsEnabled || !systemAnimationsEnabled
            GatherLiquidTheme(
                    appPreferences.visualTheme,
                    appPreferences.fontScale,
                    GlassRenderPrefs(
                        intensity = appPreferences.glassIntensity,
                        lowPerformance = appPreferences.lowPerformanceMode,
                        dynamicBackground = appPreferences.dynamicBackground,
                        reduceMotion = reducedMotion,
                    ),
                ) {
                val lightSystemBars = appPreferences.visualTheme == VisualTheme.ARCTIC
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightSystemBars
                        isAppearanceLightNavigationBars = lightSystemBars
                    }
                }
                val initialSettings = remember { settingsManager.load() }
                var profiles by remember { mutableStateOf(settingsManager.profiles()) }
                var usage by remember { mutableStateOf(UsageSnapshot()) }
                var uiState by remember { mutableStateOf<ChatUiState>(ChatUiState()) }
                var showSettings by remember { mutableStateOf(false) }
                var showTaskCenter by remember { mutableStateOf(false) }
                var settings by remember { mutableStateOf(initialSettings) }
                var settingsOperationMessage by remember { mutableStateOf<String?>(null) }
                var cacheBytes by remember { mutableStateOf(0L) }
                var isAiConfigured by remember { mutableStateOf(initialSettings.isAvailableForRouting) }
                var settingsMessage by remember { mutableStateOf<String?>(null) }
                var settingsMessageIsError by remember { mutableStateOf(false) }
                var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
                var updateChecking by remember { mutableStateOf(false) }
                var updateMessage by remember { mutableStateOf<String?>(null) }
                var updateRepository by remember { mutableStateOf(createUpdateRepository()) }
                val installPrefs = remember { getSharedPreferences(UPDATE_INSTALL_PREFS, MODE_PRIVATE) }
                var downloadedApkPath by remember { mutableStateOf(installPrefs.getString(KEY_UPDATE_APK_PATH, null)) }
                var downloadedApkSha256 by remember { mutableStateOf(installPrefs.getString(KEY_UPDATE_APK_SHA256, null)) }
                var downloadedVersionName by remember { mutableStateOf(installPrefs.getString(KEY_UPDATE_VERSION_NAME, null)) }
                var downloadedVersionCode by remember { mutableStateOf(installPrefs.getInt(KEY_UPDATE_VERSION_CODE, 0)) }
                var installPhase by remember { mutableStateOf(installPrefs.getInt(KEY_UPDATE_PHASE, INSTALL_PHASE_NONE)) }
                var previousVersionCode by remember { mutableStateOf(installPrefs.getInt(KEY_UPDATE_PREVIOUS_CODE, 0)) }
                var previousLastUpdateTime by remember { mutableStateOf(installPrefs.getLong(KEY_UPDATE_PREVIOUS_TIME, 0L)) }
                var installAuthorizationRequired by remember {
                    mutableStateOf(Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls())
                }
                var legacySecurityHelp by remember { mutableStateOf(false) }
                val downloadedApk = downloadedApkPath?.let(::File)?.takeIf { it.isFile }
                var downloadProgress by remember {
                    mutableStateOf<Int?>(if (downloadedApk != null && !downloadedApkSha256.isNullOrBlank()) 100 else null)
                }
                var downloadJob by remember { mutableStateOf<Job?>(null) }
                var updatePrompt by remember { mutableStateOf<UpdatePromptState?>(null) }
                val updateCheckGate = remember { UpdateCheckGate() }
                val startupUpdateCheck = remember { StartupUpdateCheck() }
                var speakingMessageKey by remember { mutableStateOf<String?>(null) }
                val composeScope = rememberCoroutineScope()
                val taskHistory by taskEngine.tasks.collectAsState()
                val screenTransitionMillis = if (appPreferences.animationsEnabled && systemAnimationsEnabled) 180 else 0
                // System back / edge swipe: leave settings or task center instead of closing the app.
                BackHandler(enabled = showSettings || showTaskCenter) { showSettings = false; showTaskCenter = false }
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
                LaunchedEffect(showSettings) {
                    if (showSettings) cacheBytes = withContext(Dispatchers.IO) {
                        cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    }
                }
                LaunchedEffect(taskEngine) {
                    runCatching { taskEngine.load(appPreferencesManager.load().toTaskEnginePolicy()) }
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
                fun readInstalledVersionCode(): Int {
                    val info = packageManager.getPackageInfo(packageName, 0)
                    return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
                }
                fun readInstalledLastUpdateTime(): Long = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
                fun currentDownloadedApk(): File? = installPrefs.getString(KEY_UPDATE_APK_PATH, null)
                    ?.let(::File)?.takeIf { it.isFile }
                fun currentDownloadedSha256(): String? = installPrefs.getString(KEY_UPDATE_APK_SHA256, null)
                fun clearPendingInstallPhase() {
                    installPhase = INSTALL_PHASE_NONE
                    installPrefs.edit()
                        .putInt(KEY_UPDATE_PHASE, INSTALL_PHASE_NONE)
                        .remove(KEY_UPDATE_PREVIOUS_CODE)
                        .remove(KEY_UPDATE_PREVIOUS_TIME)
                        .commit()
                }
                fun clearDownloadedApk(deleteFile: Boolean) {
                    if (deleteFile) currentDownloadedApk()?.delete()
                    downloadedApkPath = null
                    downloadedApkSha256 = null
                    downloadedVersionName = null
                    downloadedVersionCode = 0
                    downloadProgress = null
                    installPrefs.edit()
                        .remove(KEY_UPDATE_APK_PATH)
                        .remove(KEY_UPDATE_APK_SHA256)
                        .remove(KEY_UPDATE_VERSION_NAME)
                        .remove(KEY_UPDATE_VERSION_CODE)
                        .apply()
                }
                val installerResultLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    val installedCode = runCatching { readInstalledVersionCode() }.getOrDefault(0)
                    val installedTime = runCatching { readInstalledLastUpdateTime() }.getOrDefault(0L)
                    val targetCode = installPrefs.getInt(KEY_UPDATE_VERSION_CODE, downloadedVersionCode)
                    val installSucceeded = UpdateInstallFlow.installerReportedSuccess(result.resultCode, installedCode, targetCode) ||
                        UpdateInstallFlow.completedDuringProcessRestart(
                            installedCode, targetCode, previousVersionCode, previousLastUpdateTime, installedTime,
                        )
                    if (installSucceeded) {
                        val installedName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
                        val targetName = installPrefs.getString(KEY_UPDATE_VERSION_NAME, downloadedVersionName).orEmpty()
                        clearPendingInstallPhase()
                        clearDownloadedApk(deleteFile = true)
                        legacySecurityHelp = false
                        installAuthorizationRequired = false
                        showSettings = true
                        updateMessage = "系统已完成安装（${installedName ?: targetName}），正在核对版本…"
                        updateRepository = createUpdateRepository()
                        checkForUpdates()
                    } else {
                        clearPendingInstallPhase()
                        if (Build.VERSION.SDK_INT < 26) legacySecurityHelp = true
                        updateMessage = if (Build.VERSION.SDK_INT < 26) {
                            "系统安装未完成。Android 7.1 及以下可能需要在安全设置允许未知来源；返回后会自动继续。"
                        } else {
                            "系统安装未完成或已取消；已保留已校验 APK，可立即重试。"
                        }
                    }
                }
                fun launchVerifiedInstaller() {
                    val apk = currentDownloadedApk()
                    val expectedSha256 = currentDownloadedSha256()
                    if (apk == null || expectedSha256.isNullOrBlank()) {
                        clearPendingInstallPhase()
                        updateMessage = "已校验的 APK 不可用，请重新下载"
                        downloadProgress = null
                        return
                    }
                    composeScope.launch {
                        if (!updateRepository.isVerifiedApk(this@MainActivity, apk, expectedSha256)) {
                            clearPendingInstallPhase()
                            clearDownloadedApk(deleteFile = true)
                            updateMessage = "APK 校验失败，已阻止安装；请重新下载"
                            return@launch
                        }
                        try {
                            val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", apk)
                            @Suppress("DEPRECATION")
                            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                                .setDataAndType(uri, "application/vnd.android.package-archive")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                .putExtra(Intent.EXTRA_RETURN_RESULT, true)
                            installIntent.clipData = ClipData.newRawUri("Gather AI update", uri)
                            installPhase = INSTALL_PHASE_SYSTEM_UI
                            installPrefs.edit().putInt(KEY_UPDATE_PHASE, INSTALL_PHASE_SYSTEM_UI).commit()
                            try {
                                installerResultLauncher.launch(installIntent)
                            } catch (_: ActivityNotFoundException) {
                                @Suppress("DEPRECATION")
                                val fallback = Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(uri, "application/vnd.android.package-archive")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    .putExtra(Intent.EXTRA_RETURN_RESULT, true)
                                fallback.clipData = ClipData.newRawUri("Gather AI update", uri)
                                installerResultLauncher.launch(fallback)
                            }
                        } catch (error: Exception) {
                            clearPendingInstallPhase()
                            updateMessage = error.message?.let { "无法打开系统安装器：$it" } ?: "无法打开系统安装器，请重试"
                        }
                    }
                }
                val authorizationSettingsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) {
                    val authorized = Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()
                    installAuthorizationRequired = !authorized && Build.VERSION.SDK_INT >= 26
                    if (UpdateInstallFlow.shouldResumeAfterAuthorization(
                            installPhase == INSTALL_PHASE_AUTHORIZATION,
                            Build.VERSION.SDK_INT,
                            authorized,
                        )
                    ) {
                        updateMessage = "授权已开启，正在打开系统安装确认…"
                        launchVerifiedInstaller()
                    } else {
                        showSettings = true
                        updateMessage = "尚未授权安装未知应用；授权后返回会自动继续安装。"
                    }
                }
                fun requestAuthorizationSettings() {
                    installPhase = INSTALL_PHASE_AUTHORIZATION
                    installPrefs.edit().putInt(KEY_UPDATE_PHASE, INSTALL_PHASE_AUTHORIZATION).commit()
                    showSettings = true
                    updateMessage = if (Build.VERSION.SDK_INT >= 26) {
                        "请在系统页面允许 Gather AI 安装未知应用；返回后将自动打开安装确认。"
                    } else {
                        "请在系统安全设置允许未知来源安装；返回后将自动继续。"
                    }
                    val primary = if (Build.VERSION.SDK_INT >= 26) {
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                    } else {
                        Intent(Settings.ACTION_SECURITY_SETTINGS)
                    }
                    try {
                        authorizationSettingsLauncher.launch(primary)
                    } catch (_: ActivityNotFoundException) {
                        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                        runCatching { authorizationSettingsLauncher.launch(fallback) }
                            .onFailure { updateMessage = "无法打开系统授权设置；请从系统设置手动允许 Gather AI 安装未知应用。" }
                    }
                }
                val installDownloadedApk: () -> Unit = {
                    if (currentDownloadedApk() == null || currentDownloadedSha256().isNullOrBlank()) {
                        updateMessage = "已校验的 APK 不可用，请重新下载"
                        downloadProgress = null
                    } else {
                        previousVersionCode = readInstalledVersionCode()
                        previousLastUpdateTime = readInstalledLastUpdateTime()
                        installPrefs.edit()
                            .putInt(KEY_UPDATE_PREVIOUS_CODE, previousVersionCode)
                            .putLong(KEY_UPDATE_PREVIOUS_TIME, previousLastUpdateTime)
                            .commit()
                        val needsPermission = UpdateInstallFlow.requiresPerSourceAuthorization(
                            Build.VERSION.SDK_INT,
                            Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls(),
                        )
                        when {
                            needsPermission -> {
                                installAuthorizationRequired = true
                                requestAuthorizationSettings()
                            }
                            Build.VERSION.SDK_INT < 26 && legacySecurityHelp -> requestAuthorizationSettings()
                            else -> launchVerifiedInstaller()
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    if (installPhase == INSTALL_PHASE_NONE && appPreferences.startupUpdateCheck && startupUpdateCheck.shouldCheck()) checkForUpdates()
                    when (installPhase) {
                        INSTALL_PHASE_AUTHORIZATION -> {
                            showSettings = true
                            val authorized = Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()
                            installAuthorizationRequired = Build.VERSION.SDK_INT >= 26 && !authorized
                            if (UpdateInstallFlow.shouldResumeAfterAuthorization(true, Build.VERSION.SDK_INT, authorized)) {
                                launchVerifiedInstaller()
                            } else {
                                updateMessage = "请允许 Gather AI 安装未知应用；授权后可返回自动继续。"
                            }
                        }
                        INSTALL_PHASE_SYSTEM_UI -> {
                            val installedCode = runCatching { readInstalledVersionCode() }.getOrDefault(0)
                            val installedTime = runCatching { readInstalledLastUpdateTime() }.getOrDefault(0L)
                            if (UpdateInstallFlow.completedDuringProcessRestart(
                                    installedCode, downloadedVersionCode, previousVersionCode, previousLastUpdateTime, installedTime,
                                )
                            ) {
                                val installedName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
                                clearPendingInstallPhase()
                                clearDownloadedApk(deleteFile = true)
                                showSettings = true
                                updateMessage = "已确认升级到 ${installedName ?: downloadedVersionName.orEmpty()}；正在自动检查更新。"
                                updateRepository = createUpdateRepository()
                                checkForUpdates()
                            } else {
                                clearPendingInstallPhase()
                                showSettings = true
                                updateMessage = "系统安装未完成或已取消；已保留已校验 APK，可立即重试。"
                                checkForUpdates()
                            }
                        }
                    }
                }
                fun startUpdateDownload(release: UpdateRelease, installWhenReady: Boolean) {
                    val asset = release.apk
                    if (asset == null) {
                        updateMessage = "Release 没有可下载的 APK"
                        return
                    }
                    clearPendingInstallPhase()
                    clearDownloadedApk(deleteFile = false)
                    if (installWhenReady) {
                        settings = settingsManager.load()
                        profiles = settingsManager.profiles()
                        isAiConfigured = settingsManager.profiles().any { it.isAvailableForRouting }
                        composeScope.launch { usage = usageRepository.load() }
                        showSettings = true
                    }
                    downloadProgress = 0
                    updateMessage = "正在下载并校验 APK…"
                    downloadJob = composeScope.launch {
                        try {
                            val verifiedApk = updateRepository.downloadAndVerify(this@MainActivity, release) { progress ->
                                composeScope.launch { downloadProgress = progress }
                            }
                            downloadedApkPath = verifiedApk.absolutePath
                            downloadedApkSha256 = asset.sha256
                            downloadedVersionName = release.versionName
                            downloadedVersionCode = release.versionCode
                            installPrefs.edit()
                                .putString(KEY_UPDATE_APK_PATH, downloadedApkPath)
                                .putString(KEY_UPDATE_APK_SHA256, downloadedApkSha256)
                                .putString(KEY_UPDATE_VERSION_NAME, downloadedVersionName)
                                .putInt(KEY_UPDATE_VERSION_CODE, downloadedVersionCode)
                                .commit()
                            downloadProgress = 100
                            updateMessage = "SHA-256 与签名校验通过"
                            if (installWhenReady) installDownloadedApk()
                        } catch (_: CancellationException) {
                            clearDownloadedApk(deleteFile = false)
                            updateMessage = "下载已取消"
                        } catch (error: Exception) {
                            clearDownloadedApk(deleteFile = false)
                            updateMessage = error.message ?: "APK 下载或校验失败，可立即重试"
                            if (installWhenReady) showSettings = true
                        }
                    }
                }
                viewModel.onStateChanged = { uiState = it }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Crossfade(targetState = showSettings || showTaskCenter, animationSpec = tween(screenTransitionMillis), label = "main-screen-transition") { showingNonChat ->
                    if (showingNonChat) {
                    if (showSettings) {
                        FullSettingsCenter(
                            preferences = appPreferences,
                            onPreferencesChanged = { next ->
                                val normalized = next.normalized()
                                if (appPreferencesManager.save(normalized)) {
                                    appPreferences = normalized
                                    viewModel.setHistoryLimit(normalized.historyLimit)
                                    viewModel.setModelChoices(profiles)
                                    settingsOperationMessage = null
                                } else settingsOperationMessage = "设置保存失败，现有设置保持不变"
                            },
                            versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty(),
                            versionCode = if (Build.VERSION.SDK_INT >= 28) packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt() else packageManager.getPackageInfo(packageName, 0).versionCode,
                            buildInfo = "Debug · Android ${Build.VERSION.SDK_INT}",
                            defaultModel = if (settings.isConfigured) "${settings.name} · ${settings.model}" else "未配置",
                            updateStatus = when {
                                updateChecking -> "正在检查更新"
                                downloadProgress != null && downloadProgress!! < 100 -> "正在下载 $downloadProgress%"
                                downloadProgress == 100 -> "APK 已校验，等待安装"
                                updateResult is UpdateCheckResult.Available -> "发现 ${(updateResult as UpdateCheckResult.Available).release.versionName}"
                                updateResult is UpdateCheckResult.UpToDate -> "已是最新版本"
                                updateResult is UpdateCheckResult.NoInstallableApk -> "Release 缺少可安装 APK"
                                updateResult is UpdateCheckResult.InvalidMetadata -> "版本元数据无效"
                                updateResult is UpdateCheckResult.Failed -> "检查失败，可重试"
                                else -> "尚未检查"
                            },
                            providerCount = profiles.size,
                            enabledProviderCount = profiles.count { it.enabled },
                            profiles = profiles,
                            usage = usage,
                            sessionCount = uiState.sessions.size,
                            taskCount = taskHistory.size,
                            historyBytes = File(filesDir, "chat-sessions.json").takeIf { it.exists() }?.length()
                                ?: File(filesDir, "chat-history.json").length(),
                            cacheBytes = cacheBytes,
                            migrationStatus = settingsManager.migrationStatus(),
                            voiceInputAvailable = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(packageManager) != null,
                            ttsSettingsAvailable = Intent("com.android.settings.TTS_SETTINGS").resolveActivity(packageManager) != null,
                            operationMessage = settingsOperationMessage,
                            latestErrorType = usage.records.lastOrNull()?.errorType,
                            animationsEnabledBySystem = systemAnimationsEnabled,
                            onResetPreferences = {
                                if (appPreferencesManager.reset()) {
                                    appPreferences = appPreferencesManager.load()
                                    viewModel.setHistoryLimit(appPreferences.historyLimit)
                                    viewModel.setModelChoices(profiles)
                                    settingsOperationMessage = "偏好已重置；Provider、聊天历史与用量保留"
                                } else settingsOperationMessage = "重置失败，现有设置保持不变"
                            },
                            onClearHistory = {
                                settingsOperationMessage = "正在清除聊天历史…"
                                val started = viewModel.clearAllHistory { saved ->
                                    composeScope.launch {
                                        if (!saved) {
                                            settingsOperationMessage = "聊天历史写入失败；请检查存储空间后重试"
                                            return@launch
                                        }
                                        val attachmentsRemoved = withContext(Dispatchers.IO) {
                                            val directory = File(filesDir, "attachments")
                                            !directory.exists() || directory.deleteRecursively()
                                        }
                                        settingsOperationMessage = if (attachmentsRemoved) "聊天历史与引用附件已清除" else "聊天记录已清除，但部分附件文件清理失败"
                                    }
                                }
                                if (!started) settingsOperationMessage = "会话仍在加载，稍后重试"
                            },
                            onClearUsage = {
                                composeScope.launch {
                                    val result = runCatching { usageRepository.clear(); usage = usageRepository.load() }
                                    settingsOperationMessage = if (result.isSuccess) "用量统计已清除" else "清理失败：无法更新统计文件"
                                }
                            },
                            onRefreshUsage = {
                                composeScope.launch {
                                    val result = runCatching { usage = usageRepository.load() }
                                    settingsOperationMessage = if (result.isSuccess) "用量统计已刷新" else "读取用量统计失败"
                                }
                            },
                            onClearCache = {
                                composeScope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        val children = cacheDir.listFiles().orEmpty()
                                        children.all { !it.exists() || it.deleteRecursively() }
                                    }
                                    cacheBytes = withContext(Dispatchers.IO) { cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
                                    settingsOperationMessage = if (result) "临时缓存已清理" else "部分缓存文件清理失败"
                                }
                            },
                            onClearTasks = {
                                composeScope.launch {
                                    val removed = runCatching { taskEngine.clearFinished() }
                                    settingsOperationMessage = removed.fold(
                                        onSuccess = { "已清理 $it 个已结束任务；活动任务保留" },
                                        onFailure = { "清理任务历史失败：无法安全更新任务文件" },
                                    )
                                }
                            },
                            onCopyVersion = {
                                val versionInfo = packageManager.getPackageInfo(packageName, 0)
                                val installedCode = if (Build.VERSION.SDK_INT >= 28) versionInfo.longVersionCode else versionInfo.versionCode.toLong()
                                val text = "Gather AI ${versionInfo.versionName} (versionCode $installedCode) · debug"
                                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("版本信息", text))
                                settingsOperationMessage = "版本信息已复制"
                            },
                            onCopyDiagnostics = {
                                val installedInfo = packageManager.getPackageInfo(packageName, 0)
                                val installedCode = if (Build.VERSION.SDK_INT >= 28) installedInfo.longVersionCode else installedInfo.versionCode.toLong()
                                val text = "Gather AI ${installedInfo.versionName}; versionCode=$installedCode; build=debug; providers=${profiles.size}; enabled=${profiles.count { it.enabled }}; sessions=${uiState.sessions.size}; update=${when (updateResult) { is UpdateCheckResult.Available -> "available"; UpdateCheckResult.UpToDate -> "up-to-date"; is UpdateCheckResult.Failed -> "failed"; else -> "unknown" }}"
                                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("Gather AI diagnostics", text))
                                settingsOperationMessage = "脱敏诊断信息已复制（不含密钥、Base URL 或请求正文）"
                            },
                            onOpenGitHub = { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yeyeqiguai-create/Gather-AI"))) } },
                            onRequestVoiceInput = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出要发送的内容")
                                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                                }
                                runCatching { speechInput.launch(intent) }.onFailure { settingsOperationMessage = "语音服务启动失败；请直接使用文字输入" }
                            },
                            onOpenTtsSettings = {
                                runCatching { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                                    .onFailure { settingsOperationMessage = "系统未能打开 TTS 设置；当前仍使用系统默认引擎" }
                            },
                            onBack = { showSettings = false },
                            providerContent = { providerBack ->
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
                                        isAiConfigured = settingsManager.profiles().any { it.isAvailableForRouting }
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
                            onBack = providerBack,
                            messageIsError = settingsMessageIsError,
                            updateResult = updateResult,
                            updateChecking = updateChecking,
                            updateMessage = updateMessage,
                            downloadProgress = downloadProgress,
                            installUpdateButtonText = when {
                                installAuthorizationRequired -> "打开安装授权"
                                Build.VERSION.SDK_INT < 26 && legacySecurityHelp -> "打开安全设置并继续"
                                else -> "系统确认安装"
                            },
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
                                isAiConfigured = settingsManager.profiles().any { it.isAvailableForRouting }
                                settingsMessage = null
                            },
                            onCreateProfile = {
                                settings = AiSettings(
                                    id = UUID.randomUUID().toString(), name = "新配置",
                                )
                                settingsMessage = null
                            },
                            onDeleteProfile = { id ->
                                if (settingsManager.deleteProfile(id)) {
                                    profiles = settingsManager.profiles()
                                    settings = settingsManager.load()
                                    isAiConfigured = settingsManager.profiles().any { it.isAvailableForRouting }
                                    viewModel.setModelChoices(profiles)
                                    settingsMessage = "配置已删除"
                                    settingsMessageIsError = false
                                }
                            },
                            onTestConnection = {
                                val saved = settingsManager.configFor(settings.id, settings.model, allowDisabled = true)
                                val testConfig = AiChatConfig(
                                    baseUrl = settings.baseUrl,
                                    model = settings.model,
                                    apiKey = settings.apiKey.ifBlank { saved?.apiKey.orEmpty() },
                                )
                                settingsMessage = "正在测试连接…"
                                settingsMessageIsError = false
                                composeScope.launch {
                                    when (val result = providerRegistry.create(settings.providerProtocol) { testConfig }.testConnection()) {
                                        is com.tim.futureai.core.common.AppResult.Success -> {
                                            settingsMessage = "连接成功，服务商可访问"
                                            settingsManager.recordConnectionTest(settings.id, settingsMessage.orEmpty())
                                            profiles = settingsManager.profiles()
                                            settingsMessageIsError = false
                                        }
                                        is com.tim.futureai.core.common.AppResult.Failure -> {
                                            settingsMessage = ChatViewModel.errorToUserMessage(result.error)
                                            settingsManager.recordConnectionTest(settings.id, settingsMessage.orEmpty())
                                            profiles = settingsManager.profiles()
                                            settingsMessageIsError = true
                                        }
                                    }
                                }
                            },
                            usage = usage,
                            modifier = Modifier.fillMaxSize(),
                            includeUsageAndUpdates = false,
                            sessionReferences = uiState.sessions.mapNotNull { it.modelProfileId }.groupingBy { it }.eachCount().toMutableMap().apply {
                                val activeProfileId = settingsManager.load().id
                                this[activeProfileId] = (this[activeProfileId] ?: 0) + uiState.sessions.count { it.modelProfileId == null }
                            },
                        )
                            },
                            usageContent = { filteredUsage ->
                                UsageCard(filteredUsage, profiles, uiState.sessions.associate { it.id to it.title })
                            },
                            updatesContent = {
                                UpdateSection(
                                    result = updateResult, checking = updateChecking, message = updateMessage,
                                    downloadProgress = downloadProgress, onCheck = checkForUpdates,
                                    onDownload = { (updateResult as? UpdateCheckResult.Available)?.let { startUpdateDownload(it.release, installWhenReady = false) } },
                                    onCancel = { downloadJob?.cancel() }, onInstall = installDownloadedApk,
                                    installButtonText = if (installAuthorizationRequired) "打开安装授权" else "系统确认安装",
                                    onViewNotes = { (updateResult as? UpdateCheckResult.Available)?.let { updatePrompt = UpdatePromptState(it.release, showFullNotes = true) } },
                                )
                            },
                        )
                    } else TaskCenterScreen(
                        engine = taskEngine,
                        policy = appPreferences.toTaskEnginePolicy(),
                        onBack = { showTaskCenter = false },
                        onPlan = { goal, policy ->
                            val requestedModel = uiState.nextMessageModelId ?: uiState.selectedModelId
                            val selectedModelId = requestedModel?.takeUnless { it == com.tim.futureai.feature.chat.ChatModelChoice.AUTO_ID }
                            val source = taskPlanSourceFactory.create(
                                goal = goal,
                                requestedModelId = selectedModelId,
                                automaticByDefault = appPreferences.usesAutomaticModelSelection() ||
                                    requestedModel == com.tim.futureai.feature.chat.ChatModelChoice.AUTO_ID,
                            )
                            taskEngine.plan(goal, source, policy)
                        },
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
                        onNextMessageModelSelected = viewModel::selectModelForNextMessage,
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
                            if (!appPreferences.voiceInputEnabled) {
                                viewModel.showNotice("语音输入已在设置中关闭")
                            } else {
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
                            }
                        },
                        onSpeak = { key, text ->
                            if (speakingMessageKey == key) speechController.stop()
                            else if (!appPreferences.textToSpeechEnabled) viewModel.showNotice("文字转语音已在设置中关闭")
                            else speechController.speak(key, text)
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
                            showTaskCenter = false
                            settings = settingsManager.load()
                            profiles = settingsManager.profiles()
                            composeScope.launch { usage = usageRepository.load() }
                            isAiConfigured = settingsManager.profiles().any { it.isAvailableForRouting }
                            settingsMessage = null
                            showSettings = true
                        },
                        onTaskCenterClick = { showSettings = false; showTaskCenter = true },
                        isAiConfigured = isAiConfigured,
                        showModelNames = appPreferences.showModelNames,
                        showRoutingReason = appPreferences.showRoutingReason,
                        compactSpacing = appPreferences.compactChat,
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
