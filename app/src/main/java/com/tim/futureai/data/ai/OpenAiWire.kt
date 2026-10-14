package com.tim.futureai.data.ai

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.domain.model.ChatMessage

/**
 * OpenAI Chat Completions 协议的纯 Kotlin 编解码。
 * 请求体构造与响应解析独立于网络层，便于单元测试。
 */
object OpenAiWire {

    /** 构造 POST /chat/completions 请求体。 */
    fun buildRequestBody(messages: List<ChatMessage>, model: String): String {
        val sb = StringBuilder()
        sb.append("{\"model\":").append(JsonMini.quote(model)).append(",\"messages\":[")
        messages.forEachIndexed { i, m ->
            if (i > 0) sb.append(',')
            sb.append("{\"role\":").append(JsonMini.quote(m.role.name.lowercase()))
                .append(",\"content\":").append(JsonMini.quote(m.content)).append('}')
        }
        sb.append("]}")
        return sb.toString()
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
        return message.entries["content"]?.asString()
            ?: throw ResponseParseException("Missing message content")
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
