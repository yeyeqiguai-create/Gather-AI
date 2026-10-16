package com.tim.futureai.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkdownTest {
    @Test
    fun parsesBasicMarkdownBlocksAndLeavesInlineTextReadable() {
        val result = parseChatMarkdown("""# 标题

正文 **加粗** 和 *强调*
- 条目
> 引用
---""")

        assertEquals(MarkdownBlock.Heading(1, "标题"), result[0])
        assertEquals(MarkdownBlock.Paragraph("正文 **加粗** 和 *强调*"), result[1])
        assertEquals(MarkdownBlock.ListItem("-", "条目"), result[2])
        assertEquals(MarkdownBlock.Quote("引用"), result[3])
        assertEquals(MarkdownBlock.Divider, result[4])
        assertEquals(listOf(MarkdownBlock.Paragraph("普通文本 <b>不执行</b>")), parseChatMarkdown("普通文本 <b>不执行</b>"))
    }

    @Test
    fun codeFencePreservesExactSourceForIndependentCopy() {
        val source = "```kotlin\nfun hi() {\n  println(\"hello\")\n}\n```"
        val code = (parseChatMarkdown(source).single() as MarkdownBlock.Code).source
        var clipboard = ""
        copyMessageText(code, { clipboard = it }) {}
        assertEquals("fun hi() {\n  println(\"hello\")\n}", clipboard)

        val windowsCode = (parseChatMarkdown("```\r\nfirst\r\nsecond\r\n```").single() as MarkdownBlock.Code).source
        assertEquals("first\r\nsecond", windowsCode)
    }

    @Test
    fun malformedAndUnclosedFencesDoNotThrowOrLoseTheirContents() {
        val source = "```\n<remote script>\nnot closed"
        val code = (parseChatMarkdown(source).single() as MarkdownBlock.Code).source
        assertEquals("<remote script>\nnot closed", code)
        assertTrue(parseChatMarkdown("** _ ~~ ``` # > \u0000").isNotEmpty())
    }
}
