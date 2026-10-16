package com.tim.futureai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.tim.futureai.core.security.SecureKeyStore
import com.tim.futureai.data.ai.OpenAiCompatProvider
import com.tim.futureai.domain.ai.AiChatConfig
import com.tim.futureai.feature.chat.ChatScreen
import com.tim.futureai.feature.chat.ChatUiState
import com.tim.futureai.feature.chat.ChatViewModel
import com.tim.futureai.feature.settings.AiSettings
import com.tim.futureai.feature.settings.AiSettingsManager
import com.tim.futureai.feature.settings.AiSettingsScreen
import com.tim.futureai.feature.settings.SettingsSaveResult

// 深色科技风基础配色（后续演进到 core 设计系统）。
private val TechDarkColors = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF311B92),
    onPrimaryContainer = Color(0xFFE0D7FF),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE6E9F0),
    surface = Color(0xFF131722),
    onSurface = Color(0xFFE6E9F0),
    surfaceVariant = Color(0xFF1C2230),
    onSurfaceVariant = Color(0xFFA8B0C0),
)

private val TechLightColors = lightColorScheme(
    primary = Color(0xFF5E35B1),
    background = Color(0xFFF6F7FB),
    surface = Color.White,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
        val viewModel = ChatViewModel(lifecycleScope, provider)

        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) TechDarkColors else TechLightColors,
            ) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var uiState by remember { mutableStateOf<ChatUiState>(ChatUiState()) }
                    var showSettings by remember { mutableStateOf(false) }
                    var settings by remember { mutableStateOf(settingsManager.load()) }
                    var isAiConfigured by remember { mutableStateOf(settingsManager.load().let { it.baseUrl.isNotBlank() && it.model.isNotBlank() && it.apiKey.isNotBlank() }) }
                    var settingsMessage by remember { mutableStateOf<String?>(null) }
                    viewModel.onStateChanged = { uiState = it }
                    if (showSettings) {
                        AiSettingsScreen(
                            settings = settings,
                            message = settingsMessage,
                            onSettingsChanged = { settings = it; settingsMessage = null },
                            onSave = {
                                when (val result = settingsManager.save(settings)) {
                                    SettingsSaveResult.Saved -> {
                                        settingsMessage = "配置已安全保存"
                                        isAiConfigured = true
                                    }
                                    is SettingsSaveResult.Invalid -> settingsMessage = result.message
                                    SettingsSaveResult.StorageError -> settingsMessage = "保存失败，请重试"
                                }
                            },
                            onBack = { showSettings = false },
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else ChatScreen(
                        state = uiState,
                        onInputChanged = viewModel::updateInput,
                        onSend = viewModel::send,
                        onDismissNotice = viewModel::dismissNotice,
                        onSettingsClick = {
                            settings = settingsManager.load()
                            isAiConfigured = settings.baseUrl.isNotBlank() && settings.model.isNotBlank() && settings.apiKey.isNotBlank()
                            settingsMessage = null
                            showSettings = true
                        },
                        isAiConfigured = isAiConfigured,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
