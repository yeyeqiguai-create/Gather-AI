package com.tim.futureai.data.chat

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.security.SecureValueStore
import com.tim.futureai.domain.chat.SystemInstructionTemplate

/** User-authored system instructions are encrypted at rest with the other sensitive app settings. */
class SystemInstructionLibraryStore(private val store: SecureValueStore) {
    fun load(): List<SystemInstructionTemplate> = runCatching {
        JsonMini.parse(store.getString(KEY).orEmpty()).asArray()?.items.orEmpty().mapNotNull { value ->
            val fields = value.asObject()?.entries ?: return@mapNotNull null
            val id = fields["id"]?.asString()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val title = fields["title"]?.asString()?.trim()?.take(MAX_TITLE)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val instruction = fields["instruction"]?.asString()?.trim()?.take(MAX_INSTRUCTION)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val category = fields["category"]?.asString()?.trim()?.take(MAX_CATEGORY).orEmpty().ifBlank { "通用" }
            val favorite = (fields["isFavorite"] as? com.tim.futureai.core.common.JsonValue.JBool)?.value ?: false
            SystemInstructionTemplate(id, title, instruction, category, favorite)
        }.distinctBy { it.id }.take(MAX_COUNT)
    }.getOrDefault(emptyList())

    fun save(templates: List<SystemInstructionTemplate>): Boolean {
        val normalized = templates.asSequence().filter { it.id.isNotBlank() && it.title.isNotBlank() && it.instruction.isNotBlank() }
            .distinctBy { it.id }.take(MAX_COUNT).map {
                it.copy(title = it.title.trim().take(MAX_TITLE), instruction = it.instruction.trim().take(MAX_INSTRUCTION), category = it.category.trim().take(MAX_CATEGORY).ifBlank { "通用" })
            }.filter { it.title.isNotBlank() && it.instruction.isNotBlank() }.toList()
        val json = normalized.joinToString(",", prefix = "[", postfix = "]") {
            "{\"id\":${JsonMini.quote(it.id)},\"title\":${JsonMini.quote(it.title)},\"instruction\":${JsonMini.quote(it.instruction)},\"category\":${JsonMini.quote(it.category)},\"isFavorite\":${it.isFavorite}}"
        }
        return store.putString(KEY, json)
    }

    companion object {
        const val KEY = "system_instruction_library_v1"
        const val MAX_COUNT = 100
        const val MAX_TITLE = 48
        const val MAX_INSTRUCTION = 10_000
        const val MAX_CATEGORY = 24
        const val MAX_IMPORT_BYTES = 1_000_000

        fun exportPortable(templates: List<SystemInstructionTemplate>): String {
            val items = templates.take(MAX_COUNT).joinToString(",") {
                "{\"id\":${JsonMini.quote(it.id)},\"title\":${JsonMini.quote(it.title.take(MAX_TITLE))},\"instruction\":${JsonMini.quote(it.instruction.take(MAX_INSTRUCTION))},\"category\":${JsonMini.quote(it.category.take(MAX_CATEGORY))},\"isFavorite\":${it.isFavorite}}"
            }
            return "{\"format\":\"GatherAI.SystemInstructionLibrary\",\"version\":\"2\",\"templates\":[$items]}"
        }

        fun importPortable(json: String): Result<List<SystemInstructionTemplate>> = runCatching {
            require(json.toByteArray(Charsets.UTF_8).size <= MAX_IMPORT_BYTES) { "导入文件超过 1 MB" }
            val fields = JsonMini.parse(json).asObject()?.entries ?: error("JSON 格式无效")
            require(fields["format"]?.asString() == "GatherAI.SystemInstructionLibrary" && fields["version"]?.asString() in setOf("1", "2")) { "模板文件版本不受支持" }
            val items = fields["templates"]?.asArray()?.items ?: error("文件中没有模板列表")
            require(items.isNotEmpty() && items.size <= MAX_COUNT) { "模板数量应为 1 至 $MAX_COUNT 项" }
            val parsed = items.map { value ->
                val item = value.asObject()?.entries ?: error("模板记录格式无效")
                val id = item["id"]?.asString()?.takeIf(String::isNotBlank) ?: error("模板缺少 ID")
                val title = item["title"]?.asString()?.trim()?.take(MAX_TITLE)?.takeIf(String::isNotBlank) ?: error("模板名称不能为空")
                val instruction = item["instruction"]?.asString()?.trim()?.take(MAX_INSTRUCTION)?.takeIf(String::isNotBlank) ?: error("模板指令不能为空")
                val category = item["category"]?.asString()?.trim()?.take(MAX_CATEGORY).orEmpty().ifBlank { "通用" }
                val favorite = (item["isFavorite"] as? com.tim.futureai.core.common.JsonValue.JBool)?.value ?: false
                SystemInstructionTemplate(id, title, instruction, category, favorite)
            }.distinctBy { it.id }
            require(parsed.isNotEmpty()) { "文件中没有有效模板" }
            parsed
        }
    }
}
