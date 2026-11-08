package com.tim.futureai.feature.link

import androidx.test.platform.app.InstrumentationRegistry
import com.tim.futureai.platform.link.LocalGatherLinkSession
import com.tim.futureai.core.common.JsonMini
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/** Exercises the embedded transfer server on the emulator's LAN interface. */
class GatherLinkTransferTest {
    @Test fun pairingUploadDownloadAndSessionExpiry() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val link = LocalGatherLinkSession(context)
        var uploadedId: String? = null
        try {
            link.start()
            assertTrue(link.state.value.error.orEmpty(), link.state.value.isActive)
            val base = link.state.value.addresses.first()
            assertEquals(401, request("$base/api/files").code)
            assertEquals(401, request("$base/api/pair", "POST", "code=invalid".toByteArray()).code)
            val paired = request("$base/api/pair", "POST", "code=${link.state.value.pairingCode}".toByteArray())
            assertEquals(200, paired.code)
            val cookie = paired.cookie!!.substringBefore(';')
            val payload = "Gather AI 0.11 · 文件传输校验\n".repeat(200).toByteArray(Charsets.UTF_8)
            val upload = request("$base/api/upload?name=verification.txt", "POST", payload, cookie)
            assertEquals(201, upload.code)
            uploadedId = JsonMini.parse(upload.bytes.toString(Charsets.UTF_8)).asObject()!!.entries["id"]!!.asString()!!
            assertEquals(payload.size.toLong(), link.state.value.receivedFiles.single { it.id == uploadedId }.sizeBytes)
            assertArrayEquals(payload, request("$base/api/inbox/$uploadedId", cookie = cookie).bytes)
            link.stop()
            link.start()
            assertTrue(link.state.value.isActive)
            val restarted = link.state.value.addresses.first()
            assertEquals(401, request("$restarted/api/inbox", cookie = cookie).code)
        } finally {
            uploadedId?.let { link.deleteReceivedFile(it) }
            link.stop()
        }
    }
    private data class Response(val code: Int, val bytes: ByteArray, val cookie: String?)
    private fun request(url: String, method: String = "GET", body: ByteArray? = null, cookie: String? = null): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000; connection.readTimeout = 5_000
            cookie?.let { connection.setRequestProperty("Cookie", it) }
            body?.let {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(it.size)
                connection.outputStream.use { output -> output.write(it) }
            }
            val code = connection.responseCode
            val bytes = (if (code >= 400) connection.errorStream else connection.inputStream)?.use { it.readBytes() } ?: byteArrayOf()
            return Response(code, bytes, connection.getHeaderField("Set-Cookie"))
        } finally { connection.disconnect() }
    }
}
