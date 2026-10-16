package com.tim.futureai.feature.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.futureai.ui.theme.FuturePalette
import com.tim.futureai.ui.theme.glassPanel

data class UpdateSectionControls(
    val checkEnabled: Boolean,
    val downloadEnabled: Boolean,
    val cancelEnabled: Boolean,
    val installEnabled: Boolean,
) {
    companion object {
        fun forState(result: UpdateCheckResult?, checking: Boolean, progress: Int?) = UpdateSectionControls(
            checkEnabled = !checking,
            downloadEnabled = result is UpdateCheckResult.Available && progress == null,
            cancelEnabled = progress != null && progress < 100,
            installEnabled = progress == 100,
        )
    }
}

@Composable
fun UpdateSection(
    result: UpdateCheckResult?, checking: Boolean, message: String?, downloadProgress: Int?,
    onCheck: () -> Unit, onDownload: () -> Unit, onCancel: () -> Unit, onInstall: () -> Unit,
    onViewNotes: () -> Unit = {},
) {
    val controls = UpdateSectionControls.forState(result, checking, downloadProgress)
    Column(Modifier.fillMaxWidth().animateContentSize().glassPanel(RoundedCornerShape(22.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("应用更新", color = FuturePalette.Text, fontSize = 15.sp)
        Text("当前版本：${androidx.compose.ui.platform.LocalContext.current.packageManager.getPackageInfo(androidx.compose.ui.platform.LocalContext.current.packageName, 0).versionName ?: "未知"}", color = FuturePalette.Muted, fontSize = 11.sp)
        AnimatedContent(targetState = result, label = "update-status-transition") { state -> when (state) {
            UpdateCheckResult.UpToDate -> Text("当前已是最新版本", color = FuturePalette.Success, fontSize = 12.sp)
            UpdateCheckResult.NoInstallableApk -> Text("发现更新，但 Release 没有可验证的 APK", color = FuturePalette.Danger, fontSize = 12.sp)
            is UpdateCheckResult.InvalidMetadata -> Text(state.message, color = FuturePalette.Danger, fontSize = 12.sp)
            is UpdateCheckResult.Failed -> Text(state.message, color = FuturePalette.Danger, fontSize = 12.sp)
            is UpdateCheckResult.Available -> {
                Text("新版本：${state.release.versionName} · ${state.release.channel.label}", color = FuturePalette.VioletBright, fontSize = 12.sp)
                Text("发布日期：${state.release.publishedAt.take(10).ifBlank { "未知" }}", color = FuturePalette.Muted, fontSize = 10.sp)
                if (state.release.notes.isNotBlank()) Text(state.release.notes.take(1600), color = FuturePalette.Text, fontSize = 11.sp, lineHeight = 16.sp)
                TextButton(onClick = onViewNotes) { Text("查看完整更新说明", color = FuturePalette.Cyan, fontSize = 11.sp) }
            }
            null -> Text(if (checking) "正在检查更新…" else "启动时自动检查；也可手动检查", color = FuturePalette.Muted, fontSize = 11.sp)
        } }
        if (downloadProgress != null && downloadProgress < 100) {
            val animatedProgress by animateFloatAsState(downloadProgress / 100f, label = "apk-download-progress")
            LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())
            Text("${if (downloadProgress == 0) "等待下载" else "下载中"} $downloadProgress%", color = FuturePalette.Muted, fontSize = 10.sp)
        }
        if (message != null) Text(message, color = if (message.contains("失败") || message.contains("不兼容")) FuturePalette.Danger else FuturePalette.Muted, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCheck, enabled = controls.checkEnabled, shape = RoundedCornerShape(14.dp)) { Text(if (checking) "检查中…" else "检查更新", fontSize = 12.sp) }
            if (controls.downloadEnabled) Button(onClick = onDownload, shape = RoundedCornerShape(14.dp)) { Text("下载并校验 APK", fontSize = 12.sp) }
            if (controls.cancelEnabled) Button(onClick = onCancel, shape = RoundedCornerShape(14.dp)) { Text("取消", fontSize = 12.sp) }
            if (controls.installEnabled) Button(onClick = onInstall, shape = RoundedCornerShape(14.dp)) { Text("系统确认安装", fontSize = 12.sp) }
        }
    }
}
