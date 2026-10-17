package com.tim.futureai.data.task

import com.tim.futureai.domain.task.EvidenceReference
import com.tim.futureai.domain.task.Task
import com.tim.futureai.domain.task.TaskAggregate
import com.tim.futureai.domain.task.TaskCheckpoint
import com.tim.futureai.domain.task.TaskConclusion
import com.tim.futureai.domain.task.TaskEvent
import com.tim.futureai.domain.task.TaskEventType
import com.tim.futureai.domain.task.TaskResult
import com.tim.futureai.domain.task.TaskStatus
import com.tim.futureai.domain.task.TaskStep
import com.tim.futureai.domain.task.TaskStepResult
import com.tim.futureai.domain.task.TaskStepStatus
import com.tim.futureai.domain.task.ToolInvocation
import com.tim.futureai.domain.task.ToolInvocationStatus
import com.tim.futureai.domain.task.VerificationResult
import com.tim.futureai.domain.task.VerificationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class FileTaskRepositoryTest {
    @Test fun corruptPrimarySnapshotRecoversFromLastKnownGoodBackup() = runBlocking {
        val directory = Files.createTempDirectory("verified-task-recovery").toFile()
        try {
            val file = directory.resolve("tasks.json")
            val repository = FileTaskRepository(file)
            val first = TaskAggregate(Task("first", "first snapshot", 1, TaskStatus.READY))
            val second = TaskAggregate(Task("second", "second snapshot", 2, TaskStatus.RUNNING))
            repository.save(first)
            repository.save(second)
            file.writeText("partial { invalid")

            val recovered = FileTaskRepository(file).load()
            assertEquals(listOf("first"), recovered.map { it.task.id })
            assertTrue(file.readText().contains("first snapshot"))
            assertTrue(file.resolveSibling("tasks.json.bak").isFile)
            assertTrue(directory.listFiles()!!.any {
                it.name.startsWith("tasks.json.corrupt-")
            })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun persistsAggregateCheckpointAuditEvidenceAndVerificationAcrossInstances() = runBlocking {
        val directory = Files.createTempDirectory("verified-task-test").toFile()
        try {
            val file = directory.resolve("tasks.json")
            val evidence = EvidenceReference("e1", "t1", "s1", "rule", "local.calculator", "rechecked locally", 8)
            val verification = VerificationResult("t1", "s1", VerificationStatus.VERIFIED, "rule", listOf("e1"), null, 9)
            val step = TaskStep(
                id = "s1", taskId = "t1", title = "Calculate", description = "safe arithmetic", dependsOn = emptyList(),
                input = mapOf("expression" to "2+2"), executorId = "local.calculator", verificationRuleIds = listOf("rule"),
                status = TaskStepStatus.SUCCEEDED, attemptCount = 1,
                result = TaskStepResult("result 4", "{\"value\":4}", listOf("e1"), 8, confirmedSuccess = true),
                verification = verification, idempotencyKey = "t1:s1",
            )
            val aggregate = TaskAggregate(
                task = Task("t1", "calculate 2+2", 1, TaskStatus.SUCCEEDED,
                    result = TaskResult(TaskConclusion.VERIFIED_SUCCESS, "verified", 10),
                    plannerModel = "model", plannerProfileId = "profile", planVersion = "verified-plan-v1",
                    plannerPromptTokens = 5, plannerCompletionTokens = 2, plannerDurationMs = 7, estimatedCostUsd = 0.0001),
                steps = listOf(step),
                invocations = listOf(ToolInvocation("i1", "t1", "s1", "local.calculator", "t1:s1", "expression=2+2",
                    ToolInvocationStatus.SUCCEEDED, "result 4", 2, startedAtMs = 6, finishedAtMs = 8)),
                checkpoints = listOf(TaskCheckpoint("t1", null, listOf("s1"), "verified checkpoint", 9)),
                events = listOf(TaskEvent(1, "t1", null, TaskEventType.CREATED, "created", 1), TaskEvent(2, "t1", "s1", TaskEventType.COMPLETED, "verified", 10)),
                evidence = listOf(evidence),
            )
            FileTaskRepository(file).save(aggregate)
            val restored = FileTaskRepository(file).load().single()
            assertEquals(aggregate, restored)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun clearFinishedKeepsLiveTasksAndForgetOnlyRemovesDurableHistory() = runBlocking {
        val directory = Files.createTempDirectory("verified-task-retention").toFile()
        try {
            val file = directory.resolve("tasks.json")
            val repository = FileTaskRepository(file)
            val done = TaskAggregate(Task("done", "finished", 1, TaskStatus.SUCCEEDED))
            val running = TaskAggregate(Task("running", "active", 2, TaskStatus.RUNNING))
            repository.save(done)
            repository.save(running)
            assertEquals(1, repository.clearFinished())
            assertEquals(listOf("running"), repository.tasks.value.map { it.task.id })

            repository.save(TaskAggregate(Task("final", "no retention", 3, TaskStatus.SUCCEEDED)))
            repository.forget("final")
            assertTrue(repository.tasks.value.any { it.task.id == "final" })
            assertEquals(listOf("running"), FileTaskRepository(file).load().map { it.task.id })
        } finally {
            directory.deleteRecursively()
        }
    }
}
