package com.tim.futureai.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonMiniTest {

    @Test
    fun parse_simpleObject() {
        val v = JsonMini.parse("""{"a":"b","n":1.5,"t":true,"f":false,"z":null}""")
            .asObject()!!
        assertEquals("b", v.entries["a"]!!.asString())
        assertEquals(1.5, (v.entries["n"] as JsonValue.JNumber).value, 0.0)
        assertEquals(true, (v.entries["t"] as JsonValue.JBool).value)
        assertEquals(false, (v.entries["f"] as JsonValue.JBool).value)
        assertTrue(v.entries["z"] is JsonValue.JNull)
    }

    @Test
    fun parse_nestedArrayAndObject() {
        val v = JsonMini.parse("""{"choices":[{"message":{"content":"hi"}}]}""").asObject()!!
        val content = v.entries["choices"]!!.asArray()!!
            .items[0].asObject()!!.entries["message"]!!.asObject()!!
            .entries["content"]!!.asString()
        assertEquals("hi", content)
    }

    @Test
    fun parse_escapes() {
        val v = JsonMini.parse("""{"s":"line\nquote\"back\\slash\/tab\t"}""").asObject()!!
        assertEquals("line\nquote\"back\\slash/tab\t", v.entries["s"]!!.asString())
    }

    @Test
    fun parse_whitespaceTolerant() {
        val v = JsonMini.parse("  { \"k\" : [ 1 , 2 ] }  ").asObject()!!
        assertEquals(listOf(1.0, 2.0), v.entries["k"]!!.asArray()!!.items.map { (it as JsonValue.JNumber).value })
    }

    @Test(expected = JsonMini.JsonParseException::class)
    fun parse_invalidInputThrows() {
        JsonMini.parse("{not json}")
    }

    @Test(expected = JsonMini.JsonParseException::class)
    fun parse_trailingContentThrows() {
        JsonMini.parse("{} {}")
    }

    @Test
    fun quote_escapesSpecials() {
        assertEquals("\"a\\\"b\\\\c\\nd\"", JsonMini.quote("a\"b\\c\nd"))
    }

    @Test
    fun quote_and_parse_roundTrip() {
        val s = "引号\"反斜杠\\换行\n制表\temoji 😀"
        assertEquals(s, JsonMini.parse(JsonMini.quote(s)).asString())
    }

    @Test
    fun asString_onWrongTypeReturnsNull() {
        assertNull(JsonMini.parse("123").asString())
    }
}
