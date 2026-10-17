package com.tim.futureai.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onFinish: (String, String, String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = LocalLiquidSpec.current
    var baseUrl by remember { mutableStateOf("https://") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(spec.ambientTop, spec.ambientMiddle, spec.ambientBottom)))
            .statusBarsPadding().padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("欢迎使用 Gather AI", color = spec.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("先连接一个 OpenAI-compatible 服务商；密钥只保存在本机 Keystore 加密存储。", color = spec.muted, fontSize = 12.sp, lineHeight = 18.sp)
        Field("API Base URL", baseUrl, { baseUrl = it }, "https://api.example.com/v1")
        Field("模型名称", model, { model = it }, "例如 gpt-4o-mini")
        Field("API Key", apiKey, { apiKey = it }, "sk-…")
        message?.let { Text(it, color = if (it.startsWith("连接成功")) spec.success else spec.danger, fontSize = 11.sp, lineHeight = 16.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = {
                testing = true
                message = null
                onTest(baseUrl.trim(), model.trim(), apiKey.trim()) { result ->
                    testing = false
                    message = result
                }
            }, enabled = !testing) { Text(if (testing) "测试中…" else "测试连接", color = spec.cyan) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSkip) { Text("跳过", color = spec.muted) }
        }
        Button(
            onClick = { onFinish(baseUrl.trim(), model.trim(), apiKey.trim()) },
            enabled = baseUrl.startsWith("http") && model.isNotBlank() && apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) { Text("保存并开始使用") }
        Text("稍后也可以在 设置 → AI 服务 中修改。", color = spec.muted, fontSize = 10.sp)
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, hint: String) {
    val spec = LocalLiquidSpec.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = spec.muted, fontSize = 10.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = spec.text, fontSize = 13.sp),
            modifier = Modifier.fillMaxWidth().background(spec.surfaceTint.copy(alpha = 0.55f), RoundedCornerShape(11.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp),
            decorationBox = { inner ->
                if (value.isBlank()) Text(hint, color = spec.muted.copy(alpha = 0.7f), fontSize = 12.sp)
                inner()
            },
        )
    }
}
