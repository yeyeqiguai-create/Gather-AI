package com.tim.futureai.feature.instructions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.futureai.domain.chat.SystemInstructionTemplate
import com.tim.futureai.ui.theme.FuturePalette
import java.util.UUID

@Composable
fun InstructionTemplateLibraryDialog(
    templates: List<SystemInstructionTemplate>,
    initialInstruction: String = "",
    onApply: (String, Boolean) -> Unit,
    onSave: (SystemInstructionTemplate) -> Boolean,
    onDelete: (String) -> Boolean,
    onImport: (String) -> String,
    onExport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var instruction by remember { mutableStateOf(initialInstruction.take(10_000)) }
    var category by remember { mutableStateOf("通用") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("全部") }
    var error by remember { mutableStateOf<String?>(null) }
    var importText by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("系统指令模板") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("模板保存在本机加密存储中。", color = FuturePalette.Muted, fontSize = 11.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { showImport = true }) { Text("导入 JSON") }
                    TextButton(onClick = { if (templates.isNotEmpty()) onExport(com.tim.futureai.data.chat.SystemInstructionLibraryStore.exportPortable(templates)) }, enabled = templates.isNotEmpty()) { Text("复制导出 JSON") }
                }
                val categories = listOf("全部", "收藏") + templates.map { it.category }.distinct().sorted()
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    categories.forEach { item ->
                        TextButton(onClick = { selectedCategory = item }) {
                            Text(if (selectedCategory == item) "• $item" else item, color = if (selectedCategory == item) FuturePalette.Cyan else FuturePalette.Muted, fontSize = 10.sp)
                        }
                    }
                }
                if (showImport) {
                    OutlinedTextField(importText, { importText = it.take(com.tim.futureai.data.chat.SystemInstructionLibraryStore.MAX_IMPORT_BYTES) }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, label = { Text("粘贴模板 JSON") })
                    TextButton(enabled = importText.isNotBlank(), onClick = { error = onImport(importText) }) { Text("校验并导入") }
                }
                if (templates.size > 4) OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("搜索模板") })
                templates.filter { item ->
                    (selectedCategory == "全部" || (selectedCategory == "收藏" && item.isFavorite) || item.category == selectedCategory) &&
                        (query.isBlank() || item.title.contains(query, true) || item.instruction.contains(query, true) || item.category.contains(query, true))
                }.sortedWith(compareByDescending<SystemInstructionTemplate> { it.isFavorite }.thenBy { it.category }.thenBy { it.title }).forEach { item ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.title} · ${item.category}", color = FuturePalette.Text, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onSave(item.copy(isFavorite = !item.isFavorite)) }) { Text(if (item.isFavorite) "★" else "☆", color = FuturePalette.Cyan) }
                        }
                        Text(item.instruction, color = FuturePalette.Muted, fontSize = 10.sp, maxLines = 3)
                        Row {
                            TextButton(onClick = { onApply(item.instruction, false); onDismiss() }) { Text("覆盖") }
                            TextButton(onClick = { onApply(item.instruction, true); onDismiss() }) { Text("追加") }
                        TextButton(onClick = { editingId = item.id; title = item.title; instruction = item.instruction; category = item.category; error = null }) { Text("编辑") }
                            TextButton(onClick = { if (!onDelete(item.id)) error = "删除失败，请重试" }) { Text("删除", color = FuturePalette.Danger) }
                        }
                    }
                }
                if (templates.isEmpty()) Text("还没有保存的模板。", color = FuturePalette.Muted, fontSize = 11.sp)
                Text(if (editingId == null) "保存当前指令为模板" else "编辑模板", color = FuturePalette.Text, fontSize = 12.sp)
                OutlinedTextField(title, { title = it.take(48) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("名称") })
                OutlinedTextField(category, { category = it.take(com.tim.futureai.data.chat.SystemInstructionLibraryStore.MAX_CATEGORY) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("分类") })
                OutlinedTextField(instruction, { instruction = it.take(10_000) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6, label = { Text("系统指令") })
                Text("${instruction.length} / 10000 字符", color = FuturePalette.Muted, fontSize = 10.sp)
                error?.let { Text(it, color = FuturePalette.Danger, fontSize = 10.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && instruction.isNotBlank(), onClick = {
                val id = editingId ?: UUID.randomUUID().toString()
                val existing = templates.firstOrNull { it.id == id }
                if (onSave(SystemInstructionTemplate(id, title, instruction, category, existing?.isFavorite ?: false))) {
                    title = ""; instruction = ""; category = "通用"; editingId = null; error = null
                } else error = "保存失败或模板数量已达上限"
            }) { Text(if (editingId == null) "保存模板" else "保存修改") }
        },
        dismissButton = {
            Row {
                if (editingId != null) TextButton(onClick = { editingId = null; title = ""; instruction = ""; category = "通用"; error = null }) { Text("取消编辑") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}
