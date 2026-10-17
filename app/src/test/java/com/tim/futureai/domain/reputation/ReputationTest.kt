package com.tim.futureai.domain.reputation

import com.tim.futureai.domain.ai.ModelTask
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryReputationRepository : ReputationRepository {
    val records = mutableListOf<ReputationRecord>()
    override suspend fun record(modelKey: String, category: ModelTask): ReputationRecord? =
        records.firstOrNull { it.modelKey == modelKey && it.category == category }
    override suspend fun upsert(record: ReputationRecord) {
        records.removeAll { it.modelKey == record.modelKey && it.category == record.category }
        records.add(record)
    }
    override suspend fun all(): List<ReputationRecord> = records.toList()
    override suspend fun clear(modelKey: String?, category: ModelTask?) {
        records.removeAll { (modelKey == null || it.modelKey == modelKey) && (category == null || it.category == category) }
    }
}

class ReputationTest {
    private var now = 1_000L

    @Test fun insufficientSamplesNeverProduceAScore() = runBlocking {
        val store = ReputationStore(MemoryReputationRepository(), clock = { now })
        repeat(3) { store.record("m · gpt", ModelTask.CODING, success = true, failureType = null, latencyMs = 100, costUsd = 0.01, reason = "r", source = "TASK") }
        val view = store.view("m · gpt", ModelTask.CODING)!!
        assertEquals(3, view.sampleSize)
        assertFalse(view.sufficientSamples)
        assertNull(view.successRate) // no fabricated score
    }

    @Test fun statsArePerTaskTypeAndFailuresAreTyped() = runBlocking {
        val store = ReputationStore(MemoryReputationRepository(), clock = { now })
        repeat(4) { store.record("m · gpt", ModelTask.CODING, true, null, 200, 0.02, "r", "TASK") }
        store.record("m · gpt", ModelTask.CODING, false, "objective_mismatch", 300, 0.02, "r", "ARENA")
        store.record("m · gpt", ModelTask.REASONING, true, null, 100, 0.01, "r", "TASK")
        val coding = store.view("m · gpt", ModelTask.CODING)!!
        assertTrue(coding.sufficientSamples)
        assertEquals(0.8, coding.successRate!!, 0.0001)
        assertEquals(1, coding.failureTypes["objective_mismatch"])
        val reasoning = store.view("m · gpt", ModelTask.REASONING)!!
        assertFalse(reasoning.sufficientSamples) // categories never share samples
        assertNull(reasoning.successRate)
    }

    @Test fun routingWeightNeutralWhenInsufficientAndFavorsSuccessOtherwise() = runBlocking {
        val store = ReputationStore(MemoryReputationRepository(), clock = { now })
        repeat(ReputationPolicy.MIN_SAMPLES) { store.record("good", ModelTask.GENERAL, true, null, 100, 0.01, "r", "TASK") }
        repeat(ReputationPolicy.MIN_SAMPLES) { store.record("bad", ModelTask.GENERAL, false, "x", 900, 0.05, "r", "TASK") }
        val good = ReputationPolicy.routingWeight(store.view("good", ModelTask.GENERAL)!!, 1_000)
        val bad = ReputationPolicy.routingWeight(store.view("bad", ModelTask.GENERAL)!!, 1_000)
        assertTrue(good > bad)
        val emptyView = ReputationView(
            modelKey = "none", category = ModelTask.GENERAL, sampleSize = 0,
            sufficientSamples = false, successRate = null, failure = 0,
            failureTypes = emptyMap(), averageLatencyMs = null, totalCostUsd = 0.0, history = emptyList(),
        )
        assertEquals(0.5, ReputationPolicy.routingWeight(emptyView, 1_000), 0.0001)
    }

    @Test fun resetClearsOnlyRequestedScope() = runBlocking {
        val store = ReputationStore(MemoryReputationRepository(), clock = { now })
        store.record("a", ModelTask.GENERAL, true, null, 1, 0.0, "r", "TASK")
        store.record("b", ModelTask.GENERAL, true, null, 1, 0.0, "r", "TASK")
        store.reset("a", null)
        assertNull(store.view("a", ModelTask.GENERAL))
        assertNotNull(store.view("b", ModelTask.GENERAL))
    }
}
