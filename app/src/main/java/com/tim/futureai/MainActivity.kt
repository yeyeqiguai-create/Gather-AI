package com.tim.futureai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.tim.futureai.core.security.SecureKeyStore
import com.tim.futureai.data.ai.OpenAiCompatProvider
import com.tim.futureai.data.chat.FileChatHistoryRepository
import com.tim.futureai.domain.ai.AiChatConfig
import com.tim.futureai.feature.chat.ChatScreen
import com.tim.futureai.feature.chat.ChatUiState
import com.tim.futureai.feature.chat.ChatViewModel
import com.tim.futureai.feature.settings.AiSettingsManager
import com.tim.futureai.feature.settings.AiSettingsScreen
import com.tim.futureai.feature.settings.SettingsSaveResult
import com.tim.futureai.ui.theme.FuturePalette
import java.io.File

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
        val provider = OpenAiCompatProvider(configProvider = {
            val config = AiChatConfig(
                baseUrl = keyStore.getString(SecureKeyStore.KEY_BASE_URL).orEmpty(),
                model = keyStore.getString(SecureKeyStore.KEY_MODEL).orEmpty(),
                apiKey = keyStore.getString(SecureKeyStore.KEY_API).orEmpty(),
            )
            if (config.isConfigured()) config else null
        })
        val historyRepository = FileChatHistoryRepository(File(filesDir, "chat-history.json"))
        val viewModel = ChatViewModel(lifecycleScope, provider, historyRepository = historyRepository)

        setContent {
            MaterialTheme(colorScheme = FutureColors) {
                val initialSettings = remember { settingsManager.load() }
                var uiState by remember { mutableStateOf<ChatUiState>(ChatUiState()) }
                var showSettings by remember { mutableStateOf(false) }
                var settings by remember { mutableStateOf(initialSettings) }
                var isAiConfigured by remember { mutableStateOf(initialSettings.isConfigured) }
                var settingsMessage by remember { mutableStateOf<String?>(null) }
                var settingsMessageIsError by remember { mutableStateOf(false) }
                viewModel.onStateChanged = { uiState = it }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (showSettings) {
                        AiSettingsScreen(
                            settings = settings,
                            message = settingsMessage,
                            onSettingsChanged = { settings = it; settingsMessage = null },
                            onSave = {
                                when (val result = settingsManager.save(settings)) {
                                    SettingsSaveResult.Saved -> {
                                        settings = settings.copy(apiKey = "", hasSavedApiKey = true)
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
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else ChatScreen(
                        state = uiState,
                        onInputChanged = viewModel::updateInput,
                        onSend = viewModel::send,
                        onDismissNotice = viewModel::dismissNotice,
                        onCancel = viewModel::cancel,
                        onExamplePrompt = viewModel::updateInput,
                        onSettingsClick = {
                            settings = settingsManager.load()
                            isAiConfigured = settings.isConfigured
                            settingsMessage = null
                            showSettings = true
                        },
                        isAiConfigured = isAiConfigured,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
