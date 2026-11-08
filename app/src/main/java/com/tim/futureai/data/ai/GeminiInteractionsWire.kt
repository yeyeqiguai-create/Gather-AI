package com.tim.futureai.data.ai

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.domain.ai.AiTokenUsage
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.MessageRole
import java.io.BufferedReader
import java.io.File

/** Stateless request/response mapping for Google's current Gemini Interactions API. */
internal object GeminiInteractionsWire {
    fun buildRequestBody(
        messages: List<ChatMessage>, model: String, stream: Boolean,
        temperature: Double? = null, maxOutputTokens: Int? = null, systemInstruction: String = "",
    ): String {
        val system = (listOf(systemInstruction).filter(String::isNotBlank) + messages.filter { it.role == MessageRole.SYSTEM }.map { it.content }).joinToString("\n\n")
        val history = messages.filter { it.role != MessageRole.SYSTEM }.map { message ->
            val role = if (message.role == MessageRole.ASSISTANT) "model_output" else "user_input"
            val content = buildString {
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
                        append("{\"type\":\"image\",\"mime_type\":")
                            .append(JsonMini.quote(attachment.mimeType)).append(",\"data\":")
                            .append(JsonMini.quote(OpenAiWire.encodeBase64(bytes))).append('}')
                        comma = true
                    } else if (attachment.textContent.isNotBlank()) text("【附件：${attachment.name}】\n${attachment.textContent}")
                }
                append(']')
            }
            "{\"type\":${JsonMini.quote(role)},\"content\":$content}"
        }
        require(history.isNotEmpty()) { "At least one user or assistant message is required" }
        return buildString {
            append("{\"model\":").append(JsonMini.quote(model))
            append(",\"input\":[").append(history.joinToString(",")).append(']')
            append(",\"store\":false")
            if (stream) append(",\"stream\":true")
            if (system.isNotBlank()) append(",\"system_instruction\":").append(JsonMini.quote(system))
            if (temperature != null || maxOutputTokens != null) {
                append(",\"generation_config\":{")
                if (temperature != null) append("\"temperature\":").append(temperature)
                if (temperature != null && maxOutputTokens != null) append(',')
                if (maxOutputTokens != null) append("\"max_output_tokens\":").append(maxOutputTokens)
                append('}')
            }
            append('}')
        }
    }

    fun extractReply(body: String): Pair<String, AiTokenUsage?> {
        val root = JsonMini.parse(body).asObject()?.entries ?: error("Expected JSON object")
        val steps = root["steps"]?.asArray()?.items.orEmpty()
        val text = steps.asSequence().mapNotNull { it.asObject()?.entries }
            .filter { it["type"]?.asString() == "model_output" }
            .flatMap { it["content"]?.asArray()?.items.orEmpty().asSequence() }
            .mapNotNull { part ->
                val fields = part.asObject()?.entries ?: return@mapNotNull null
                if (fields["type"]?.asString() == "text") fields["text"]?.asString() else null
            }.joinToString("")
        val fallback = root["output_text"]?.asString().orEmpty()
        val reply = text.ifBlank { fallback }
        if (reply.isBlank()) error("Empty assistant response")
        return reply to root["usage"]?.asObject()?.entries?.let(::usage)
    }

    fun stream(reader: BufferedReader, onUsage: (AiTokenUsage) -> Unit, onDelta: (String) -> Unit): Boolean {
        val data = StringBuilder()
        var eventName = ""
        var completed = false
        fun dispatch() {
            if (data.isEmpty()) { eventName = ""; return }
            val payload = data.toString().trim()
            data.setLength(0)
            if (payload == "[DONE]") { completed = true; return }
            val root = JsonMini.parse(payload).asObject()?.entries ?: error("Malformed Gemini stream event")
            val type = root["event_type"]?.asString() ?: eventName
            when (type) {
                "step.delta" -> {
                    val delta = root["delta"]?.asObject()?.entries
                    if (delta?.get("type")?.asString() == "text") delta["text"]?.asString()?.let(onDelta)
                }
                "interaction.completed" -> {
                    val usage = root["interaction"]?.asObject()?.entries?.get("usage")?.asObject()?.entries?.let(::usage)
                    usage?.let(onUsage)
                    completed = true
                }
                "interaction.failed" -> {
                    val message = root["error"]?.asObject()?.entries?.get("message")?.asString()
                        ?: root["interaction"]?.asObject()?.entries?.get("error")?.asString()
                    throw IllegalStateException(message ?: "Gemini interaction failed")
                }
            }
            eventName = ""
        }
        while (!completed) {
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
        if (!completed) dispatch()
        return completed
    }

    private fun usage(fields: Map<String, JsonValue>): AiTokenUsage? {
        val prompt = (fields["total_input_tokens"] as? JsonValue.JNumber)?.value?.toInt()
            ?: (fields["promptTokenCount"] as? JsonValue.JNumber)?.value?.toInt()
        val output = (fields["total_output_tokens"] as? JsonValue.JNumber)?.value?.toInt()
            ?: (fields["candidatesTokenCount"] as? JsonValue.JNumber)?.value?.toInt()
        return if (prompt != null && output != null && prompt >= 0 && output >= 0) AiTokenUsage(prompt, output) else null
    }
}
