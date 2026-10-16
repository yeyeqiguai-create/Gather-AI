package com.tim.futureai.data.ai

import java.io.BufferedReader
import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.domain.model.ChatMessage
import com.tim.futureai.domain.model.ChatAttachment
import com.tim.futureai.domain.ai.AiTokenUsage
import java.io.File

/**
 * OpenAI Chat Completions 协议的纯 Kotlin 编解码。
 * 请求体构造与响应解析独立于网络层，便于单元测试。
 */
object OpenAiWire {

    /** 构造 POST /chat/completions 请求体。 */
    fun buildRequestBody(messages: List<ChatMessage>, model: String, stream: Boolean = false): String {
        val sb = StringBuilder()
        sb.append("{\"model\":").append(JsonMini.quote(model))
        if (stream) sb.append(",\"stream\":true")
        sb.append(",\"messages\":[")
        messages.forEachIndexed { i, m ->
            if (i > 0) sb.append(',')
            sb.append("{\"role\":").append(JsonMini.quote(m.role.name.lowercase()))
                .append(",\"content\":")
            if (m.attachments.isEmpty()) sb.append(JsonMini.quote(m.content)) else appendAttachmentContent(sb, m.content, m.attachments)
            sb.append('}')
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun appendAttachmentContent(out: StringBuilder, text: String, attachments: List<ChatAttachment>) {
        out.append('[')
        var needsComma = false
        fun addText(value: String) {
            if (needsComma) out.append(',')
            out.append("{\"type\":\"text\",\"text\":").append(JsonMini.quote(value)).append('}')
            needsComma = true
        }
        addText(text)
        attachments.forEach { attachment ->
            if (attachment.mimeType.startsWith("image/")) {
                val bytes = runCatching { File(attachment.localPath).readBytes() }.getOrElse {
                    throw ResponseParseException("Attached image is unavailable", it)
                }
                if (needsComma) out.append(',')
                out.append("{\"type\":\"image_url\",\"image_url\":{\"url\":")
                    .append(JsonMini.quote("data:${attachment.mimeType};base64,${encodeBase64(bytes)}"))
                    .append("}}")
                needsComma = true
            } else if (attachment.textContent.isNotBlank()) {
                addText("【附件：${attachment.name}】\n${attachment.textContent}")
            }
        }
        out.append(']')
    }

    /** Small RFC 4648 encoder to keep API-24 compatibility without another dependency. */
    fun encodeBase64(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val out = StringBuilder(((bytes.size + 2) / 3) * 4)
        var i = 0
        while (i < bytes.size) {
            val a = bytes[i].toInt() and 0xff
            val hasB = i + 1 < bytes.size
            val hasC = i + 2 < bytes.size
            val b = if (hasB) bytes[i + 1].toInt() and 0xff else 0
            val c = if (hasC) bytes[i + 2].toInt() and 0xff else 0
            out.append(alphabet[a ushr 2])
            out.append(alphabet[((a and 0x03) shl 4) or (b ushr 4)])
            out.append(if (hasB) alphabet[((b and 0x0f) shl 2) or (c ushr 6)] else '=')
            out.append(if (hasC) alphabet[c and 0x3f] else '=')
            i += 3
        }
        return out.toString()
    }

    /** Reads SSE data events until [DONE] or EOF and emits each text delta in order. */
    fun streamReply(reader: BufferedReader, onUsage: (AiTokenUsage) -> Unit = {}, onDelta: (String) -> Unit): Boolean {
        val data = mutableListOf<String>()
        var done = false
        fun dispatch() {
            if (data.isEmpty()) return
            val payload = data.joinToString("\n")
            data.clear()
            if (payload == "[DONE]") {
                done = true
                return
            }
            extractTokenUsage(payload)?.let(onUsage)
            extractDeltaContent(payload)?.takeIf(String::isNotEmpty)?.let(onDelta)
        }
        while (!done) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) dispatch()
            else if (line.startsWith("data:")) data += line.substring(5).removePrefix(" ")
        }
        if (!done) dispatch()
        return done
    }

    fun extractTokenUsage(responseBody: String): AiTokenUsage? = runCatching {
        val root = JsonMini.parse(responseBody).asObject()?.entries ?: return null
        val usage = root["usage"]?.asObject()?.entries ?: return null
        val prompt = (usage["prompt_tokens"] as? JsonValue.JNumber)?.value?.toInt() ?: return null
        val completion = (usage["completion_tokens"] as? JsonValue.JNumber)?.value?.toInt() ?: return null
        if (prompt < 0 || completion < 0) null else AiTokenUsage(prompt, completion)
    }.getOrNull()

    /** Extracts one OpenAI-compatible choices[0].delta.content value. */
    fun extractDeltaContent(eventData: String): String? {
        val root = try {
            JsonMini.parse(eventData).asObject()
        } catch (e: JsonMini.JsonParseException) {
            throw ResponseParseException("Malformed JSON in stream event", e)
        } ?: throw ResponseParseException("Expected JSON object in stream event")
        root.entries["error"]?.asObject()?.entries?.get("message")?.asString()?.let {
            throw ResponseParseException(it)
        }
        val first = root.entries["choices"]?.asArray()?.items?.firstOrNull()?.asObject() ?: return null
        return first.entries["delta"]?.asObject()?.entries?.get("content")?.asString()
    }

    /** 从响应体提取 choices[0].message.content；结构不符抛 [ResponseParseException]。 */
    fun extractReplyContent(responseBody: String): String {
        val root = try {
            JsonMini.parse(responseBody).asObject()
        } catch (e: JsonMini.JsonParseException) {
            throw ResponseParseException("Malformed JSON in response", e)
        } ?: throw ResponseParseException("Expected JSON object")
        val choices = root.entries["choices"]?.asArray()
            ?: throw ResponseParseException("Missing choices array")
        val first = choices.items.firstOrNull()?.asObject()
            ?: throw ResponseParseException("Empty choices")
        val message = first.entries["message"]?.asObject()
            ?: throw ResponseParseException("Missing message object")
        val content = message.entries["content"]?.asString()
            ?: throw ResponseParseException("Missing message content")
        if (content.isBlank()) throw ResponseParseException("Empty assistant response")
        return content
    }

    /** 从错误响应体提取可读信息；失败返回原始截断文本。 */
    fun extractErrorMessage(errorBody: String): String =
        try {
            (JsonMini.parse(errorBody).asObject())
                ?.entries?.get("error")?.asObject()
                ?.entries?.get("message")?.asString()
                ?: errorBody.take(200)
        } catch (e: JsonMini.JsonParseException) {
            errorBody.take(200)
        }

    class ResponseParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
}
