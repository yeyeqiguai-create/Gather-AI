package com.tim.futureai.domain.ai

/** Local Ollama-style model tiers; tags are editable defaults, not assumptions. */
enum class LocalModelTier(val label: String, val defaultTag: String) {
    TINY_0_4B("0.4B", "qwen3:0.6b"),
    SMALL_1B("1B", "llama3.2:1b"),
    MEDIUM_2B("2B", "gemma3:2b"),
}

data class LocalRouteDecision(val tier: LocalModelTier, val reason: String)

data class SmartLocalRoute(val profileId: String, val modelId: String, val explanation: String)

object LocalModels {
    /** Emulator reaches the host loopback through 10.0.2.2; LAN users edit this. */
    const val DEFAULT_BASE_URL = "http://10.0.2.2:11434/v1"

    fun profileName(tier: LocalModelTier): String = "本地 " + tier.label

    /** Strict-JSON classification prompt answered by the smallest local model. */
    fun routerPrompt(userText: String): String = buildString {
        append("你是模型路由器。根据用户请求选择最合适的本地模型层级，只输出 JSON。\n")
        append("0.4b = 极短问答/闲聊/改写；1b = 一般问答与代码片段；2b = 复杂推理/长文/严谨任务。\n")
        append("输出格式：{\"tier\":\"0.4b\"|\"1b\"|\"2b\",\"reason\":\"不超过20字\"}\n")
        append("用户请求：")
        append(userText.take(800))
    }

    fun parseDecision(raw: String): LocalRouteDecision? {
        val jsonText = Regex("""\{[\s\S]*\}""").find(raw)?.value ?: raw
        val obj = runCatching { com.tim.futureai.core.common.JsonMini.parse(jsonText).asObject() }.getOrNull()
        val tierText = (obj?.get("tier")?.asString() ?: raw).lowercase()
        val tier = when {
            tierText.contains("0.4") -> LocalModelTier.TINY_0_4B
            tierText.contains("2b") -> LocalModelTier.MEDIUM_2B
            tierText.contains("1b") -> LocalModelTier.SMALL_1B
            else -> return null
        }
        return LocalRouteDecision(tier, obj?.get("reason")?.asString().orEmpty())
    }
}
