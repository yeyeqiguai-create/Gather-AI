package com.tim.futureai.data.ai

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.domain.ai.AiTokenUsage
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.MessageRole
import java.io.BufferedReader
import java.io.File

/** JSON and SSE mapping for Anthropic's Messages API. */
internal object AnthropicWire {
    fun buildRequestBody(
        messages: List<ChatMessage>, model: String, stream: Boolean,
        temperature: Double? = null, maxOutputTokens: Int? = null, systemInstruction: String = "",
    ): String {
        val systems = listOf(systemInstruction).filter(String::isNotBlank) + messages.filter { it.role == MessageRole.SYSTEM }.map { it.content }
        val turns = messages.filter { it.role != MessageRole.SYSTEM }.map { message ->
            val role = if (message.role == MessageRole.ASSISTANT) "assistant" else "user"
            val hasAttachments = message.attachments.any { it.mimeType.startsWith("image/") || it.textContent.isNotBlank() }
            val content = if (!hasAttachments) JsonMini.quote(message.content) else buildString {
                append('[')
                var comma = false
                fun text(value: String) {
                    if (comma) append(',')
                    append("{\"type\":\"text\",\"text\":").append(JsonMini.quote(value)).append('}')
                    comma = true
                }
                text(message.content)
                message.attachments.forEach { attachment ->
                    if (attachment.mimeType.startsWith("image/")) {
                        val bytes = runCatching { File(attachment.localPath).readBytes() }
                            .getOrElse { throw IllegalArgumentException("Attached image is unavailable", it) }
                        if (comma) append(',')
                        append("{\"type\":\"image\",\"source\":{\"type\":\"base64\",\"media_type\":")
                            .append(JsonMini.quote(attachment.mimeType)).append(",\"data\":")
                            .append(JsonMini.quote(OpenAiWire.encodeBase64(bytes))).append("}}")
                        comma = true
                    } else if (attachment.textContent.isNotBlank()) text("【附件：${attachment.name}】\n${attachment.textContent}")
                }
                append(']')
            }
            "{\"role\":${JsonMini.quote(role)},\"content\":$content}"
        }
        require(turns.isNotEmpty()) { "At least one user or assistant message is required" }
        return buildString {
            append("{\"model\":").append(JsonMini.quote(model))
            append(",\"max_tokens\":").append(maxOutputTokens ?: 4096)
            temperature?.let { append(",\"temperature\":").append(it) }
            if (systems.isNotEmpty()) append(",\"system\":").append(JsonMini.quote(systems.joinToString("\n\n")))
            append(",\"messages\":[").append(turns.joinToString(",")).append(']')
            if (stream) append(",\"stream\":true")
            append('}')
        }
    }

    fun extractReply(body: String): Pair<String, AiTokenUsage?> {
        val root = JsonMini.parse(body).asObject()?.entries ?: error("Expected JSON object")
        val content = root["content"]?.asArray()?.items.orEmpty().mapNotNull { block ->
            val fields = block.asObject()?.entries ?: return@mapNotNull null
            if (fields["type"]?.asString() == "text") fields["text"]?.asString() else null
        }.joinToString("")
        if (content.isBlank()) error("Empty assistant response")
        val usage = root["usage"]?.asObject()?.entries
        val input = (usage?.get("input_tokens") as? JsonValue.JNumber)?.value?.toInt()
        val output = (usage?.get("output_tokens") as? JsonValue.JNumber)?.value?.toInt()
        return content to if (input != null && output != null) AiTokenUsage(input, output) else null
    }

    fun streamReply(reader: BufferedReader, onUsage: (AiTokenUsage) -> Unit, onDelta: (String) -> Unit): Boolean {
        var eventName = ""
        val data = StringBuilder()
        var stopped = false
        var inputTokens: Int? = null
        var outputTokens: Int? = null
        fun dispatch() {
            if (data.isEmpty()) { eventName = ""; return }
            val payload = data.toString().trim()
            data.setLength(0)
            val root = JsonMini.parse(payload).asObject()?.entries ?: error("Malformed event")
            val type = root["type"]?.asString() ?: eventName
            when (type) {
                "content_block_delta" -> {
                    val delta = root["delta"]?.asObject()?.entries
                    if (delta?.get("type")?.asString() == "text_delta") delta["text"]?.asString()?.let(onDelta)
                }
                "message_start", "message_delta" -> {
                    val usage = (if (type == "message_start") root["message"] else null)
                        ?.asObject()?.entries?.get("usage")?.asObject()?.entries
                        ?: root["usage"]?.asObject()?.entries
                    (usage?.get("input_tokens") as? JsonValue.JNumber)?.value?.toInt()?.let { inputTokens = it }
                    (usage?.get("output_tokens") as? JsonValue.JNumber)?.value?.toInt()?.let { outputTokens = it }
                    if (inputTokens != null && outputTokens != null) onUsage(AiTokenUsage(inputTokens!!, outputTokens!!))
                }
                "error" -> {
                    val error = root["error"]?.asObject()?.entries
                    throw IllegalStateException(error?.get("message")?.asString() ?: "Anthropic stream error")
                }
                "message_stop" -> stopped = true
            }
            eventName = ""
        }
        while (!stopped) {
            val line = reader.readLine() ?: break
            when {
                line.isEmpty() -> dispatch()
                line.startsWith("event:") -> eventName = line.substringAfter(':').trim()
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.substringAfter(':').trimStart())
                }
            }
        }
        if (!stopped) dispatch()
        return stopped
    }
}
