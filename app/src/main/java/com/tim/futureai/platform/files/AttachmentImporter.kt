package com.tim.futureai.platform.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.tim.futureai.domain.model.ChatAttachment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/** Copies supported selections into app-private storage and extracts safe text locally. */
object AttachmentImporter {
    private const val MAX_INPUT_BYTES = 10 * 1024 * 1024
    private const val MAX_TEXT_CHARS = 1_000_000

    fun delete(attachment: ChatAttachment) {
        listOf(attachment.localPath, attachment.previewPath).filter(String::isNotBlank).forEach { File(it).delete() }
    }

    fun import(context: Context, uri: Uri): ChatAttachment {
        val resolver = context.contentResolver
        val name = displayName(context, uri)
        val mime = resolver.getType(uri)?.substringBefore(';')?.lowercase() ?: mimeFromName(name)
        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_INPUT_BYTES) { "文件超过 10 MB 限制" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("无法读取所选文件")
        }.getOrElse { return ChatAttachment(name = name, mimeType = mime, error = it.message ?: "读取文件失败") }

        val extension = name.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") && extension in IMAGE_EXTENSIONS -> saveImage(context, name, mime, bytes)
            extension in TEXT_EXTENSIONS || mime.startsWith("text/") -> {
                val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrDefault("").trim()
                if (text.isBlank() || text.length > MAX_TEXT_CHARS) {
                    ChatAttachment(name = name, mimeType = mime, error = "文本文件为空或超过 1,000,000 字符")
                } else ChatAttachment(name = name, mimeType = mime, textContent = text)
            }
            extension == "docx" -> {
                val text = runCatching { extractDocxText(bytes) }.getOrElse {
                    return ChatAttachment(name = name, mimeType = mime, error = "无法解析此 Word 文档")
                }
                if (text.isBlank()) ChatAttachment(name = name, mimeType = mime, error = "Word 文档中没有可提取文本")
                else ChatAttachment(name = name, mimeType = mime, textContent = text.take(MAX_TEXT_CHARS))
            }
            else -> ChatAttachment(
                name = name,
                mimeType = mime,
                error = "暂不支持解析 .$extension 文件；支持图片、TXT/MD/CSV/JSON/XML 和 DOCX",
            )
        }
    }

    fun extractDocxText(bytes: ByteArray): String {
        var document: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    var total = 0
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 5 * 1024 * 1024) { "Word document content is too large" }
                        output.write(buffer, 0, count)
                    }
                    document = output.toString("UTF-8")
                    break
                }
            }
        }
        return Regex("<w:t(?:\\s+[^>]*)?>(.*?)</w:t>", setOf(RegexOption.DOT_MATCHES_ALL))
            .findAll(document.orEmpty())
            .joinToString("") { decodeXml(it.groupValues[1]) }
            .take(MAX_TEXT_CHARS)
    }

    private fun saveImage(context: Context, name: String, mime: String, bytes: ByteArray): ChatAttachment = runCatching {
        val directory = File(context.filesDir, "attachments").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val extension = name.substringAfterLast('.', "img").lowercase().take(8)
        val image = File(directory, "$id.$extension").apply { writeBytes(bytes) }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val largest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        val sample = (largest / 384).coerceAtLeast(1)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        val preview = File(directory, "$id-preview.jpg")
        if (bitmap != null) {
            preview.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 78, it) }
            bitmap.recycle()
        }
        ChatAttachment(
            id = id, name = name, mimeType = mime, localPath = image.absolutePath,
            previewPath = preview.takeIf { it.exists() }?.absolutePath.orEmpty(),
        )
    }.getOrElse { ChatAttachment(name = name, mimeType = mime, error = "图片读取失败：${it.message ?: "未知错误"}") }

    private fun displayName(context: Context, uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "附件"
        return File(name).name.take(120).ifBlank { "附件" }
    }

    private fun mimeFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "txt" -> "text/plain"
        "md" -> "text/markdown"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }

    private fun decodeXml(value: String): String = value
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
    private val TEXT_EXTENSIONS = setOf("txt", "md", "csv", "json", "xml")
}
