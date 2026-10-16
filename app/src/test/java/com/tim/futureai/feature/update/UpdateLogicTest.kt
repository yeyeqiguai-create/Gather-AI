package com.tim.futureai.feature.update

import com.tim.futureai.data.update.DownloadResponsePolicy
import com.tim.futureai.data.update.GitHubReleaseParser
import com.tim.futureai.data.update.Sha256Check
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UpdateLogicTest {
    @Test fun versionComparisonAndVersionCodePreventDowngrade() {
        assertTrue(AppVersion.parse("v0.1.3.10 (Revise1)", 11)!! > AppVersion.parse("v0.1.3.09 (Revise9)", 10)!!)
        assertEquals(UpdateCheckResult.UpToDate, UpdateSelector.select(listOf(release("v0.1.3.06 (Revise1)", 10)), "v0.1.3.06 (Revise1)", 10, UpdateCheckMode.INTERNAL_DEV))
        assertTrue(UpdateSelector.select(listOf(release("v0.1.3.06 (Revise1)", 11)), "v0.1.3.06 (Revise1)", 10, UpdateCheckMode.INTERNAL_DEV) is UpdateCheckResult.Available)
        assertTrue(UpdateSelector.select(listOf(release("v0.1.3.06 (Revise1)", 9)), "v0.1.3.06 (Revise1)", 10, UpdateCheckMode.INTERNAL_DEV) is UpdateCheckResult.UpToDate)
    }

    @Test fun internalBuildCanSeePrereleaseButFormalCannot() {
        val parsed = GitHubReleaseParser.parseList(json(prerelease = true), UpdateCheckMode.INTERNAL_DEV)
        assertEquals(1, parsed.size)
        assertEquals(ReleaseChannel.INTERNAL_DEV, parsed.single().channel)
        assertTrue(GitHubReleaseParser.parseList(json(prerelease = true), UpdateCheckMode.FORMAL).isEmpty())
    }

    @Test fun missingApkIsReportedAndDownloadErrorsAreFailures() {
        assertEquals(UpdateCheckResult.NoInstallableApk, UpdateSelector.select(listOf(release("v0.1.3.07 (Revise1)", 11, apk = null)), "v0.1.3.06 (Revise1)", 10, UpdateCheckMode.INTERNAL_DEV))
        try { DownloadResponsePolicy.requireSuccess(503); throw AssertionError("expected failure") } catch (_: IOException) { }
    }

    @Test fun checksumMismatchIsRejected() {
        assertTrue(Sha256Check.matches("a".repeat(64), "A".repeat(64)))
        assertFalse(Sha256Check.matches("a".repeat(64), "b".repeat(64)))
        assertFalse(Sha256Check.matches("bad", "bad"))
    }

    private fun release(name: String, code: Int, apk: ApkAsset? = ApkAsset("app.apk", "https://github.com/a/b.apk", "a".repeat(64))) =
        UpdateRelease("tag", name, code, AppVersion.parse(name, code)!!, ReleaseChannel.INTERNAL_DEV, "notes", "2026-10-16", true, apk)

    private fun json(prerelease: Boolean) = """[{"draft":false,"prerelease":$prerelease,"tag_name":"v0.1.3.06-Revise1-internal-dev","body":"Release notes\nversionCode: 10","published_at":"2026-10-16T00:00:00Z","assets":[{"name":"app.apk","browser_download_url":"https://github.com/a/b/releases/app.apk","digest":"sha256:${"a".repeat(64)}"}]}]"""
}
