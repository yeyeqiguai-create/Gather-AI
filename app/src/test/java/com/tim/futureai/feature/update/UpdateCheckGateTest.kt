package com.tim.futureai.feature.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckGateTest {
    @Test fun inFlightChecksAreBlockedAndRecoverAfterCooldown() {
        var now = 1_000L
        val gate = UpdateCheckGate(clock = { now }, cooldownMs = 30_000L)
        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())
        gate.finish()
        assertFalse(gate.tryStart())
        now += 29_999
        assertFalse(gate.tryStart())
        now += 1
        assertTrue(gate.tryStart())
    }

    @Test fun failureStillReleasesTheGateAndResetClearsCooldown() {
        var now = 5_000L
        val gate = UpdateCheckGate(clock = { now }, cooldownMs = 10_000L)
        assertTrue(gate.tryStart())
        gate.finish(success = false)
        assertFalse(gate.tryStart())
        gate.reset()
        assertTrue(gate.tryStart())
    }
}