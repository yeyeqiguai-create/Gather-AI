package com.tim.futureai.feature.update

/** Pure policy for the Android package-installer handoff. */
object UpdateInstallFlow {
    const val RESULT_OK = -1

    fun requiresPerSourceAuthorization(apiLevel: Int, canRequestPackageInstalls: Boolean): Boolean =
        apiLevel >= 26 && !canRequestPackageInstalls

    fun shouldResumeAfterAuthorization(pendingInstall: Boolean, apiLevel: Int, authorized: Boolean): Boolean =
        pendingInstall && (apiLevel < 26 || authorized)

    fun installerReportedSuccess(resultCode: Int, installedVersionCode: Int, targetVersionCode: Int): Boolean =
        resultCode == RESULT_OK && targetVersionCode > 0 && installedVersionCode >= targetVersionCode

    fun completedDuringProcessRestart(
        installedVersionCode: Int,
        targetVersionCode: Int,
        previousVersionCode: Int,
        previousLastUpdateTime: Long,
        currentLastUpdateTime: Long,
    ): Boolean = targetVersionCode > 0 &&
        installedVersionCode >= targetVersionCode &&
        (installedVersionCode > previousVersionCode || currentLastUpdateTime > previousLastUpdateTime)
}
