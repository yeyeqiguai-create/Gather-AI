package com.tim.futureai.core.common

/**
 * 极简 JSON 编解码（纯 Kotlin，无第三方依赖）。
 *
 * 只覆盖本项目所需：字符串/数字/布尔/null、对象、数组的完整解析，
 * 以及字符串转义。解析结果为 JsonValue 树，用于安全取值。
 * 后续如引入 kotlinx-serialization，可整体替换本文件。
 */
sealed class JsonValue {
    data class JString(val value: String) : JsonValue()
    data class JNumber(val value: Double) : JsonValue()
    data class JBool(val value: Boolean) : JsonValue()
    data object JNull : JsonValue()
    data class JArray(val items: List<JsonValue>) : JsonValue()
    data class JObject(val entries: Map<String, JsonValue>) : JsonValue()

    fun asString(): String? = (this as? JString)?.value
    fun asObject(): JObject? = this as? JObject
    fun asArray(): JArray? = this as? JArray
}

object JsonMini {

    /** 解析 JSON 文本；非法输入抛 [JsonParseException]（不吞异常）。 */
    fun parse(text: String): JsonValue {
        val p = Parser(text)
        val v = p.parseValue()
        p.skipWhitespace()
        if (!p.atEnd()) throw JsonParseException("Trailing content at ${p.pos}")
        return v
    }

    /** 字符串转义为合法 JSON 字符串字面量（含引号）。 */
    fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    class JsonParseException(message: String) : Exception(message)

    private class Parser(val text: String) {
        var pos = 0

        fun atEnd() = pos >= text.length

        fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        fun parseValue(): JsonValue {
            skipWhitespace()
            if (atEnd()) throw JsonParseException("Unexpected end of input")
            return when (text[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.JString(parseString())
                't' -> parseLiteral("true", JsonValue.JBool(true))
                'f' -> parseLiteral("false", JsonValue.JBool(false))
                'n' -> parseLiteral("null", JsonValue.JNull)
                else -> parseNumber()
            }
        }

        fun parseLiteral(word: String, value: JsonValue): JsonValue {
            if (pos + word.length > text.length || text.substring(pos, pos + word.length) != word) {
                throw JsonParseException("Invalid literal at $pos")
            }
            pos += word.length
            return value
        }

        fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonParseException("Unterminated string")
                when (val c = text[pos++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw JsonParseException("Unterminated escape")
                        when (val e = text[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (pos + 4 > text.length) throw JsonParseException("Bad unicode escape")
                                val hex = text.substring(pos, pos + 4)
                                pos += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> throw JsonParseException("Bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun parseObject(): JsonValue.JObject {
            expect('{')
            val map = mutableMapOf<String, JsonValue>()
            skipWhitespace()
            if (peek() == '}') { pos++; return JsonValue.JObject(map) }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                map[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> { pos++ }
                    '}' -> { pos++; return JsonValue.JObject(map) }
                    else -> throw JsonParseException("Expected ',' or '}' at $pos")
                }
            }
        }

        fun parseArray(): JsonValue.JArray {
            expect('[')
            val items = mutableListOf<JsonValue>()
            skipWhitespace()
            if (peek() == ']') { pos++; return JsonValue.JArray(items) }
            while (true) {
                items.add(parseValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> { pos++ }
                    ']' -> { pos++; return JsonValue.JArray(items) }
                    else -> throw JsonParseException("Expected ',' or ']' at $pos")
                }
            }
        }

        fun parseNumber(): JsonValue.JNumber {
            val start = pos
            if (peek() == '-') pos++
            while (!atEnd() && (text[pos].isDigit() || text[pos] in ".eE+-")) pos++
            val v = text.substring(start, pos).toDoubleOrNull()
                ?: throw JsonParseException("Invalid number at $start")
            return JsonValue.JNumber(v)
        }

        fun peek(): Char {
            if (atEnd()) throw JsonParseException("Unexpected end of input")
            return text[pos]
        }

        fun expect(c: Char) {
            if (atEnd() || text[pos] != c) throw JsonParseException("Expected '$c' at $pos")
            pos++
        }
    }
}
