package com.tim.futureai.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import com.tim.futureai.ui.theme.ContinuousCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.futureai.ui.liquid.LocalLiquidSpec

/** First-run guide: three fields, a real connection test, then save or skip. */
@Composable
fun OnboardingScreen(
    onTest: (String, String, String, (String) -> Unit) -> Unit,
    onFinish: (String, String, String) -> String?,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = LocalLiquidSpec.current
    var baseUrl by remember { mutableStateOf("https://") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val valid = runCatching { java.net.URI(baseUrl.trim()) }.getOrNull()?.let { it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() } == true && model.isNotBlank() && apiKey.isNotBlank()
    Column(
        modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(spec.ambientTop, spec.ambientMiddle, spec.ambientBottom)))
            .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("一个好想法，\n从对话开始。", color = spec.text, fontSize = 32.sp, lineHeight = 41.sp, fontWeight = FontWeight.SemiBold)
        Text("欢迎使用 Gather AI。连接你的 AI 服务，或先看看界面。", color = spec.muted, fontSize = 15.sp, lineHeight = 24.sp)
        Field("API Base URL", baseUrl, { baseUrl = it }, "https://api.example.com/v1")
        Field("模型名称", model, { model = it }, "例如 gpt-4o-mini")
        Field("API Key", apiKey, { apiKey = it }, "sk-…", secret = true)
        message?.let { Text(it, color = if (it.startsWith("连接成功")) spec.success else spec.danger, fontSize = 11.sp, lineHeight = 16.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = {
                testing = true
                message = null
                onTest(baseUrl.trim(), model.trim(), apiKey.trim()) { result ->
                    testing = false
                    message = result
                }
            }, enabled = !testing && valid) { Text(if (testing) "测试中…" else "测试连接", color = spec.cyan) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSkip) { Text("先体验界面", color = spec.muted) }
        }
        Button(
            onClick = { message = onFinish(baseUrl.trim(), model.trim(), apiKey.trim()) },
            enabled = valid && !testing,
            modifier = Modifier.fillMaxWidth(),
            shape = ContinuousCornerShape(14.dp),
        ) { Text("保存并开始使用") }
        Text("密钥通过 Android Keystore 加密保存在本机。也可稍后在设置中添加 Anthropic、Gemini 或本地模型。", color = spec.muted, fontSize = 12.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, hint: String, secret: Boolean = false) {
    val spec = LocalLiquidSpec.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = spec.muted, fontSize = 13.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text),
            textStyle = TextStyle(color = spec.text, fontSize = 15.sp),
            modifier = Modifier.fillMaxWidth().background(spec.surfaceTint.copy(alpha = 0.55f), ContinuousCornerShape(11.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp),
            decorationBox = { inner ->
                if (value.isBlank()) Text(hint, color = spec.muted.copy(alpha = 0.7f), fontSize = 12.sp)
                inner()
            },
        )
    }
}
