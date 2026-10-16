package com.tim.futureai.feature.update

import com.tim.futureai.data.update.DownloadResponsePolicy
import com.tim.futureai.data.update.GitHubReleaseParser
import com.tim.futureai.data.update.Sha256Check
import com.tim.futureai.data.update.UpdateMetadataParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

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

    @Test fun targetBetaTagAndVersionCodeParseForInternalClients() {
        val releaseJson = """[{"draft":false,"prerelease":true,"tag_name":"v0.1.5.01-Revise1-internal-dev","body":"Provider routing\n<div hidden>\nversionCode: 13\n</div>","published_at":"2026-10-16T00:00:00Z","assets":[]}]"""
        val release = GitHubReleaseParser.parseList(releaseJson, UpdateCheckMode.INTERNAL_DEV).single()
        assertEquals("v0.1.5.01 (Revise1)", release.versionName)
        assertEquals(13, release.versionCode)
        assertEquals(ReleaseChannel.INTERNAL_DEV, release.channel)
        assertTrue(GitHubReleaseParser.parseList(releaseJson, UpdateCheckMode.FORMAL).isEmpty())
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

    @Test fun releaseMetadataAndApkAreMatchedByVersionAndVerifiedDigest() {
        val digest = "b".repeat(64)
        val json = """[{"draft":false,"prerelease":true,"tag_name":"v0.1.4.02-Revise2-internal-dev","body":"New features\n<div hidden>\nversionCode: 12\n</div>","published_at":"2026-10-16T00:00:00Z","assets":[{"name":"old.apk","browser_download_url":"https://github.com/a/b/releases/old.apk","digest":"sha256:${"a".repeat(64)}","size":12},{"name":"Gather-AI-v0.1.4.02-Revise2-internal-dev.apk","browser_download_url":"https://github.com/a/b/releases/app.apk","digest":"sha256:$digest","size":42},{"name":"update.json","browser_download_url":"https://github.com/a/b/releases/update.json","digest":"sha256:${"c".repeat(64)}","size":123}]}]"""
        val release = GitHubReleaseParser.parseList(json, UpdateCheckMode.INTERNAL_DEV).single()
        assertEquals("Gather-AI-v0.1.4.02-Revise2-internal-dev.apk", release.apk?.fileName)
        assertEquals(42L, release.apk?.sizeBytes)
        assertEquals("New features", release.notes)
        assertTrue(release.metadataUrl?.endsWith("/update.json?download=1&sha256=${"c".repeat(64)}") == true)
        assertTrue(release.apk?.downloadUrl?.endsWith("app.apk?download=1&sha256=$digest") == true)
        val metadata = UpdateMetadataParser.parse(
            UpdateMetadataParser.encode(com.tim.futureai.feature.update.UpdateMetadata(
                release.versionName, 12, "New features", release.apk!!.downloadUrl, digest,
            )),
        )
        assertEquals("New features", UpdateMetadataParser.validate(metadata, release).releaseNotes)
        try {
            UpdateMetadataParser.validate(metadata.copy(sha256 = "d".repeat(64)), release)
            throw AssertionError("expected metadata mismatch")
        } catch (_: com.tim.futureai.data.update.InvalidReleaseResponse) { }
    }

    @Test fun missingLegacyVersionCodeIsReportedInsteadOfShowingLatest() {
        val parsed = GitHubReleaseParser.parseDetailed(
            """[{"draft":false,"prerelease":true,"tag_name":"v0.1.4.02-Revise2-internal-dev","body":"Notes only","assets":[]}]""",
            UpdateCheckMode.INTERNAL_DEV,
        )
        assertTrue(parsed.releases.isEmpty())
        val result = UpdateSelector.select(
            parsed.releases, "v0.1.3.06 (Revise1)", 10, UpdateCheckMode.INTERNAL_DEV, parsed.rejected,
        )
        assertTrue(result is UpdateCheckResult.InvalidMetadata)
    }

    @Test fun manualChecksHaveNoCooldownAndOnlyBlockWhileInFlight() {
        val gate = UpdateCheckGate()
        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())
        gate.finish()
        assertTrue(gate.tryStart())
        gate.finish()
        assertTrue(gate.tryStart())

        val available = UpdateCheckResult.Available(release("v0.1.3.07 (Revise1)", 11))
        assertFalse(UpdateSectionControls.forState(available, true, null).checkEnabled)
        assertTrue(UpdateSectionControls.forState(available, false, null).checkEnabled)
        assertTrue(UpdateSectionControls.forState(available, false, 30).cancelEnabled)
        assertTrue(UpdateSectionControls.forState(available, false, 100).installEnabled)
        assertTrue(UpdateSectionControls.forState(available, false, 50).checkEnabled)
    }

    @Test fun startupAutomaticallyChecksOncePerActivityStart() {
        val startup = StartupUpdateCheck()
        assertTrue(startup.shouldCheck())
        assertFalse(startup.shouldCheck())
        assertTrue(StartupUpdateCheck().shouldCheck())
    }

    @Test fun updatePromptSupportsImmediateLaterAndFullNotesActions() {
        val prompt = UpdatePromptState(release("v0.1.3.07 (Revise1)", 11))
        assertTrue(UpdatePromptReducer.reduce(prompt, UpdatePromptAction.VIEW_NOTES)!!.showFullNotes)
        assertEquals(null, UpdatePromptReducer.reduce(prompt, UpdatePromptAction.LATER))
        assertEquals(null, UpdatePromptReducer.reduce(prompt, UpdatePromptAction.START_UPDATE))
    }

    @Test fun installerAuthorizationAndResumeFollowAndroidApiLevel() {
        assertTrue(UpdateInstallFlow.requiresPerSourceAuthorization(26, false))
        assertTrue(UpdateInstallFlow.requiresPerSourceAuthorization(37, false))
        assertFalse(UpdateInstallFlow.requiresPerSourceAuthorization(25, false))
        assertFalse(UpdateInstallFlow.requiresPerSourceAuthorization(37, true))
        assertTrue(UpdateInstallFlow.shouldResumeAfterAuthorization(true, 26, true))
        assertFalse(UpdateInstallFlow.shouldResumeAfterAuthorization(true, 26, false))
        assertTrue(UpdateInstallFlow.shouldResumeAfterAuthorization(true, 25, false))
        assertFalse(UpdateInstallFlow.shouldResumeAfterAuthorization(false, 37, true))
    }

    @Test fun installerResultAndRestartConfirmTheTargetVersion() {
        assertTrue(UpdateInstallFlow.installerReportedSuccess(-1, 12, 12))
        assertTrue(UpdateInstallFlow.installerReportedSuccess(-1, 13, 12))
        assertFalse(UpdateInstallFlow.installerReportedSuccess(0, 12, 12))
        assertFalse(UpdateInstallFlow.installerReportedSuccess(-1, 11, 12))
        assertTrue(UpdateInstallFlow.completedDuringProcessRestart(12, 12, 11, 100L, 100L))
        assertTrue(UpdateInstallFlow.completedDuringProcessRestart(12, 12, 12, 100L, 101L))
        assertFalse(UpdateInstallFlow.completedDuringProcessRestart(12, 12, 12, 100L, 100L))
        assertFalse(UpdateInstallFlow.completedDuringProcessRestart(11, 12, 10, 100L, 101L))
    }

    @Test fun networkFailureCanBeRetriedImmediately() {
        val calls = AtomicInteger()
        val repository = com.tim.futureai.data.update.GitHubUpdateRepository(
            "v0.1.3.06 (Revise1)", 10, true,
            releasesUrl = "https://example.invalid/releases",
            textFetcher = { if (calls.incrementAndGet() == 1) throw IOException("network unavailable") else "[]" },
        )
        assertTrue(runSuspend { repository.check() } is UpdateCheckResult.Failed)
        assertEquals(UpdateCheckResult.UpToDate, runSuspend { repository.check() })
        assertEquals(2, calls.get())
    }

    @Test fun malformedReleaseVersionAndMetadataAreRejected() {
        assertEquals(null, AppVersion.parse("v0.1.4.1 (Revise2)", 12))
        val badTag = GitHubReleaseParser.parseDetailed(
            """[{"draft":false,"prerelease":true,"tag_name":"v0.1.4.1-Revise2-internal-dev","body":"Notes\nversionCode: 12","assets":[]}]""",
            UpdateCheckMode.INTERNAL_DEV,
        )
        assertTrue(UpdateSelector.select(badTag.releases, "v0.1.3.06 (Revise1)", 10, UpdateCheckMode.INTERNAL_DEV, badTag.rejected) is UpdateCheckResult.InvalidMetadata)
        try {
            UpdateMetadataParser.parse("""{"versionName":"v0.1.4.02 (Revise2)","versionCode":0,"releaseNotes":"","apkUrl":"https://github.com/a/b.apk","sha256":"${"a".repeat(64)}"}""")
            throw AssertionError("expected invalid metadata")
        } catch (_: com.tim.futureai.data.update.InvalidReleaseResponse) { }
    }

    private fun release(name: String, code: Int, apk: ApkAsset? = ApkAsset("app.apk", "https://github.com/a/b.apk", "a".repeat(64))) =
        UpdateRelease("tag", name, code, AppVersion.parse(name, code)!!, ReleaseChannel.INTERNAL_DEV, "notes", "2026-10-16", true, apk)

    private fun json(prerelease: Boolean) = """[{"draft":false,"prerelease":$prerelease,"tag_name":"v0.1.3.06-Revise1-internal-dev","body":"Release notes\nversionCode: 10","published_at":"2026-10-16T00:00:00Z","assets":[{"name":"app.apk","browser_download_url":"https://github.com/a/b/releases/app.apk","digest":"sha256:${"a".repeat(64)}"}]}]"""

    private fun <T> runSuspend(block: suspend () -> T): T {
        val latch = CountDownLatch(1)
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { outcome = result; latch.countDown() }
        })
        check(latch.await(5, TimeUnit.SECONDS)) { "suspend call timed out" }
        return outcome!!.getOrThrow()
    }
}
