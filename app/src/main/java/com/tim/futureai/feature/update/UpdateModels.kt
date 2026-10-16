package com.tim.futureai.feature.update

data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val mini: Int,
    val revise: Int,
    val versionCode: Int,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch, AppVersion::mini, AppVersion::revise)

    companion object {
        private val pattern = Regex("^v(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d{2}) \\(Revise([1-9]\\d*)\\)$")

        fun parse(versionName: String, versionCode: Int): AppVersion? {
            val match = pattern.matchEntire(versionName) ?: return null
            val numbers = (1..5).map { match.groupValues[it].toIntOrNull() ?: return null }
            if (versionCode <= 0) return null
            return AppVersion(numbers[0], numbers[1], numbers[2], numbers[3], numbers[4], versionCode)
        }
    }
}

enum class ReleaseChannel(val label: String) { FORMAL("正式版"), INTERNAL_DEV("内部开发") }

data class ApkAsset(val fileName: String, val downloadUrl: String, val sha256: String)

data class UpdateRelease(
    val tag: String,
    val versionName: String,
    val versionCode: Int,
    val version: AppVersion,
    val channel: ReleaseChannel,
    val notes: String,
    val publishedAt: String,
    val isPrerelease: Boolean,
    val apk: ApkAsset?,
)

sealed interface UpdateCheckResult {
    data class Available(val release: UpdateRelease) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object NoInstallableApk : UpdateCheckResult
    data class InvalidMetadata(val message: String) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

enum class UpdateCheckMode { FORMAL, INTERNAL_DEV }
