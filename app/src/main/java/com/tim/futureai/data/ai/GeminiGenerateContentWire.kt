package com.tim.futureai.data.ai

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.domain.ai.AiTokenUsage
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.MessageRole
import java.io.BufferedReader
import java.io.File

/** Legacy (still supported) Gemini GenerateContent wire format. */
internal object GeminiGenerateContentWire {
    fun buildRequestBody(
        messages: List<ChatMessage>, temperature: Double?, maxOutputTokens: Int?,
        systemInstruction: String = "",
    ): String {
        val system = (listOf(systemInstruction).filter(String::isNotBlank) + messages.filter { it.role == MessageRole.SYSTEM }.map { it.content }).joinToString("\n\n")
        val history = messages.filter { it.role != MessageRole.SYSTEM }.map { message ->
            val role = if (message.role == MessageRole.ASSISTANT) "model" else "user"
            val parts = buildString {
                append('[')
                var comma = false
                fun text(value: String) {
                    if (comma) append(',')
                    append("{\"text\":").append(JsonMini.quote(value)).append('}')
                    comma = true
                }
                text(message.content)
                message.attachments.forEach { attachment ->
                    if (attachment.mimeType.startsWith("image/")) {
                        val bytes = runCatching { File(attachment.localPath).readBytes() }
                            .getOrElse { throw IllegalArgumentException("Attached image is unavailable", it) }
                        if (comma) append(',')
                        append("{\"inlineData\":{\"mimeType\":")
                            .append(JsonMini.quote(attachment.mimeType)).append(",\"data\":")
                            .append(JsonMini.quote(OpenAiWire.encodeBase64(bytes))).append("}}")
                        comma = true
                    } else if (attachment.textContent.isNotBlank()) text("【附件：${attachment.name}】\n${attachment.textContent}")
                }
                append(']')
            }
            "{\"role\":${JsonMini.quote(role)},\"parts\":$parts}"
        }
        require(history.isNotEmpty()) { "At least one user or assistant message is required" }
        return buildString {
            append("{\"contents\":[").append(history.joinToString(",")).append(']')
            if (system.isNotBlank()) append(",\"systemInstruction\":{\"parts\":[{\"text\":")
                .append(JsonMini.quote(system)).append("}]}")
            if (temperature != null || maxOutputTokens != null) {
                append(",\"generationConfig\":{")
                if (temperature != null) append("\"temperature\":").append(temperature)
                if (temperature != null && maxOutputTokens != null) append(',')
                if (maxOutputTokens != null) append("\"maxOutputTokens\":").append(maxOutputTokens)
                append('}')
            }
            append('}')
        }
    }

    fun extractReply(body: String): Pair<String, AiTokenUsage?> {
        val root = JsonMini.parse(body).asObject()?.entries ?: error("Expected JSON object")
        root["error"]?.asObject()?.entries?.get("message")?.asString()?.let { error(it) }
        val reply = root["candidates"]?.asArray()?.items.orEmpty().firstOrNull()
            ?.asObject()?.entries?.get("content")?.asObject()?.entries?.get("parts")?.asArray()?.items.orEmpty()
            .mapNotNull { part -> part.asObject()?.entries?.get("text")?.asString() }.joinToString("")
        if (reply.isBlank()) {
            val blocked = root["promptFeedback"]?.asObject()?.entries?.get("blockReason")?.asString()
            error(blocked?.let { "Gemini blocked the prompt: $it" } ?: "Empty assistant response")
        }
        return reply to root["usageMetadata"]?.asObject()?.entries?.let(::usage)
    }

    fun streamReply(reader: BufferedReader, onUsage: (AiTokenUsage) -> Unit, onDelta: (String) -> Unit): Boolean {
        val data = StringBuilder()
        var completed = false
        var emittedText = false
        fun dispatch() {
            if (data.isEmpty()) return
            val payload = data.toString().trim()
            data.setLength(0)
            if (payload == "[DONE]") { completed = true; return }
            val root = JsonMini.parse(payload).asObject()?.entries ?: error("Malformed Gemini stream event")
            root["error"]?.asObject()?.entries?.get("message")?.asString()?.let { error(it) }
            val candidates = root["candidates"]?.asArray()?.items.orEmpty()
            candidates.firstOrNull()?.asObject()?.entries?.let { candidate ->
                candidate["content"]?.asObject()?.entries?.get("parts")?.asArray()?.items.orEmpty().forEach { part ->
                    part.asObject()?.entries?.get("text")?.asString()?.takeIf(String::isNotEmpty)?.let {
                        emittedText = true
                        onDelta(it)
                    }
                }
                val finishReason = candidate["finishReason"]?.asString()
                if (!finishReason.isNullOrBlank() && finishReason != "FINISH_REASON_UNSPECIFIED") completed = true
            }
            root["usageMetadata"]?.asObject()?.entries?.let(::usage)?.let(onUsage)
        }
        while (!completed) {
            val line = reader.readLine() ?: break
            when {
                line.isEmpty() -> dispatch()
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.substringAfter(':').trimStart())
                }
            }
        }
        if (!completed) dispatch()
        return completed && emittedText
    }

    private fun usage(fields: Map<String, JsonValue>): AiTokenUsage? {
        val input = (fields["promptTokenCount"] as? JsonValue.JNumber)?.value?.toInt()
        val output = (fields["candidatesTokenCount"] as? JsonValue.JNumber)?.value?.toInt()
        return if (input != null && output != null && input >= 0 && output >= 0) AiTokenUsage(input, output) else null
    }
}
