package com.tim.futureai.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.tim.futureai.feature.update.UpdateCheckMode
import com.tim.futureai.feature.update.UpdateCheckResult
import com.tim.futureai.feature.update.UpdateRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class GitHubUpdateRepository(
    private val currentVersionName: String,
    private val currentVersionCode: Int,
    private val isInternalBuild: Boolean,
) {
    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val mode = if (isInternalBuild) UpdateCheckMode.INTERNAL_DEV else UpdateCheckMode.FORMAL
        try {
            val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "Gather-AI-Android")
            }
            try {
                val status = connection.responseCode
                if (status == 403 || status == 429) {
                    return@withContext UpdateCheckResult.Failed("GitHub API 暂时限流，请稍后重试")
                }
                if (status !in 200..299) return@withContext UpdateCheckResult.Failed("更新检查失败（HTTP $status）")
                val releases = GitHubReleaseParser.parseList(connection.inputStream.bufferedReader().use { it.readText() }, mode)
                com.tim.futureai.feature.update.UpdateSelector.select(
                    releases, currentVersionName, currentVersionCode, mode,
                )
            } finally { connection.disconnect() }
        } catch (error: InvalidReleaseResponse) {
            UpdateCheckResult.InvalidMetadata(error.message ?: "GitHub 返回了无效数据")
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            UpdateCheckResult.Failed("无法连接 GitHub，请检查网络后重试")
        }
    }

    suspend fun downloadAndVerify(
        context: Context,
        release: UpdateRelease,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val asset = release.apk ?: error("Release 没有 APK")
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, asset.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        val partial = File(directory, target.name + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val connection = (URL(asset.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Gather-AI-Android")
            }
            try {
                DownloadResponsePolicy.requireSuccess(connection.responseCode)
                val total = connection.contentLengthLong
                require(total <= MAX_APK_BYTES || total < 0) { "APK 文件超出允许大小" }
                var received = 0L
                var lastPercent = -1
                connection.inputStream.use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(received + count <= MAX_APK_BYTES) { "APK 文件超出允许大小" }
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            received += count
                            if (total > 0) {
                                val percent = (received * 100 / total).toInt().coerceIn(0, 99)
                                if (percent > lastPercent) { lastPercent = percent; onProgress(percent) }
                            }
                        }
                    }
                }
            } finally { connection.disconnect() }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(Sha256Check.matches(actual, asset.sha256)) { "APK SHA-256 校验失败，已拒绝安装" }
            require(hasCompatibleSignature(context, partial)) { "APK 签名与当前应用不兼容，不能覆盖安装" }
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "无法保存下载的 APK" }
            onProgress(100)
            target
        } catch (error: Exception) {
            partial.delete()
            throw error
        }
    }

    private fun hasCompatibleSignature(context: Context, apk: File): Boolean {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        if (archive.packageName != context.packageName) return false
        if (Build.VERSION.SDK_INT < 28) return signatureHashes(installed.signatures) == signatureHashes(archive.signatures)
        val current = installed.signingInfo ?: return false
        val candidate = archive.signingInfo ?: return false
        if (current.hasMultipleSigners() || candidate.hasMultipleSigners()) {
            return signatureHashes(current.apkContentsSigners) == signatureHashes(candidate.apkContentsSigners)
        }
        val installedCurrentSigner = signatureHashes(current.apkContentsSigners).singleOrNull() ?: return false
        return installedCurrentSigner in signatureHashes(candidate.signingCertificateHistory)
    }

    @Suppress("DEPRECATION")
    private fun signatureHashes(signatures: Array<android.content.pm.Signature>?): Set<String> = signatures.orEmpty()
        .map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }
        .toSet()

    companion object {
        private const val MAX_APK_BYTES = 300L * 1024 * 1024
        private const val RELEASES_URL = "https://api.github.com/repos/yeyeqiguai-create/Gather-AI/releases?per_page=100"
    }
}

object DownloadResponsePolicy {
    fun requireSuccess(status: Int) {
        if (status !in 200..299) throw IOException("APK 下载失败（HTTP $status）")
    }
}

object Sha256Check {
    fun matches(actual: String, expected: String): Boolean =
        expected.matches(Regex("(?i)[0-9a-f]{64}")) && actual.equals(expected, ignoreCase = true)
}
