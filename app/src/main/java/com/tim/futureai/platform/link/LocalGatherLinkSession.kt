package com.tim.futureai.platform.link

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import com.tim.futureai.domain.link.GatherLinkSession
import com.tim.futureai.domain.link.GatherLinkState
import com.tim.futureai.domain.link.LinkReceivedFile
import com.tim.futureai.domain.link.LinkSharedFile
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A short-lived, PIN-protected HTTP link that only listens on private IPv4 interfaces. */
class LocalGatherLinkSession(context: Context) : GatherLinkSession {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val inbox = File(appContext.filesDir, "gather-link-inbox")
    private val webPage by lazy {
        appContext.assets.open("gather-link/index.html").bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(GatherLinkState(receivedFiles = loadReceivedFiles()))
    override val state: StateFlow<GatherLinkState> = mutableState.asStateFlow()

    private val lock = Any()
    private val startMutex = Mutex()
    private val sockets = mutableListOf<ServerSocket>()
    private val acceptJobs = mutableListOf<Job>()
    private val activeClients = ConcurrentHashMap.newKeySet<Socket>()
    private val sharedFiles = ConcurrentHashMap<String, LinkSharedFile>()
    private val activeInboxIds = ConcurrentHashMap.newKeySet<String>()
    private val sessions = ConcurrentHashMap<String, Long>()
    private val pairAttempts = ConcurrentHashMap<String, PairAttemptWindow>()
    @Volatile private var pairingCode: String? = null

    override suspend fun start() = startMutex.withLock {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                if (sockets.isNotEmpty()) return@withContext
            }

            val localAddresses = privateIpv4Addresses()
            if (localAddresses.isEmpty()) {
                mutableState.update { it.copy(error = "没有找到局域网地址；请先连接 Wi‑Fi 或手机热点。", message = null) }
                return@withContext
            }

            val created = mutableListOf<ServerSocket>()
            var selectedPort = 0
            for (address in localAddresses) {
                val server = ServerSocket()
                try {
                    server.reuseAddress = true
                    server.bind(InetSocketAddress(address, selectedPort), 12)
                    if (selectedPort == 0) selectedPort = server.localPort
                    created += server
                } catch (_: IOException) {
                    runCatching { server.close() }
                }
            }
            if (created.isEmpty()) {
                mutableState.update { it.copy(error = "无法开启局域网连接，请稍后重试。", message = null) }
                return@withContext
            }
            try {
                currentCoroutineContext().ensureActive()
            } catch (cancelled: CancellationException) {
                created.forEach { runCatching { it.close() } }
                throw cancelled
            }

            val code = randomPairingCode()
            synchronized(lock) {
                sockets += created
                pairingCode = code
                activeInboxIds.clear()
                val links = created.mapNotNull { server ->
                    (server.inetAddress.hostAddress ?: return@mapNotNull null).let { "http://$it:${server.localPort}" }
                }
                mutableState.update {
                    it.copy(
                        isActive = true,
                        addresses = links,
                        pairingCode = code,
                        error = null,
                        message = "互联已开启；其他设备请连接同一 Wi‑Fi。",
                        receivedFiles = loadReceivedFiles(),
                    )
                }
                created.forEach { server ->
                    acceptJobs += serviceScope.launch { acceptLoop(server) }
                }
            }
        }
    }

    override fun stop() {
        val toClose = synchronized(lock) {
            val active = sockets.toList()
            sockets.clear()
            acceptJobs.forEach(Job::cancel)
            acceptJobs.clear()
            pairingCode = null
            sessions.clear()
            pairAttempts.clear()
            activeInboxIds.clear()
            mutableState.update {
                it.copy(
                    isActive = false,
                    addresses = emptyList(),
                    pairingCode = null,
                    message = "互联已关闭。",
                    error = null,
                )
            }
            active
        }
        toClose.forEach { runCatching { it.close() } }
        activeClients.toList().forEach { runCatching { it.close() } }
    }

    override suspend fun setSharedFiles(contentUris: List<String>) = withContext(Dispatchers.IO) {
        val next = contentUris.distinct().take(MAX_SHARED_FILES).mapNotNull { uriText ->
            runCatching {
                val uri = Uri.parse(uriText)
                var name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "文件"
                var size: Long? = null
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0) name = cursor.getString(nameIndex)?.ifBlank { name } ?: name
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex).takeIf { it >= 0 }
                    }
                }
                val mime = safeMimeType(resolver.getType(uri))
                LinkSharedFile(UUID.randomUUID().toString(), safeFilename(name), mime, size, uriText)
            }.getOrNull()
        }
        sharedFiles.clear()
        next.forEach { sharedFiles[it.id] = it }
        mutableState.update {
            it.copy(
                sharedFiles = next,
                message = when {
                    contentUris.size > MAX_SHARED_FILES -> "最多共享 $MAX_SHARED_FILES 个文件。"
                    next.size < contentUris.distinct().size -> "已加入 ${next.size} 个可读取文件；部分文件无法访问。"
                    else -> "已更新共享文件。"
                },
                error = null,
            )
        }
    }

    override suspend fun saveReceivedFile(fileId: String, destinationUri: String) = withContext(Dispatchers.IO) {
        val file = receivedFile(fileId)
        if (file == null) {
            mutableState.update { it.copy(error = "找不到这份接收文件。", message = null) }
            return@withContext
        }
        try {
            val target = resolver.openOutputStream(Uri.parse(destinationUri), "w")
                ?: throw IOException("无法打开保存位置")
            target.use { output -> file.inputStream().use { input -> input.copyTo(output) } }
            mutableState.update { it.copy(message = "已保存到所选位置：${file.name}", error = null) }
        } catch (_: Exception) {
            mutableState.update { it.copy(error = "保存失败；请重新选择位置后再试。", message = null) }
        }
    }

    override suspend fun deleteReceivedFile(fileId: String) = withContext(Dispatchers.IO) {
        val file = receivedFile(fileId)
        if (file?.delete() == true) {
            mutableState.update { it.copy(receivedFiles = loadReceivedFiles(), message = "已删除接收文件。", error = null) }
        } else {
            mutableState.update { it.copy(error = "删除失败；文件可能已不存在。", message = null) }
        }
    }

    private suspend fun acceptLoop(server: ServerSocket) {
        while (currentCoroutineContext().isActive) {
            val client = try {
                server.accept().apply { soTimeout = CLIENT_TIMEOUT_MS }
            } catch (_: SocketException) {
                return
            } catch (_: IOException) {
                if (server.isClosed) return else continue
            }
            activeClients += client
            serviceScope.launch {
                try {
                    handleClient(client)
                } catch (_: Exception) {
                    // A disconnected or malformed local client does not affect the link session.
                } finally {
                    activeClients -= client
                    runCatching { client.close() }
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        val remote = socket.inetAddress
        if (!(remote.isSiteLocalAddress || remote.isLinkLocalAddress || remote.isLoopbackAddress)) {
            socket.getOutputStream().let { respond(it, 403, "Forbidden", "仅允许局域网设备访问。".toByteArray(), "text/plain; charset=utf-8") }
            return
        }

        val input = socket.getInputStream()
        val output = BufferedOutputStream(socket.getOutputStream())
        val requestLine = readAsciiLine(input, MAX_REQUEST_LINE_BYTES) ?: return
        val parts = requestLine.split(' ', limit = 3)
        if (parts.size < 2 || parts[0].length > 8) {
            respond(output, 400, "Bad Request", jsonError("请求无效"), JSON_TYPE)
            return
        }
        val headers = LinkedHashMap<String, String>()
        var headerBytes = 0
        while (true) {
            val line = readAsciiLine(input, MAX_HEADER_BYTES) ?: return
            headerBytes += line.length
            if (headerBytes > MAX_TOTAL_HEADER_BYTES) {
                respond(output, 431, "Request Header Fields Too Large", jsonError("请求头过长"), JSON_TYPE)
                return
            }
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
        }

        val target = try {
            URI(parts[1])
        } catch (_: Exception) {
            respond(output, 400, "Bad Request", jsonError("请求地址无效"), JSON_TYPE)
            return
        }
        val path = decode(target.rawPath ?: "/")
        val query = parseQuery(target.rawQuery.orEmpty())
        val method = parts[0].uppercase()

        if (method == "GET" && path == "/") {
            respond(output, 200, "OK", webPage.toByteArray(StandardCharsets.UTF_8), "text/html; charset=utf-8")
            return
        }
        if (method == "POST" && path == "/api/pair") {
            pairDevice(socket, input, output, headers)
            return
        }

        val sessionToken = cookieValue(headers["cookie"], "gather_link")
        if (sessionToken == null || !isSessionValid(sessionToken)) {
            respond(output, 401, "Unauthorized", jsonError("先输入手机上显示的配对码"), JSON_TYPE)
            return
        }

        when {
            method == "GET" && path == "/api/files" -> {
                val body = sharedFiles.values.sortedBy { it.name.lowercase() }.joinToString(",", "[", "]") { file ->
                    "{\"id\":${json(file.id)},\"name\":${json(file.name)},\"mime\":${json(file.mimeType)},\"size\":${file.sizeBytes?.toString() ?: "null"}}"
                }
                respond(output, 200, "OK", body.toByteArray(StandardCharsets.UTF_8), JSON_TYPE)
            }
            method == "GET" && path.startsWith("/api/files/") -> downloadFile(path.removePrefix("/api/files/"), output)
            method == "GET" && path == "/api/inbox" -> {
                val body = activeInboxIds.mapNotNull(::receivedFile)
                    .sortedByDescending(File::lastModified)
                    .joinToString(",", "[", "]") { file ->
                        val id = file.name.substringBefore("__")
                        val name = file.name.substringAfter("__", "接收文件")
                        "{\"id\":${json(id)},\"name\":${json(name)},\"size\":${file.length()}}"
                    }
                respond(output, 200, "OK", body.toByteArray(StandardCharsets.UTF_8), JSON_TYPE)
            }
            method == "GET" && path.startsWith("/api/inbox/") -> downloadInboxFile(path.removePrefix("/api/inbox/"), output)
            method == "POST" && path == "/api/upload" -> uploadFile(input, output, headers, query["name"] ?: "接收文件", sessionToken)
            else -> respond(output, 404, "Not Found", jsonError("没有这个地址"), JSON_TYPE)
        }
    }

    private fun pairDevice(socket: Socket, input: InputStream, output: BufferedOutputStream, headers: Map<String, String>) {
        val length = headers["content-length"]?.toLongOrNull()
        if (length == null || length !in 1..MAX_PAIR_BODY_BYTES) {
            respond(output, 400, "Bad Request", jsonError("配对请求无效"), JSON_TYPE)
            return
        }
        val body = readExact(input, length.toInt()) ?: run {
            respond(output, 400, "Bad Request", jsonError("配对请求不完整"), JSON_TYPE)
            return
        }
        val values = parseQuery(String(body, StandardCharsets.UTF_8))
        val submitted = values["code"].orEmpty()
        val expected = pairingCode
        if (!canTryPair(socket.inetAddress.hostAddress.orEmpty()) || expected == null || !sameSecret(submitted, expected)) {
            respond(output, 401, "Unauthorized", jsonError("配对码不正确，或尝试次数过多"), JSON_TYPE)
            return
        }
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { it.value <= now }
        if (sessions.size >= MAX_PAIRED_DEVICES) {
            respond(output, 429, "Too Many Requests", jsonError("当前会话已连接太多设备"), JSON_TYPE)
            return
        }
        val token = ByteArray(24).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        sessions[token] = now + SESSION_TTL_MS
        val bodyBytes = "{\"ok\":true}".toByteArray(StandardCharsets.UTF_8)
        val extra = mapOf("Set-Cookie" to "gather_link=$token; HttpOnly; SameSite=Strict; Path=/; Max-Age=3600")
        respond(output, 200, "OK", bodyBytes, JSON_TYPE, extra)
    }

    private fun downloadFile(id: String, output: BufferedOutputStream) {
        val file = sharedFiles[id]
        if (file == null) {
            respond(output, 404, "Not Found", jsonError("共享文件已移除"), JSON_TYPE)
            return
        }
        val uri = Uri.parse(file.contentUri)
        val stream = try {
            resolver.openInputStream(uri)
        } catch (_: Exception) {
            null
        }
        if (stream == null) {
            respond(output, 404, "Not Found", jsonError("无法读取共享文件"), JSON_TYPE)
            return
        }

        val name = safeFilename(file.name)
        val disposition = contentDisposition(name)
        val extra = mapOf("Content-Disposition" to disposition)
        stream.use { input ->
            if (file.sizeBytes != null) {
                writeHeaders(output, 200, "OK", file.mimeType, file.sizeBytes, extra)
                copyExactly(input, output, file.sizeBytes)
            } else {
                writeHeaders(output, 200, "OK", file.mimeType, null, extra, chunked = true)
                copyChunked(input, output)
            }
            output.flush()
        }
    }

    private fun downloadInboxFile(id: String, output: BufferedOutputStream) {
        if (!UUID_REGEX.matches(id) || id !in activeInboxIds) {
            respond(output, 404, "Not Found", jsonError("文件不在当前互联收件箱中"), JSON_TYPE)
            return
        }
        val file = receivedFile(id)
        if (file == null) {
            activeInboxIds.remove(id)
            respond(output, 404, "Not Found", jsonError("接收文件已移除"), JSON_TYPE)
            return
        }
        val name = safeFilename(file.name.substringAfter("__", "接收文件"))
        val stream = try {
            file.inputStream()
        } catch (_: Exception) {
            respond(output, 404, "Not Found", jsonError("无法读取接收文件"), JSON_TYPE)
            return
        }
        stream.use { input ->
            writeHeaders(output, 200, "OK", "application/octet-stream", file.length(), mapOf("Content-Disposition" to contentDisposition(name)))
            copyExactly(input, output, file.length())
            output.flush()
        }
    }

    private fun uploadFile(
        input: InputStream,
        output: BufferedOutputStream,
        headers: Map<String, String>,
        requestedName: String,
        sessionToken: String,
    ) {
        val length = headers["content-length"]?.toLongOrNull()
        if (length == null || length < 0L) {
            respond(output, 411, "Length Required", jsonError("浏览器未提供文件大小"), JSON_TYPE)
            return
        }
        if (length > MAX_UPLOAD_BYTES) {
            respond(output, 413, "Content Too Large", jsonError("单个文件最大为 2 GB"), JSON_TYPE)
            return
        }
        inbox.mkdirs()
        val availableBytes = runCatching { StatFs(inbox.absolutePath).availableBytes }.getOrDefault(0L)
        if (availableBytes <= DISK_RESERVE_BYTES || length > availableBytes - DISK_RESERVE_BYTES) {
            respond(output, 507, "Insufficient Storage", jsonError("手机可用空间不足"), JSON_TYPE)
            return
        }

        val id = UUID.randomUUID().toString()
        val name = safeFilename(requestedName).take(180).ifBlank { "接收文件" }
        val partial = File(inbox, "$id.part")
        val destination = File(inbox, "${id}__${name}")
        try {
            partial.outputStream().buffered(64 * 1024).use { fileOutput ->
                val buffer = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) throw IOException("上传提前结束")
                    fileOutput.write(buffer, 0, read)
                    remaining -= read
                }
            }
            if (!partial.renameTo(destination)) throw IOException("无法完成文件保存")
            if (isSessionValid(sessionToken)) activeInboxIds += id
            mutableState.update { it.copy(receivedFiles = loadReceivedFiles(), message = "收到文件：$name", error = null) }
            val response = "{\"ok\":true,\"id\":${json(id)},\"name\":${json(name)},\"size\":$length}"
            respond(output, 201, "Created", response.toByteArray(StandardCharsets.UTF_8), JSON_TYPE)
        } catch (_: Exception) {
            partial.delete()
            destination.delete()
            respond(output, 500, "Internal Server Error", jsonError("接收失败；请检查网络和手机存储空间"), JSON_TYPE)
        }
    }

    private fun canTryPair(remote: String): Boolean {
        val now = System.currentTimeMillis()
        val window = pairAttempts.computeIfAbsent(remote) { PairAttemptWindow(now, 0) }
        synchronized(window) {
            if (now - window.startedAt >= PAIR_WINDOW_MS) {
                window.startedAt = now
                window.count = 0
            }
            window.count += 1
            return window.count <= MAX_PAIR_ATTEMPTS
        }
    }

    private fun isSessionValid(token: String): Boolean {
        val expiresAt = sessions[token] ?: return false
        if (expiresAt > System.currentTimeMillis()) return true
        sessions.remove(token)
        return false
    }

    private fun receivedFile(id: String): File? {
        if (!UUID_REGEX.matches(id)) return null
        inbox.listFiles()?.firstOrNull { it.isFile && it.name.startsWith("${id}__") }?.let { return it }
        return null
    }

    private fun loadReceivedFiles(): List<LinkReceivedFile> {
        inbox.mkdirs()
        return inbox.listFiles()
            .orEmpty()
            .filter { it.isFile && !it.name.endsWith(".part") }
            .mapNotNull { file ->
                val separator = file.name.indexOf("__")
                if (separator <= 0) null else LinkReceivedFile(
                    id = file.name.substring(0, separator),
                    name = file.name.substring(separator + 2),
                    sizeBytes = file.length(),
                    receivedAtMs = file.lastModified(),
                )
            }
            .sortedByDescending { it.receivedAtMs }
    }

    private fun privateIpv4Addresses(): List<InetAddress> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress && !it.isLoopbackAddress }
            .distinctBy { it.hostAddress }
    } catch (_: Exception) {
        emptyList()
    }

    private fun respond(
        output: java.io.OutputStream,
        code: Int,
        status: String,
        body: ByteArray,
        contentType: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ) {
        writeHeaders(output, code, status, contentType, body.size.toLong(), extraHeaders)
        output.write(body)
        output.flush()
    }

    private fun writeHeaders(
        output: java.io.OutputStream,
        code: Int,
        status: String,
        contentType: String,
        length: Long?,
        extraHeaders: Map<String, String> = emptyMap(),
        chunked: Boolean = false,
    ) {
        val headers = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(status).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            if (chunked) append("Transfer-Encoding: chunked\r\n")
            else append("Content-Length: ").append(length ?: 0L).append("\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Referrer-Policy: no-referrer\r\n")
            append("Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; connect-src 'self'; form-action 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'none'\r\n")
            extraHeaders.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
            append("\r\n")
        }
        output.write(headers.toByteArray(StandardCharsets.ISO_8859_1))
    }

    private fun copyExactly(input: InputStream, output: java.io.OutputStream, size: Long) {
        val buffer = ByteArray(64 * 1024)
        var remaining = size
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun copyChunked(input: InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write((read.toString(16) + "\r\n").toByteArray(StandardCharsets.US_ASCII))
            output.write(buffer, 0, read)
            output.write("\r\n".toByteArray(StandardCharsets.US_ASCII))
        }
        output.write("0\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
    }

    private fun readAsciiLine(input: InputStream, maxBytes: Int): String? {
        val bytes = java.io.ByteArrayOutputStream()
        var previous = -1
        while (bytes.size() <= maxBytes) {
            val next = input.read()
            if (next < 0) return if (bytes.size() == 0) null else bytes.toString("US-ASCII")
            if (previous == '\r'.code && next == '\n'.code) {
                val value = bytes.toByteArray()
                return String(value, 0, (value.size - 1).coerceAtLeast(0), StandardCharsets.US_ASCII)
            }
            bytes.write(next)
            previous = next
        }
        throw IOException("HTTP line too long")
    }

    private fun readExact(input: InputStream, size: Int): ByteArray? {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(bytes, offset, size - offset)
            if (read < 0) return null
            offset += read
        }
        return bytes
    }

    private fun parseQuery(query: String): Map<String, String> = query.split('&')
        .filter(String::isNotBlank)
        .mapNotNull { pair ->
            val index = pair.indexOf('=')
            if (index < 0) decode(pair) to "" else decode(pair.substring(0, index)) to decode(pair.substring(index + 1))
        }
        .toMap()

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun cookieValue(cookieHeader: String?, key: String): String? = cookieHeader
        ?.split(';')
        ?.map(String::trim)
        ?.firstOrNull { it.startsWith("$key=") }
        ?.substringAfter('=')

    private fun safeFilename(raw: String): String = raw
        .replace(Regex("[\\u0000-\\u001f\\u007f/\\\\]"), "_")
        .trim()
        .take(180)
        .ifBlank { "文件" }

    private fun safeMimeType(raw: String?): String = raw
        ?.takeIf { MIME_TYPE_REGEX.matches(it) }
        ?: "application/octet-stream"

    private fun contentDisposition(name: String): String {
        val fallback = name.map { if (it.code in 0x20..0x7e && it != '"' && it != '\\') it else '_' }.joinToString("")
        val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return "attachment; filename=\"$fallback\"; filename*=UTF-8''$encoded"
    }

    private fun jsonError(message: String): ByteArray = "{\"error\":${json(message)}}".toByteArray(StandardCharsets.UTF_8)

    private fun json(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }

    private fun sameSecret(first: String, second: String): Boolean = MessageDigest.isEqual(
        first.toByteArray(StandardCharsets.UTF_8),
        second.toByteArray(StandardCharsets.UTF_8),
    )

    private fun randomPairingCode(): String = SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')

    private data class PairAttemptWindow(var startedAt: Long, var count: Int)

    private companion object {
        const val JSON_TYPE = "application/json; charset=utf-8"
        const val CLIENT_TIMEOUT_MS = 120_000
        const val MAX_REQUEST_LINE_BYTES = 8 * 1024
        const val MAX_HEADER_BYTES = 8 * 1024
        const val MAX_TOTAL_HEADER_BYTES = 16 * 1024
        const val MAX_PAIR_BODY_BYTES = 128L
        const val MAX_SHARED_FILES = 100
        const val MAX_UPLOAD_BYTES = 2L * 1024L * 1024L * 1024L
        const val DISK_RESERVE_BYTES = 32L * 1024L * 1024L
        const val SESSION_TTL_MS = 60L * 60L * 1000L
        const val PAIR_WINDOW_MS = 60_000L
        const val MAX_PAIR_ATTEMPTS = 10
        const val MAX_PAIRED_DEVICES = 12
        val UUID_REGEX = Regex("[a-fA-F0-9-]{36}")
        val MIME_TYPE_REGEX = Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+")


    }
}
