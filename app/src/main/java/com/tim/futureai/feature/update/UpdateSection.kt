package com.tim.futureai.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.futureai.ui.theme.FuturePalette
import com.tim.futureai.ui.theme.glassPanel

@Composable
fun UpdateSection(
    result: UpdateCheckResult?, checking: Boolean, message: String?, downloadProgress: Int?,
    onCheck: () -> Unit, onDownload: () -> Unit, onCancel: () -> Unit, onInstall: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(22.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("应用更新", color = FuturePalette.Text, fontSize = 15.sp)
        Text("当前版本：${androidx.compose.ui.platform.LocalContext.current.packageManager.getPackageInfo(androidx.compose.ui.platform.LocalContext.current.packageName, 0).versionName ?: "未知"}", color = FuturePalette.Muted, fontSize = 11.sp)
        when (result) {
            UpdateCheckResult.UpToDate -> Text("当前已是最新版本", color = FuturePalette.Success, fontSize = 12.sp)
            UpdateCheckResult.NoInstallableApk -> Text("发现更新，但 Release 没有可验证的 APK", color = FuturePalette.Danger, fontSize = 12.sp)
            is UpdateCheckResult.InvalidMetadata -> Text(result.message, color = FuturePalette.Danger, fontSize = 12.sp)
            is UpdateCheckResult.Failed -> Text(result.message, color = FuturePalette.Danger, fontSize = 12.sp)
            is UpdateCheckResult.Available -> {
                Text("新版本：${result.release.versionName} · ${result.release.channel.label}", color = FuturePalette.VioletBright, fontSize = 12.sp)
                Text("发布日期：${result.release.publishedAt.take(10).ifBlank { "未知" }}", color = FuturePalette.Muted, fontSize = 10.sp)
                if (result.release.notes.isNotBlank()) Text(result.release.notes.take(1600), color = FuturePalette.Text, fontSize = 11.sp, lineHeight = 16.sp)
            }
            null -> Text(if (checking) "正在检查更新…" else "启动时自动检查；也可手动检查", color = FuturePalette.Muted, fontSize = 11.sp)
        }
        if (downloadProgress != null && downloadProgress < 100) {
            LinearProgressIndicator(progress = { downloadProgress / 100f }, modifier = Modifier.fillMaxWidth())
            Text("下载中 $downloadProgress%", color = FuturePalette.Muted, fontSize = 10.sp)
        }
        if (message != null) Text(message, color = if (message.contains("失败") || message.contains("不兼容")) FuturePalette.Danger else FuturePalette.Muted, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCheck, enabled = !checking && (downloadProgress == null || downloadProgress >= 100), shape = RoundedCornerShape(14.dp)) { Text(if (checking) "检查中…" else "检查更新", fontSize = 12.sp) }
            if (result is UpdateCheckResult.Available && downloadProgress == null) Button(onClick = onDownload, shape = RoundedCornerShape(14.dp)) { Text("下载并校验 APK", fontSize = 12.sp) }
            if (downloadProgress != null && downloadProgress < 100) Button(onClick = onCancel, shape = RoundedCornerShape(14.dp)) { Text("取消", fontSize = 12.sp) }
            if (downloadProgress == 100) Button(onClick = onInstall, shape = RoundedCornerShape(14.dp)) { Text("系统确认安装", fontSize = 12.sp) }
        }
    }
}
