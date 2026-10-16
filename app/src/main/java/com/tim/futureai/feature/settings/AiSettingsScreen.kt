package com.tim.futureai.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    settings: AiSettings,
    message: String?,
    onSettingsChanged: (AiSettings) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("AI 服务配置") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("返回") }
        }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("配置 OpenAI 兼容服务的连接信息。API Key 会使用 Android Keystore 加密保存。", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = settings.baseUrl,
                onValueChange = { onSettingsChanged(settings.copy(baseUrl = it)) },
                label = { Text("API Base URL") },
                placeholder = { Text("https://api.openai.com/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = settings.model,
                onValueChange = { onSettingsChanged(settings.copy(model = it)) },
                label = { Text("Model") },
                placeholder = { Text("gpt-4o-mini") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = settings.apiKey,
                onValueChange = { onSettingsChanged(settings.copy(apiKey = it)) },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (message != null) Text(message, color = MaterialTheme.colorScheme.primary)
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("保存配置") }
        }
    }
}
