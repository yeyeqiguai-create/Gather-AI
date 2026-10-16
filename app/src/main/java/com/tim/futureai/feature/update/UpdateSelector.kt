package com.tim.futureai.feature.update

object UpdateSelector {
    fun select(
        releases: List<UpdateRelease>,
        currentVersionName: String,
        currentVersionCode: Int,
        mode: UpdateCheckMode,
    ): UpdateCheckResult {
        val current = AppVersion.parse(currentVersionName, currentVersionCode)
            ?: return UpdateCheckResult.InvalidMetadata("当前应用版本信息无效")
        val eligible = releases.asSequence()
            .filter { it.version.major == 0 && it.version.minor == 1 }
            .filter { mode == UpdateCheckMode.INTERNAL_DEV || !it.isPrerelease }
            .filter { it.channel == ReleaseChannel.FORMAL || mode == UpdateCheckMode.INTERNAL_DEV }
            .sortedWith(compareByDescending<UpdateRelease> { it.version }.thenByDescending { it.versionCode })
            .toList()
        val newest = eligible.firstOrNull() ?: return UpdateCheckResult.UpToDate
        val versionOrder = newest.version.compareTo(current)
        if (versionOrder == 0 && newest.versionCode <= currentVersionCode) return UpdateCheckResult.UpToDate
        if (versionOrder < 0 || newest.versionCode <= currentVersionCode) return UpdateCheckResult.InvalidMetadata(
            "Release 版本号与 versionCode 不一致，已跳过以防止降级或覆盖安装失败",
        )
        val apk = newest.apk ?: return UpdateCheckResult.NoInstallableApk
        if (apk.sha256.length != 64 || apk.sha256.any { it !in '0'..'9' && it !in 'a'..'f' }) {
            return UpdateCheckResult.InvalidMetadata("Release 的 APK 校验信息无效")
        }
        return UpdateCheckResult.Available(newest)
    }
}
