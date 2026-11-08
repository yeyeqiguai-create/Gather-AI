package com.tim.futureai.feature.link

import com.tim.futureai.ui.components.GatherAlertDialog as AlertDialog
import com.tim.futureai.ui.components.GatherFilterChip as FilterChip
import com.tim.futureai.ui.components.GatherButton as Button
import com.tim.futureai.ui.components.GatherCheckBox as Checkbox
import com.tim.futureai.ui.components.GatherDropdownMenu as DropdownMenu
import com.tim.futureai.ui.components.GatherDropdownMenuItem as DropdownMenuItem
import com.tim.futureai.ui.components.GatherIconButton as IconButton
import com.tim.futureai.ui.components.GatherSwitch as Switch
import com.tim.futureai.ui.components.GatherTextButton as TextButton

import com.tim.futureai.ui.liquid.GlassMaterial
import com.tim.futureai.ui.liquid.glassSurface
import com.tim.futureai.ui.liquid.LiquidStage
import com.tim.futureai.ui.theme.glassPanel
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import com.tim.futureai.ui.theme.ContinuousCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.futureai.domain.link.GatherLinkSession
import com.tim.futureai.ui.liquid.LocalGlassPrefs
import com.tim.futureai.ui.liquid.LocalLiquidSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class LinkColors(val panel: Color, val text: Color, val muted: Color, val line: Color, val accent: Color)
@Composable
private fun linkColors() = LocalLiquidSpec.current.let { spec ->
    LinkColors(spec.surfaceTint, spec.text, spec.muted, spec.surfaceEdge, spec.accent)
}

@Composable
fun GatherLinkScreen(session: GatherLinkSession, onBack: () -> Unit, onShareInvite: (String, String) -> Unit, modifier: Modifier = Modifier, updateStatus: String = "尚未检查更新", updateChecking: Boolean = false, onCheckUpdates: () -> Unit = {}) {
    val state by session.state.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("gather-link-preferences", Context.MODE_PRIVATE) }
    val systemReducedMotion = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val appearance = LocalGlassPrefs.current
    val reduceMotion = appearance.reduceMotion || systemReducedMotion
    var keepAwake by remember { mutableStateOf(prefs.getBoolean("keepAwake", true)) }
    var sound by remember { mutableStateOf(prefs.getBoolean("sound", false)) }
    var confirmExit by remember { mutableStateOf(prefs.getBoolean("confirmExit", true)) }
    var settingsOpen by remember { mutableStateOf(false) }
    var exitDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf(0) }
    var fileQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("名称") }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var moreAddresses by remember { mutableStateOf(false) }
    var savingFileId by remember { mutableStateOf<String?>(null) }
    var denied by remember { mutableStateOf(false) }
    var receivedIds by remember { mutableStateOf(state.receivedFiles.map { it.id }.toSet()) }
    val colors = linkColors()
    val matchingShared = state.sharedFiles.filter { file ->
        fileQuery.isBlank() || (file.name + " " + file.mimeType).contains(fileQuery.trim(), ignoreCase = true)
    }
    val visibleShared = when (sortMode) {
        "大小" -> matchingShared.sortedByDescending { it.sizeBytes ?: -1L }
        else -> matchingShared.sortedBy { it.name.lowercase(Locale.ROOT) }
    }
    val matchingReceived = state.receivedFiles.filter { file ->
        fileQuery.isBlank() || file.name.contains(fileQuery.trim(), ignoreCase = true)
    }
    val visibleReceived = when (sortMode) {
        "名称" -> matchingReceived.sortedBy { it.name.lowercase(Locale.ROOT) }
        "大小" -> matchingReceived.sortedByDescending { it.sizeBytes }
        else -> matchingReceived.sortedByDescending { it.receivedAtMs }
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch { session.setSharedFiles((state.sharedFiles.map { it.contentUri } + uris.map { it.toString() }).distinct()) }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val id = savingFileId
        if (id != null && uri != null) scope.launch { session.saveReceivedFile(id, uri.toString()) }
        savingFileId = null
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        denied = !allowed
        if (allowed) scope.launch { session.start() }
    }
    val start: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 37 && context.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED)
            permission.launch("android.permission.ACCESS_LOCAL_NETWORK")
        else scope.launch { session.start() }
    }
    val leave: () -> Unit = { if (state.isActive && confirmExit) exitDialog = true else onBack() }
    BackHandler { leave() }
    DisposableEffect(session) { onDispose { session.stop() } }
    DisposableEffect(keepAwake, state.isActive) {
        val previous = view.keepScreenOn
        view.keepScreenOn = keepAwake && state.isActive
        onDispose { view.keepScreenOn = previous }
    }
    LaunchedEffect(state.receivedFiles, sound) {
        val next = state.receivedFiles.map { it.id }.toSet()
        if (sound && (next - receivedIds).isNotEmpty()) {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 160)
            delay(220)
            tone.release()
        }
        receivedIds = next
    }
    val frameDensity = LocalDensity.current
    var linkHeader by remember { mutableStateOf(88.dp) }
    var linkFooter by remember { mutableStateOf(80.dp) }
    CompositionLocalProvider(LocalLinkReducedMotion provides reduceMotion) {
        LiquidStage(modifier = modifier.imePadding(), source = {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = linkHeader + 12.dp, bottom = linkFooter + 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Text("你的文件，随时流转。", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
                    Text("在手机和 Windows 电脑之间直接传输", fontSize = 12.sp, color = colors.muted, modifier = Modifier.padding(top = 7.dp, bottom = 8.dp))
                }
                item {
                    LinkSurface(colors) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (state.isActive) "电脑连接信息" else "连接到 Windows", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (state.isActive) "在电脑客户端输入地址与配对码" else "两台设备连接同一个 Wi-Fi", color = colors.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                            }
                            if (state.isActive) TextButton(onClick = { session.stop() }) { Text("结束", fontSize = 12.sp) }
                        }
                        if (state.isActive) {
                            Spacer(Modifier.height(19.dp))
                            Text("连接地址", fontSize = 10.sp, color = colors.muted)
                            val shown = if (moreAddresses) state.addresses else state.addresses.take(1)
                            shown.forEach { address ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(address, color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    TextButton(onClick = { copyLink(context, address, "地址") }) { Text("复制", fontSize = 11.sp) }
                                }
                            }
                            if (state.addresses.size > 1) TextButton(onClick = { moreAddresses = !moreAddresses }, contentPadding = PaddingValues(0.dp)) { Text(if (moreAddresses) "收起其他地址" else "显示其他连接地址", fontSize = 11.sp) }
                            HorizontalDivider(color = colors.line, modifier = Modifier.padding(vertical = 9.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("本次配对码", fontSize = 10.sp, color = colors.muted)
                                    Text(state.pairingCode.orEmpty().chunked(3).joinToString(" "), fontSize = 30.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold, color = colors.text, modifier = Modifier.padding(top = 6.dp))
                                }
                                TextButton(onClick = { state.pairingCode?.let { copyLink(context, it, "配对码") } }) { Text("复制", fontSize = 11.sp) }
                            }
                            TextButton(onClick = { state.addresses.firstOrNull()?.let { address -> state.pairingCode?.let { onShareInvite(address, it) } } }, contentPadding = PaddingValues(0.dp)) { Text("分享连接信息  ↗", fontSize = 12.sp) }
                        } else {
                            LiquidButton(onClick = start, modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(46.dp), shape = ContinuousCornerShape(10.dp)) { Text("开启互联", fontWeight = FontWeight.SemiBold) }
                        }
                        if (denied) Text("请允许本地网络权限后再开启互联。", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = activeTab == 0, onClick = { activeTab = 0; fileQuery = ""; sortMode = "名称" }, selectedContainerColor = colors.accent.copy(alpha = 0.14f), selectedLabelColor = colors.accent, label = { Text("共享文件  " + state.sharedFiles.size, fontSize = 12.sp) })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = activeTab == 1, onClick = { activeTab = 1; fileQuery = ""; sortMode = "时间" }, selectedContainerColor = colors.accent.copy(alpha = 0.14f), selectedLabelColor = colors.accent, label = { Text("收件箱  " + state.receivedFiles.size, fontSize = 12.sp) })
                        Spacer(Modifier.weight(1f))
                        if (activeTab == 0 && state.sharedFiles.isNotEmpty()) TextButton(onClick = { scope.launch { session.setSharedFiles(emptyList()) } }) { Text("清空", fontSize = 11.sp) }
                    }
                    Text(if (activeTab == 0) "电脑可以下载你在这里选择的文件" else "电脑发送的文件保存在应用收件箱，可另存到指定位置", color = colors.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp, bottom = 3.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).clip(ContinuousCornerShape(12.dp)).background(colors.panel).border(1.dp, colors.line, ContinuousCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                            BasicTextField(
                                value = fileQuery,
                                onValueChange = { fileQuery = it.take(100) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(color = colors.text, fontSize = 12.sp),
                                decorationBox = { inner -> Box {
                                    if (fileQuery.isBlank()) Text("搜索文件名或类型", color = colors.muted, fontSize = 12.sp)
                                    inner()
                                } },
                            )
                        }
                        Box {
                            TextButton(onClick = { sortMenuOpen = true }) { Text("排序 · $sortMode", fontSize = 11.sp) }
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                (if (activeTab == 0) listOf("名称", "大小") else listOf("时间", "名称", "大小")).forEach { option ->
                                    DropdownMenuItem(text = { Text(option) }, onClick = { sortMode = option; sortMenuOpen = false })
                                }
                            }
                        }
                    }
                    val totalBytes = if (activeTab == 0) state.sharedFiles.mapNotNull { it.sizeBytes }.sum() else state.receivedFiles.sumOf { it.sizeBytes }
                    val totalCount = if (activeTab == 0) state.sharedFiles.size else state.receivedFiles.size
                    Text("显示 ${if (activeTab == 0) visibleShared.size else visibleReceived.size} / $totalCount 项 · 已知大小 ${formatBytes(totalBytes)}", color = colors.muted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                }
                if (activeTab == 0) {
                    if (state.sharedFiles.isEmpty() || visibleShared.isEmpty()) item {
                        LinkSurface(colors) {
                            Text(if (state.sharedFiles.isEmpty()) "还没有共享文件" else "没有找到匹配文件", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(if (state.sharedFiles.isEmpty()) "选择照片、视频或文档；原文件会保留在原处。" else "试试其他名称或文件类型。", color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    items(visibleShared, key = { it.id }) { file ->
                        LinkSurface(colors, if (reduceMotion || systemReducedMotion) Modifier else Modifier.animateItem()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(file.name, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                TextButton(onClick = { scope.launch { session.setSharedFiles(state.sharedFiles.filterNot { it.id == file.id }.map { it.contentUri }) } }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("移除", fontSize = 10.sp) }
                            }
                            Text("${file.sizeBytes?.let(::formatBytes) ?: "大小未知"} · ${file.mimeType}", color = colors.muted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else {
                    if (state.receivedFiles.isEmpty() || visibleReceived.isEmpty()) item {
                        LinkSurface(colors) {
                            Text(if (state.receivedFiles.isEmpty()) "收件箱是空的" else "没有找到匹配文件", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(if (state.receivedFiles.isEmpty()) "在 Windows 客户端选择文件，或把文件拖入上传区域。" else "试试其他文件名。", color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    items(visibleReceived, key = { it.id }) { file ->
                        LinkSurface(colors, if (reduceMotion || systemReducedMotion) Modifier else Modifier.animateItem()) {
                            Text(file.name, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${formatBytes(file.sizeBytes)} · ${formatReceivedAt(file.receivedAtMs)}", color = colors.muted, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { savingFileId = file.id; saveFile.launch(file.name) }) { Text("另存为", fontSize = 11.sp) }
                                TextButton(onClick = { deleteId = file.id }) { Text("删除", fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
                item { Text("本地 HTTP 传输 · 请使用可信网络\n离开此页会结束互联；单个上传文件上限 2 GB。", fontSize = 10.sp, color = colors.muted, lineHeight = 16.sp, modifier = Modifier.padding(vertical = 12.dp)) }
            }
        }, overlay = {
            Column(Modifier.fillMaxWidth().onSizeChanged { linkHeader = with(frameDensity) { it.height.toDp() } }
                .glassSurface(GlassMaterial.FROSTED, RectangleShape).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = leave) { Text("‹", fontSize = 29.sp) }
                Column(Modifier.weight(1f)) {
                    Text("Gather Link", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
                    Crossfade(targetState = state.isActive, animationSpec = if (reduceMotion || systemReducedMotion) snap() else tween(180), label = "link-connection-status") { active ->
                        Text(if (active) "●  互联已开启" else "本地设备互联", fontSize = 11.sp, color = if (active) colors.accent else colors.muted)
                    }
                }
                TextButton(onClick = { settingsOpen = true }) { Text("⚙  设置", fontSize = 13.sp) }
            }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { linkFooter = with(frameDensity) { it.height.toDp() } }
                .glassSurface(GlassMaterial.FROSTED, RectangleShape).navigationBarsPadding()) {
            if (activeTab == 0) LiquidButton(onClick = { pickFiles.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp).height(46.dp), shape = ContinuousCornerShape(10.dp)) { Text("＋  添加共享文件", fontWeight = FontWeight.SemiBold) }
            state.message?.let { Text(it, fontSize = 10.sp, color = colors.muted, modifier = Modifier.padding(horizontal = 22.dp, vertical = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        })
    }
        if (settingsOpen) AlertDialog(containerColor = colors.panel, 
            onDismissRequest = { settingsOpen = false }, title = { Text("互联设置", fontSize = 19.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("外观与动态效果跟随 Gather AI 设置。", fontSize = 11.sp, color = colors.muted)
                    Spacer(Modifier.height(14.dp))
                    LinkSetting("传输时保持屏幕常亮", keepAwake) { keepAwake = it; prefs.edit().putBoolean("keepAwake", it).apply() }
                    LinkSetting("收到文件时提示音", sound) { sound = it; prefs.edit().putBoolean("sound", it).apply() }
                    LinkSetting("离开前确认结束互联", confirmExit) { confirmExit = it; prefs.edit().putBoolean("confirmExit", it).apply() }
                    HorizontalDivider(color = colors.line, modifier = Modifier.padding(vertical = 12.dp))
                    Text("收件箱 · " + state.receivedFiles.size + " 个文件", fontSize = 12.sp)
                    Text("使用“另存为”选择保存目录。清空只删除应用中的收件箱文件。", fontSize = 11.sp, color = colors.muted, modifier = Modifier.padding(top = 6.dp))
                    TextButton(onClick = { clearDialog = true }, enabled = state.receivedFiles.isNotEmpty()) { Text("清空收件箱", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    HorizontalDivider(color = colors.line, modifier = Modifier.padding(vertical = 12.dp))
                    Text("软件更新", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("$updateStatus\n通过 GitHub Release 核对版本和 APK 校验信息；下载后交由系统确认安装。", fontSize = 11.sp, color = colors.muted, modifier = Modifier.padding(top = 6.dp))
                    TextButton(onClick = onCheckUpdates, enabled = !updateChecking) { Text(if (updateChecking) "正在检查…" else "检查更新") }
                }
            }, confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text("完成") } },
        )
        if (exitDialog) AlertDialog(containerColor = colors.panel, onDismissRequest = { exitDialog = false }, title = { Text("结束本次互联？") }, text = { Text("离开后电脑将无法继续传输，未完成的文件需要重新发送。") }, confirmButton = { TextButton(onClick = { exitDialog = false; onBack() }) { Text("结束并离开") } }, dismissButton = { TextButton(onClick = { exitDialog = false }) { Text("继续互联") } })
        if (clearDialog) AlertDialog(containerColor = colors.panel, onDismissRequest = { clearDialog = false }, title = { Text("清空收件箱？") }, text = { Text("将删除应用收件箱内的文件，已经另存的副本不受影响。") }, confirmButton = { TextButton(onClick = { clearDialog = false; scope.launch { state.receivedFiles.forEach { session.deleteReceivedFile(it.id) } } }) { Text("清空") } }, dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("取消") } })
        deleteId?.let { id -> AlertDialog(containerColor = colors.panel, onDismissRequest = { deleteId = null }, title = { Text("删除收件箱文件？") }, text = { Text("已经另存到其他位置的副本会保留。") }, confirmButton = { TextButton(onClick = { deleteId = null; scope.launch { session.deleteReceivedFile(id) } }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleteId = null }) { Text("取消") } }) }
}
@Composable private fun LinkSurface(colors: LinkColors, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val reduce = LocalLinkReducedMotion.current
    val shape = ContinuousCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().animateContentSize(animationSpec = if (reduce) snap() else spring(dampingRatio = 0.90f, stiffness = 360f))
            .glassPanel(shape).padding(16.dp),
        content = content,
    )
}
@Composable private fun LinkSetting(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
private fun copyLink(context: Context, value: String, label: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, value))
}
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return bytes.toString() + " B"
    if (bytes < 1048576) return "%.1f KB".format(bytes / 1024.0)
    if (bytes < 1073741824) return "%.1f MB".format(bytes / 1048576.0)
    return "%.2f GB".format(bytes / 1073741824.0)
}
private fun formatReceivedAt(timestampMs: Long): String =
    if (timestampMs <= 0L) "时间未知" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestampMs))
