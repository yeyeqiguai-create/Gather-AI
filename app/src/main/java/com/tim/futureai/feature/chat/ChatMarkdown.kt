package com.tim.futureai.feature.chat

internal sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class ListItem(val marker: String, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val source: String, val language: String = "") : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>, val alignment: List<MarkdownAlignment>) : MarkdownBlock
    data object Divider : MarkdownBlock
}

internal enum class MarkdownAlignment { START, CENTER, END }

/** Pipes inside inline code and escaped pipes belong to the cell's text. */
private fun tableCells(line: String): List<String> {
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var code = false
    var escaped = false
    line.trim().removePrefix("|").removeSuffix("|").forEach { ch ->
        when {
            escaped -> { if (ch != '|') cell.append('\\'); cell.append(ch); escaped = false }
            ch == '\\' -> escaped = true
            ch == '`' -> { code = !code; cell.append(ch) }
            ch == '|' && !code -> { cells += cell.toString().trim(); cell.clear() }
            else -> cell.append(ch)
        }
    }
    if (escaped) cell.append('\\')
    cells += cell.toString().trim()
    return cells
}

private fun tableHeader(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size || '|' !in lines[index]) return false
    val header = tableCells(lines[index])
    val separator = tableCells(lines[index + 1])
    return header.size == separator.size && separator.all { it.matches(Regex(":?-{3,}:?")) }
}

/** Small, non-HTML Markdown subset for chat text. Unclosed fences are treated as code to EOF. */
internal fun parseChatMarkdown(source: String): List<MarkdownBlock> = try {
    val lines = source.split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index].removeSuffix("\r")
        if (line.isBlank()) { index++; continue }
        if (line.trimStart().startsWith("```")) {
            val language = line.trimStart().drop(3).trim().takeWhile { !it.isWhitespace() }
            index++
            val code = mutableListOf<String>()
            while (index < lines.size && !lines[index].removeSuffix("\r").trimStart().startsWith("```")) code += lines[index++]
            if (index < lines.size && code.isNotEmpty()) code[code.lastIndex] = code.last().removeSuffix("\r")
            if (index < lines.size) index++
            blocks += MarkdownBlock.Code(code.joinToString("\n"), language)
            continue
        }
        if (tableHeader(lines, index)) {
            val headers = tableCells(line)
            val alignment = tableCells(lines[index + 1]).map {
                when { it.startsWith(':') && it.endsWith(':') -> MarkdownAlignment.CENTER
                    it.endsWith(':') -> MarkdownAlignment.END; else -> MarkdownAlignment.START }
            }
            index += 2
            val rows = mutableListOf<List<String>>()
            while (index < lines.size && lines[index].isNotBlank() && '|' in lines[index] && !startsBlock(lines[index])) {
                val cells = tableCells(lines[index++])
                rows += List(headers.size) { cells.getOrElse(it) { "" } }
            }
            blocks += MarkdownBlock.Table(headers, rows, alignment)
            continue
        }
        val heading = Regex("^ {0,3}(#{1,6})\\s+(.+?)\\s*#*\\s*$").matchEntire(line)
        if (heading != null) { blocks += MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2]); index++; continue }
        if (line.trim().matches(Regex("(?:(?:\\*\\s*){3,}|(?:-\\s*){3,}|(?:_\\s*){3,})"))) {
            blocks += MarkdownBlock.Divider; index++; continue
        }
        val quote = Regex("^ {0,3}>\\s?(.*)$").matchEntire(line)
        if (quote != null) {
            val quoted = mutableListOf<String>()
            while (index < lines.size) {
                val match = Regex("^ {0,3}>\\s?(.*)$").matchEntire(lines[index].removeSuffix("\r")) ?: break
                quoted += match.groupValues[1]
                index++
            }
            blocks += MarkdownBlock.Quote(quoted.joinToString("\n")); continue
        }
        val listItem = Regex("^\\s{0,3}([-+*]|\\d+[.)])\\s+(.+)$").matchEntire(line)
        if (listItem != null) { blocks += MarkdownBlock.ListItem(listItem.groupValues[1], listItem.groupValues[2]); index++; continue }
        val paragraph = mutableListOf<String>()
        while (index < lines.size && lines[index].removeSuffix("\r").isNotBlank() && !startsBlock(lines[index].removeSuffix("\r")) && !tableHeader(lines, index)) paragraph += lines[index++].removeSuffix("\r")
        if (paragraph.isEmpty()) { paragraph += lines[index++] }
        blocks += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
    }
    blocks
} catch (_: Exception) {
    listOf(MarkdownBlock.Paragraph(source))
}

private fun startsBlock(line: String): Boolean =
    line.trimStart().startsWith("```") ||
        Regex("^ {0,3}#{1,6}\\s+").containsMatchIn(line) ||
        Regex("^ {0,3}>").containsMatchIn(line) ||
        Regex("^\\s{0,3}([-+*]|\\d+[.)])\\s+").containsMatchIn(line) ||
        line.trim().matches(Regex("(?:(?:\\*\\s*){3,}|(?:-\\s*){3,}|(?:_\\s*){3,})"))
