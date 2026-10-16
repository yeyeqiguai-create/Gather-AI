package com.tim.futureai.platform.files

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AttachmentImporterTest {
    @Test fun extractsTextAndXmlEntitiesFromDocxDocument() {
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write("<w:document><w:t>Hello</w:t><w:t> &amp; world</w:t></w:document>".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        assertEquals("Hello & world", AttachmentImporter.extractDocxText(bytes))
    }
}
