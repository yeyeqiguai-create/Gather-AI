package com.tim.futureai.data.update

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.feature.update.ApkAsset
import com.tim.futureai.feature.update.AppVersion
import com.tim.futureai.feature.update.RejectedRelease
import com.tim.futureai.feature.update.ReleaseChannel
import com.tim.futureai.feature.update.UpdateCheckMode
import com.tim.futureai.feature.update.UpdateMetadata
import com.tim.futureai.feature.update.UpdateRelease

class InvalidReleaseResponse(message: String) : Exception(message)

data class ParsedReleaseList(val releases: List<UpdateRelease>, val rejected: List<RejectedRelease>)

object GitHubReleaseParser {
    private val codePattern = Regex("(?im)^\\s*versionCode\\s*:\\s*(\\d+)\\s*$")
    private val metadataLine = Regex("(?im)^\\s*(?:versionCode|versionName)\\s*:\\s*.*(?:\\r?\\n|$)")
    private val hiddenMetadataBlock = Regex("(?is)<div\\s+hidden\\s*>.*?</div>")
    private val digestPattern = Regex("(?i)^sha256:[0-9a-f]{64}$")
    private val tagPattern = Regex("^(v\\d+\\.\\d+\\.\\d+\\.\\d{2})-Revise([1-9]\\d*)(-internal-dev)?$")
    private val candidateTagPattern = Regex("^(v(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+))-Revise(\\d+)(-internal-dev)?$")

    fun parseList(json: String, mode: UpdateCheckMode): List<UpdateRelease> = parseDetailed(json, mode).releases

    fun parseDetailed(json: String, mode: UpdateCheckMode): ParsedReleaseList {
        val array = try { JsonMini.parse(json).asArray() } catch (_: Exception) { null }
            ?: throw InvalidReleaseResponse("GitHub 返回了无效的 Release 数据")
        val releases = mutableListOf<UpdateRelease>()
        val rejected = mutableListOf<RejectedRelease>()
        array.items.forEach { value ->
            val fields = value.asObject()?.entries ?: return@forEach
            if (fields["draft"]?.asBoolean() == true) return@forEach
            val prerelease = fields["prerelease"]?.asBoolean() ?: return@forEach
            if (mode == UpdateCheckMode.FORMAL && prerelease) return@forEach
            val tag = fields["tag_name"]?.asString() ?: return@forEach
            val tagMatch = tagPattern.matchEntire(tag)
            val candidateMatch = candidateTagPattern.matchEntire(tag) ?: return@forEach
            val baseName = candidateMatch.groupValues[1]
            val revise = candidateMatch.groupValues[6].toIntOrNull()
            val versionName = revise?.let { "$baseName (Revise$it)" } ?: return@forEach
            val code = codePattern.find(fields["body"]?.asString().orEmpty())?.groupValues?.get(1)?.toIntOrNull()
            val version = AppVersion.parse(versionName, code?.takeIf { it > 0 } ?: 1)
                ?: runCatching {
                    AppVersion(
                        candidateMatch.groupValues[2].toInt(), candidateMatch.groupValues[3].toInt(),
                        candidateMatch.groupValues[4].toInt(), candidateMatch.groupValues[5].toInt(),
                        revise, code?.takeIf { it > 0 } ?: 1,
                    )
                }.getOrNull() ?: return@forEach
            val channel = if (candidateMatch.groupValues[7].isNotEmpty()) ReleaseChannel.INTERNAL_DEV else ReleaseChannel.FORMAL
            if (tagMatch == null || AppVersion.parse(versionName, code ?: 0) == null || code == null || code <= 0) {
                val reason = if (tagMatch == null) "版本标签格式无效" else "缺少有效的 versionCode"
                rejected += RejectedRelease(version, tag, reason, channel, prerelease)
                return@forEach
            }

            val assets = fields["assets"]?.asArray()?.items.orEmpty().mapNotNull { it.asObject()?.entries }
            val apkAssets = assets.filter { it["name"]?.asString()?.endsWith(".apk", ignoreCase = true) == true }
            val pickedApk = selectApk(apkAssets, tag)
            val apk = pickedApk?.let { asset ->
                val name = asset["name"]?.asString() ?: return@let null
                val url = asset["browser_download_url"]?.asString()?.takeIf { it.startsWith("https://github.com/") }
                    ?: return@let null
                val digest = asset["digest"]?.asString()?.takeIf { digestPattern.matches(it) }?.substringAfter(':')
                    ?: return@let null
                val size = (asset["size"] as? JsonValue.JNumber)?.value?.toLong()?.takeIf { it >= 0 }
                ApkAsset(name, withDigestCacheKey(url, digest.substringAfter(':')), digest.substringAfter(':').lowercase(), size)
            }
            val metadataAsset = assets.firstOrNull { it["name"]?.asString() == "update.json" }
            val metadataUrl = metadataAsset?.get("browser_download_url")?.asString()
                ?.takeIf { it.startsWith("https://github.com/") }
                ?.let { url ->
                    val digest = metadataAsset["digest"]?.asString()?.takeIf { digestPattern.matches(it) }?.substringAfter(':')
                    digest?.let { withDigestCacheKey(url, it) }
                }
            val body = fields["body"]?.asString().orEmpty()
            releases += UpdateRelease(
                tag = tag,
                versionName = versionName,
                versionCode = code,
                version = AppVersion.parse(versionName, code)!!,
                channel = channel,
                notes = body.replace(hiddenMetadataBlock, "").replace(metadataLine, "").trim(),
                publishedAt = fields["published_at"]?.asString().orEmpty(),
                isPrerelease = prerelease,
                apk = apk,
                metadataUrl = metadataUrl,
            )
            if (apkAssets.isNotEmpty() && apk == null) {
                rejected += RejectedRelease(version, tag, "APK 资源缺少有效下载链接或 SHA-256", channel, prerelease)
            }
        }
        return ParsedReleaseList(releases, rejected)
    }

    private fun selectApk(assets: List<Map<String, JsonValue>>, tag: String): Map<String, JsonValue>? {
        if (assets.size == 1) return assets.single()
        val versionPart = tag.removeSuffix("-internal-dev").removePrefix("v").lowercase()
        return assets.singleOrNull { asset ->
            val name = asset["name"]?.asString()?.lowercase().orEmpty()
            name.contains(versionPart) && (tag.endsWith("-internal-dev") == name.contains("internal-dev"))
        }
    }

    private fun withDigestCacheKey(url: String, digest: String): String =
        "$url${if ('?' in url) "&" else "?"}download=1&sha256=$digest"
}

object UpdateMetadataParser {
    fun encode(metadata: UpdateMetadata): String = """
        {"versionName":${JsonMini.quote(metadata.versionName)},"versionCode":${metadata.versionCode},"releaseNotes":${JsonMini.quote(metadata.releaseNotes)},"apkUrl":${JsonMini.quote(metadata.apkUrl)},"sha256":${JsonMini.quote(metadata.sha256.lowercase())}}
    """.trimIndent()

    fun parse(json: String): UpdateMetadata {
        val fields = try { JsonMini.parse(json).asObject()?.entries } catch (_: Exception) { null }
            ?: throw InvalidReleaseResponse("更新元数据 JSON 无效")
        val versionName = fields["versionName"]?.asString()?.takeIf(String::isNotBlank)
            ?: throw InvalidReleaseResponse("更新元数据缺少 versionName")
        val versionCode = (fields["versionCode"] as? JsonValue.JNumber)?.value
            ?.takeIf { it > 0 && it % 1.0 == 0.0 }?.toInt()
            ?: throw InvalidReleaseResponse("更新元数据缺少有效 versionCode")
        if (AppVersion.parse(versionName, versionCode) == null) throw InvalidReleaseResponse("更新元数据版本号无效")
        val notes = fields["releaseNotes"]?.asString()
            ?: throw InvalidReleaseResponse("更新元数据缺少 releaseNotes")
        val apkUrl = fields["apkUrl"]?.asString()?.takeIf { it.startsWith("https://github.com/") }
            ?: throw InvalidReleaseResponse("更新元数据 APK 链接无效")
        val sha256 = fields["sha256"]?.asString()?.takeIf { it.matches(Regex("(?i)[0-9a-f]{64}")) }?.lowercase()
            ?: throw InvalidReleaseResponse("更新元数据 SHA-256 无效")
        return UpdateMetadata(versionName, versionCode, notes, apkUrl, sha256)
    }

    fun validate(metadata: UpdateMetadata, release: UpdateRelease): UpdateMetadata {
        val asset = release.apk ?: throw InvalidReleaseResponse("Release 缺少可验证的 APK")
        if (metadata.versionName != release.versionName || metadata.versionCode != release.versionCode) {
            throw InvalidReleaseResponse("更新元数据版本与 Release 不一致")
        }
        if (metadata.apkUrl != asset.downloadUrl || !metadata.sha256.equals(asset.sha256, ignoreCase = true)) {
            throw InvalidReleaseResponse("更新元数据 APK 链接或 SHA-256 与 GitHub 资源不一致")
        }
        if (metadata.releaseNotes != release.notes) throw InvalidReleaseResponse("更新说明与兼容 Release 正文不一致")
        return metadata
    }
}

private fun JsonValue.asBoolean(): Boolean? = (this as? JsonValue.JBool)?.value
