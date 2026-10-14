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
import com.tim.futureai.feature.chat.ChatScreen
import com.tim.futureai.feature.chat.ChatUiState

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
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) TechDarkColors else TechLightColors,
            ) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var state by remember { mutableStateOf(ChatUiState()) }
                    ChatScreen(
                        state = state,
                        onInputChanged = { text -> state = state.updateInput(text) },
                        onSend = { state = state.send() },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
