package com.tim.futureai.data.chat

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.security.SecureValueStore
import com.tim.futureai.domain.chat.PromptTemplate

/** Stores user-authored prompt templates separately from resettable display preferences. */
class PromptLibraryStore(private val store: SecureValueStore) {
    fun load(): List<PromptTemplate> = runCatching {
        val items = JsonMini.parse(store.getString(KEY).orEmpty()).asArray()?.items.orEmpty()
        items.mapNotNull { value ->
            val fields = value.asObject()?.entries ?: return@mapNotNull null
            val id = fields["id"]?.asString()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val title = fields["title"]?.asString()?.trim()?.take(MAX_TITLE)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val prompt = fields["prompt"]?.asString()?.take(MAX_PROMPT)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            PromptTemplate(id, title, prompt, "自定义")
        }.distinctBy { it.id }.take(MAX_COUNT)
    }.getOrDefault(emptyList())

    fun save(prompts: List<PromptTemplate>): Boolean {
        val normalized = prompts.asSequence()
            .filter { it.id.isNotBlank() && it.title.isNotBlank() && it.prompt.isNotBlank() }
            .distinctBy { it.id }
            .take(MAX_COUNT)
            .map { it.copy(title = it.title.trim().take(MAX_TITLE), prompt = it.prompt.trim().take(MAX_PROMPT), category = "自定义") }
            .filter { it.title.isNotBlank() && it.prompt.isNotBlank() }
            .toList()
        val json = normalized.joinToString(",", prefix = "[", postfix = "]") { item ->
            "{\"id\":${JsonMini.quote(item.id)},\"title\":${JsonMini.quote(item.title)},\"prompt\":${JsonMini.quote(item.prompt)}}"
        }
        return store.putString(KEY, json)
    }

    companion object {
        const val KEY = "prompt_library_v1"
        const val MAX_COUNT = 100
        const val MAX_TITLE = 48
        const val MAX_PROMPT = 4_000
        const val MAX_IMPORT_BYTES = 1_000_000

        fun exportPortable(prompts: List<PromptTemplate>): String {
            val items = prompts.take(MAX_COUNT).joinToString(",") { item ->
                "{\"id\":${JsonMini.quote(item.id)},\"title\":${JsonMini.quote(item.title.take(MAX_TITLE))},\"prompt\":${JsonMini.quote(item.prompt.take(MAX_PROMPT))}}"
            }
            return "{\"format\":\"GatherAI.PromptLibrary\",\"version\":\"1\",\"prompts\":[$items]}"
        }

        fun importPortable(json: String): Result<List<PromptTemplate>> = runCatching {
            require(json.toByteArray(Charsets.UTF_8).size <= MAX_IMPORT_BYTES) { "提示词文件过大" }
            val fields = JsonMini.parse(json).asObject()?.entries ?: error("文件格式无效")
            require(fields["format"]?.asString() == "GatherAI.PromptLibrary" && fields["version"]?.asString() == "1") { "文件版本不受支持" }
            val items = fields["prompts"]?.asArray()?.items ?: error("文件中没有提示词列表")
            require(items.isNotEmpty() && items.size <= MAX_COUNT) { "提示词数量应为 1 至 $MAX_COUNT 项" }
            val prompts = items.map { value ->
                val item = value.asObject()?.entries ?: error("提示词记录格式无效")
                val id = item["id"]?.asString()?.takeIf(String::isNotBlank) ?: error("提示词缺少 ID")
                val title = item["title"]?.asString()?.trim()?.take(MAX_TITLE)?.takeIf(String::isNotBlank) ?: error("提示词名称不能为空")
                val prompt = item["prompt"]?.asString()?.trim()?.take(MAX_PROMPT)?.takeIf(String::isNotBlank) ?: error("提示内容不能为空")
                PromptTemplate(id, title, prompt, "自定义")
            }.distinctBy { it.id }
            require(prompts.isNotEmpty()) { "文件中没有有效提示词" }
            prompts
        }
    }
}
