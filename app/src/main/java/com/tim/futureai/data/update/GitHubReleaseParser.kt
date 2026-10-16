package com.tim.futureai.data.update

import com.tim.futureai.core.common.JsonMini
import com.tim.futureai.core.common.JsonValue
import com.tim.futureai.feature.update.ApkAsset
import com.tim.futureai.feature.update.AppVersion
import com.tim.futureai.feature.update.ReleaseChannel
import com.tim.futureai.feature.update.UpdateCheckMode
import com.tim.futureai.feature.update.UpdateRelease

class InvalidReleaseResponse(message: String) : Exception(message)

object GitHubReleaseParser {
    private val codePattern = Regex("(?im)^\\s*versionCode\\s*:\\s*(\\d+)\\s*$")
    private val metadataPattern = Regex("(?im)^\\s*versionCode\\s*:\\s*\\d+\\s*$")
    private val digestPattern = Regex("(?i)^sha256:[0-9a-f]{64}$")

    fun parseList(json: String, mode: UpdateCheckMode): List<UpdateRelease> {
        val array = try { JsonMini.parse(json).asArray() } catch (_: Exception) { null }
            ?: throw InvalidReleaseResponse("GitHub 返回了无效的 Release 数据")
        return array.items.mapNotNull { value ->
            val release = value.asObject() ?: return@mapNotNull null
            val fields = release.entries
            if (fields["draft"]?.asBoolean() == true) return@mapNotNull null
            val prerelease = fields["prerelease"]?.asBoolean() ?: return@mapNotNull null
            if (mode == UpdateCheckMode.FORMAL && prerelease) return@mapNotNull null
            val tag = fields["tag_name"]?.asString() ?: return@mapNotNull null
            val tagged = parseTag(tag) ?: return@mapNotNull null
            val body = fields["body"]?.asString().orEmpty()
            val code = codePattern.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val versionName = "${tagged.first} (Revise${tagged.second})"
            val version = AppVersion.parse(versionName, code) ?: return@mapNotNull null
            val channel = if (tag.endsWith("-internal-dev")) ReleaseChannel.INTERNAL_DEV else ReleaseChannel.FORMAL
            val assets = fields["assets"]?.asArray()?.items.orEmpty()
            val apk = assets.mapNotNull { assetValue ->
                val asset = assetValue.asObject()?.entries ?: return@mapNotNull null
                val name = asset["name"]?.asString() ?: return@mapNotNull null
                if (!name.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
                val url = asset["browser_download_url"]?.asString()?.takeIf { it.startsWith("https://github.com/") }
                    ?: return@mapNotNull null
                val digest = asset["digest"]?.asString()?.takeIf { digestPattern.matches(it) }?.substringAfter(':')
                    ?: return@mapNotNull null
                ApkAsset(name, url, digest.lowercase())
            }.firstOrNull()
            UpdateRelease(
                tag = tag,
                versionName = versionName,
                versionCode = code,
                version = version,
                channel = channel,
                notes = body.replace(metadataPattern, "").trim(),
                publishedAt = fields["published_at"]?.asString().orEmpty(),
                isPrerelease = prerelease,
                apk = apk,
            )
        }
    }

    private fun parseTag(tag: String): Pair<String, Int>? {
        val match = Regex("^(v\\d+\\.\\d+\\.\\d+\\.\\d{2})-Revise([1-9]\\d*)(?:-internal-dev)?$").matchEntire(tag)
            ?: return null
        return match.groupValues[1] to (match.groupValues[2].toIntOrNull() ?: return null)
    }
}

private fun JsonValue.asBoolean(): Boolean? = (this as? JsonValue.JBool)?.value
