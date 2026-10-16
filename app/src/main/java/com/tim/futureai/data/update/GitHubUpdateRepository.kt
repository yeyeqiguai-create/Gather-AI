package com.tim.futureai.data.update

import android.content.pm.PackageManager
import android.app.DownloadManager
import android.os.Build
import android.os.Environment
import android.database.Cursor
import android.net.Uri
import android.content.Context
import com.tim.futureai.feature.update.UpdateCheckMode
import com.tim.futureai.feature.update.UpdateCheckResult
import com.tim.futureai.feature.update.UpdateRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class GitHubUpdateRepository(
    private val currentVersionName: String,
    private val currentVersionCode: Int,
    private val isInternalBuild: Boolean,
    private val releasesUrl: String = RELEASES_URL,
    private val textFetcher: (String) -> String = ::fetchUpdateText,
) {
    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val mode = if (isInternalBuild) UpdateCheckMode.INTERNAL_DEV else UpdateCheckMode.FORMAL
        try {
            val response = textFetcher(releasesUrl)
            val parsed = GitHubReleaseParser.parseDetailed(response, mode)
            when (val selected = com.tim.futureai.feature.update.UpdateSelector.select(
                parsed.releases, currentVersionName, currentVersionCode, mode, parsed.rejected,
            )) {
                is UpdateCheckResult.Available -> {
                    val metadataUrl = selected.release.metadataUrl ?: return@withContext selected
                    val metadata = UpdateMetadataParser.validate(
                        UpdateMetadataParser.parse(textFetcher(metadataUrl)), selected.release,
                    )
                    UpdateCheckResult.Available(selected.release.copy(notes = metadata.releaseNotes))
                }
                else -> selected
            }
        } catch (error: InvalidReleaseResponse) {
            UpdateCheckResult.InvalidMetadata(error.message ?: "GitHub 返回了无效数据")
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            UpdateCheckResult.Failed(error.message ?: "无法连接 GitHub，请检查网络后重试")
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
        require(asset.sizeBytes == null || asset.sizeBytes <= MAX_APK_BYTES) { "APK 文件超出允许大小" }
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("Android 下载目录不可用")
        val target = File(directory, asset.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        if (target.isFile && target.length() <= MAX_APK_BYTES && verifyDownloadedApk(context, target, asset.sha256)) {
            onProgress(100)
            return@withContext target
        }
        target.delete()
        val manager = context.getSystemService(DownloadManager::class.java) ?: error("系统下载服务不可用")
        val preferences = context.getSharedPreferences("update_downloads", Context.MODE_PRIVATE)
        val preferenceKey = "download_${release.tag.replace(Regex("[^A-Za-z0-9_-]"), "_")}"
        var downloadId = preferences.getLong(preferenceKey, -1L)
        try {
            if (downloadId < 0 || downloadSnapshot(manager, downloadId) == null) {
                val request = DownloadManager.Request(Uri.parse(asset.downloadUrl))
                    .setTitle("Gather AI 更新")
                    .setDescription("正在下载 ${release.versionName}")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, target.name)
                    .addRequestHeader("User-Agent", "Gather-AI-Android")
                downloadId = manager.enqueue(request)
                preferences.edit().putLong(preferenceKey, downloadId).apply()
            }
            while (true) {
                delay(350)
                val snapshot = downloadSnapshot(manager, downloadId) ?: error("下载任务已丢失，请重新下载")
                when (snapshot.status) {
                    DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                        val percent = if (snapshot.total > 0) (snapshot.downloaded * 100 / snapshot.total).toInt().coerceIn(0, 99) else 0
                        onProgress(percent)
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> break
                    DownloadManager.STATUS_FAILED -> {
                        val reason = when (snapshot.reason) {
                            DownloadManager.ERROR_CANNOT_RESUME -> "网络中断，无法续传"
                            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "设备存储空间不足"
                            DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "服务器下载失败（HTTP ${snapshot.reason}）"
                            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "下载存储不可用"
                            else -> "下载失败（${snapshot.reason}）"
                        }
                        manager.remove(downloadId)
                        preferences.edit().remove(preferenceKey).apply()
                        target.delete()
                        error("$reason，可立即重试")
                    }
                }
            }
            require(target.length() <= MAX_APK_BYTES && verifyDownloadedApk(context, target, asset.sha256)) {
                "APK SHA-256 或签名校验失败，已拒绝安装；可重新下载"
            }
            onProgress(100)
            target
        } catch (error: CancellationException) {
            manager.remove(downloadId)
            preferences.edit().remove(preferenceKey).apply()
            target.delete()
            throw error
        } catch (error: Exception) {
            manager.remove(downloadId)
            preferences.edit().remove(preferenceKey).apply()
            target.delete()
            throw error
        }
    }

    suspend fun isVerifiedApk(context: Context, apk: File, expectedSha256: String): Boolean =
        withContext(Dispatchers.IO) {
            apk.isFile && apk.length() in 1..MAX_APK_BYTES &&
                verifyDownloadedApk(context, apk, expectedSha256)
        }

    private fun verifyDownloadedApk(context: Context, apk: File, expectedSha256: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return Sha256Check.matches(actual, expectedSha256) && hasCompatibleSignature(context, apk)
    }

    private fun downloadSnapshot(manager: DownloadManager, id: Long): DownloadSnapshot? {
        val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            return DownloadSnapshot(
                status = it.intColumn(DownloadManager.COLUMN_STATUS),
                reason = it.intColumn(DownloadManager.COLUMN_REASON),
                downloaded = it.longColumn(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                total = it.longColumn(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
            )
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

private data class DownloadSnapshot(val status: Int, val reason: Int, val downloaded: Long, val total: Long)

private fun Cursor.intColumn(name: String): Int = getInt(getColumnIndexOrThrow(name))
private fun Cursor.longColumn(name: String): Long = getLong(getColumnIndexOrThrow(name))

private fun fetchUpdateText(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 15_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        setRequestProperty("User-Agent", "Gather-AI-Android")
        instanceFollowRedirects = true
    }
    try {
        val status = connection.responseCode
        if (status == 403 || status == 429) throw IOException("GitHub API 暂时限流，请稍后重试")
        if (status !in 200..299) throw IOException("更新源请求失败（HTTP $status）")
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally { connection.disconnect() }
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
