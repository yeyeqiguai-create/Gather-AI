package com.tim.futureai.domain.task

import com.tim.futureai.core.common.JsonMini
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class VerifiedTaskEngineTest {
    @Test fun taskStateMachineRejectsIllegalAndFailedToSuccessTransitions() {
        assertTrue(TaskStateMachine.canTransition(TaskStatus.CREATED, TaskStatus.PLANNING))
        assertTrue(TaskStateMachine.canTransition(TaskStatus.PLANNING, TaskStatus.READY))
        assertFalse(TaskStateMachine.canTransition(TaskStatus.FAILED, TaskStatus.SUCCEEDED))
        assertFalse(TaskStateMachine.canTransition(TaskStatus.CANCELLED, TaskStatus.RUNNING))
        assertFalse(TaskStateMachine.canTransition(TaskStepStatus.PLANNED, TaskStepStatus.SUCCEEDED))
        assertTrue(TaskStateMachine.canTransition(TaskStatus.FAILED, TaskStatus.READY, manualRetry = true))
    }

    @Test fun plannerTopologicallyOrdersStepsAndRejectsCyclesAndMissingDependencies() {
        val registry = BuiltInTaskToolRegistry(InMemoryTaskRepository())
        val planner = TaskPlanner(registry)
        val valid = planner.parseAndValidate(planJson(listOf(
            jsonStep("second", "first"), jsonStep("first", null),
        )), maxSteps = 4)
        assertNotNull(valid.first)
        assertEquals(listOf("first", "second"), (planner.validate(valid.first!!, 4) as PlanValidation.Valid).topologicalOrder)

        val cycle = planner.parseAndValidate(planJson(listOf(jsonStep("a", "b"), jsonStep("b", "a"))), 4)
        assertNull(cycle.first)
        assertTrue(cycle.second.orEmpty().contains("循环依赖"))
        val missing = planner.parseAndValidate(planJson(listOf(jsonStep("a", "ghost"))), 4)
        assertNull(missing.first)
        assertTrue(missing.second.orEmpty().contains("不存在"))
    }

    @Test fun plannerRejectsUnregisteredToolsInvalidArgumentsAndExcessiveSteps() {
        val planner = TaskPlanner(BuiltInTaskToolRegistry(InMemoryTaskRepository()))
        val unknown = planJson(listOf(jsonStep("a", null, tool = "arbitrary.shell")))
        assertTrue(planner.parseAndValidate(unknown, 4).second.orEmpty().contains("未注册"))
        val malformed = """{"title":"bad","steps":[{"id":"a","title":"a","description":"a","dependsOn":[],"executorId":"local.calculator","input":{"code":"x"},"verificationRuleIds":["status.confirmed-success"]}]}"""
        assertTrue(planner.parseAndValidate(malformed, 4).second.orEmpty().contains("不接受参数"))
        assertTrue(planner.parseAndValidate(planJson((1..3).map { jsonStep("s$it", null) }), 2).second.orEmpty().contains("超过"))
    }

    @Test fun structuralVerificationChecksOutputFormatRequiredFieldsAndTypes() {
        val definition = CalculatorTaskTool().definition
        val step = step("shape", "task", definition.id, listOf("structure.non-empty-output"), mapOf("expression" to "2+2"))
        val verifier = VerificationEngine()
        val malformed = verifier.verify("task", step, definition,
            ToolExecutionResult("not json", "bad output", confirmedSuccess = true), emptyList(), false, 1)
        val wrongType = verifier.verify("task", step, definition,
            ToolExecutionResult("{\"value\":\"4\"}", "wrong type", confirmedSuccess = true), emptyList(), false, 2)
        val missingField = verifier.verify("task", step, definition,
            ToolExecutionResult("{}", "missing field", confirmedSuccess = true), emptyList(), false, 3)
        val valid = verifier.verify("task", step, definition,
            ToolExecutionResult("{\"value\":4}", "valid shape", confirmedSuccess = true), emptyList(), false, 4)
        assertEquals(VerificationStatus.FAILED, malformed.status)
        assertEquals(VerificationStatus.FAILED, wrongType.status)
        assertEquals(VerificationStatus.FAILED, missingField.status)
        assertEquals(VerificationStatus.VERIFIED, valid.status)
    }

    @Test fun inconclusiveVerificationIsNotSuccessAndFailedRulesStayFailed() {
        val tool = CalculatorTaskTool().definition
        val step = step("s1", "t1", TaskToolIds.CALCULATOR, listOf("unregistered.verification"), mapOf("expression" to "1+1"))
        val execution = ToolExecutionResult("2", "done", confirmedSuccess = true)
        val inconclusive = VerificationEngine().verify("t1", step, tool, execution, emptyList(), false, 5)
        assertEquals(VerificationStatus.INCONCLUSIVE, inconclusive.status)

        val statusStep = step("s1", "t1", TaskToolIds.CALCULATOR, listOf("status.confirmed-success"), mapOf("expression" to "1+1"))
        val failed = VerificationEngine().verify("t1", statusStep, tool, execution.copy(confirmedSuccess = false), emptyList(), false, 5)
        assertEquals(VerificationStatus.FAILED, failed.status)
        assertNotNull(failed.reason)
    }

    @Test fun permissionCheckerCannotBypassToolLevelApproval() {
        val base = CalculatorTaskTool().definition
        val mandatory = base.copy(requiresApproval = true, hasSideEffects = true, permissions = setOf(ToolPermission.EXTERNAL_WRITE))
        val checker = TaskPermissionChecker()
        assertFalse(checker.check(mandatory, TaskAuthorizationPolicy.SAFE_ONLY, userApproved = true).allowed)
        val pending = checker.check(mandatory, TaskAuthorizationPolicy.CONFIRM_SIDE_EFFECTS, userApproved = false)
        assertTrue(pending.requiresApproval)
        assertFalse(pending.allowed)
        assertTrue(checker.check(mandatory, TaskAuthorizationPolicy.CONFIRM_SIDE_EFFECTS, userApproved = true).allowed)
        assertTrue(checker.check(base, TaskAuthorizationPolicy.CONFIRM_EACH_TOOL, userApproved = false).requiresApproval)
    }

    @Test fun calculatorOnlyEvaluatesBoundedArithmetic() {
        assertEquals(391.0, SafeCalculator.evaluate("17 * 23"), 0.000001)
        assertEquals(4.0, SafeCalculator.evaluate("(2 + 6) / 2"), 0.000001)
        assertTrue(runCatching { SafeCalculator.evaluate("1/0") }.isFailure)
        assertTrue(runCatching { SafeCalculator.evaluate("java.lang.Runtime") }.isFailure)
    }

    @Test fun jsonAndTextToolsProduceRuleSpecificEvidenceAndFailures() = runBlocking {
        val repository = InMemoryTaskRepository()
        val jsonTool = JsonValidationTaskTool()
        val validJson = jsonTool.execute(mapOf("json" to "{\"id\":7,\"name\":\"A\"}", "requiredKeys" to "id,name"), context())
        val jsonStep = step("json", "t", jsonTool.definition.id, jsonTool.definition.verificationRuleIds,
            mapOf("json" to "{\"id\":7,\"name\":\"A\"}", "requiredKeys" to "id,name"))
        val evidence = validJson.evidence.map { EvidenceReference("e", "t", "json", it.ruleId, it.source, it.summary, 1) }
        assertEquals(VerificationStatus.VERIFIED,
            VerificationEngine().verify("t", jsonStep, jsonTool.definition, validJson, evidence, false, 2).status)
        val invalidJson = jsonTool.execute(mapOf("json" to "{\"id\":7}", "requiredKeys" to "id,name"), context())
        assertEquals(VerificationStatus.FAILED,
            VerificationEngine().verify("t", jsonStep, jsonTool.definition, invalidJson, invalidJson.evidence.map {
                EvidenceReference("e2", "t", "json", it.ruleId, it.source, it.summary, 1)
            }, false, 2).status)

        val textTool = TextRulesTaskTool()
        val textInput = mapOf("text" to "Gather AI verified", "contains" to "Gather|verified", "excludes" to "unsafe")
        val textResult = textTool.execute(textInput, context())
        val textStep = step("text", "t", textTool.definition.id, textTool.definition.verificationRuleIds, textInput)
        val textEvidence = textResult.evidence.map { EvidenceReference("e3", "t", "text", it.ruleId, it.source, it.summary, 1) }
        assertEquals(VerificationStatus.VERIFIED,
            VerificationEngine().verify("t", textStep, textTool.definition, textResult, textEvidence, false, 2).status)
        assertTrue(repository.tasks.value.isEmpty())
    }

    @Test fun auditSequenceIsOrderedAndCredentialSummariesAreRedacted() {
        var time = 1L
        val logger = TaskAuditLogger(object : TaskClock { override fun nowMs() = time++ })
        val aggregate = TaskAggregate(Task("task", "goal", 0))
        val first = logger.append(aggregate, TaskEventType.CREATED, "created")
        val second = logger.append(first, TaskEventType.TOOL_STARTED, "api_key=sk-abcdefghijklmnopqrstuvwxyz")
        assertEquals(listOf(1L, 2L), second.events.map { it.sequence })
        assertFalse(second.events.last().summary.contains("abcdefghijklmnopqrstuvwxyz"))
        assertTrue(second.events.last().summary.contains("REDACTED"))
    }

    @Test fun schedulerRespectsDependenciesVerifiesResultsAndPreventsDuplicateStart() = runBlocking {
        val repository = InMemoryTaskRepository()
        val order = mutableListOf<String>()
        val base = CalculatorTaskTool()
        val recording = object : TaskTool by base {
            override suspend fun execute(input: Map<String, String>, context: ToolExecutionContext): ToolExecutionResult {
                order += input.getValue("expression")
                return base.execute(input, context)
            }
        }
        val registry = TestRegistry(recording)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = TaskEngine(repository, registry, scope, IncrementingClock())
        val source = source(planJson(listOf(
            jsonStep("second", "first", expression = "2+2"), jsonStep("first", null, expression = "17*23"),
        )))
        val policy = TaskEnginePolicy(maxSteps = 4, maxRetries = 0)
        val planned = engine.plan("计算并复核", source, policy) as TaskPlanResult.Ready
        assertTrue(engine.start(planned.aggregate.task.id, policy))
        val finished = repository.tasks.value.single()
        assertEquals(TaskStatus.SUCCEEDED, finished.task.status)
        assertEquals(TaskConclusion.VERIFIED_SUCCESS, finished.task.result?.conclusion)
        assertEquals(listOf("17*23", "2+2"), order)
        assertEquals(2, finished.verifiedStepCount)
        assertFalse(engine.start(planned.aggregate.task.id, policy))
        scope.cancel()
    }

    @Test fun toolFailuresHaveFiniteSafeRetries() = runBlocking {
        val repository = InMemoryTaskRepository()
        val attempts = AtomicInteger()
        val definition = safeDefinition("test.flaky", listOf("structure.non-empty-output", "status.confirmed-success"), maxRetries = 1)
        val tool = FakeTool(definition) { _, _ ->
            if (attempts.incrementAndGet() == 1) error("transient")
            ToolExecutionResult("ok", "recovered", confirmedSuccess = true)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = TaskEngine(repository, TestRegistry(tool), scope, IncrementingClock())
        val json = customPlan("""{"id":"s1","title":"Call","description":"test retry","dependsOn":[],"executorId":"test.flaky","input":{},"verificationRuleIds":["structure.non-empty-output","status.confirmed-success"]}""")
        val policy = TaskEnginePolicy(maxRetries = 1)
        val planned = engine.plan("retry safely", source(json), policy) as TaskPlanResult.Ready
        assertTrue(engine.start(planned.aggregate.task.id, policy))
        val finished = repository.tasks.value.single()
        assertEquals(TaskStatus.SUCCEEDED, finished.task.status)
        assertEquals(2, finished.steps.single().attemptCount)
        assertEquals(2, finished.invocations.size)
        assertTrue(finished.events.any { it.type == TaskEventType.RETRY_SCHEDULED })
        scope.cancel()
    }

    @Test fun duplicateStartIsBlockedAndCoroutineCancellationPersists() = runBlocking {
        val repository = InMemoryTaskRepository()
        val started = CompletableDeferred<Unit>()
        val definition = safeDefinition("test.blocking", listOf("structure.non-empty-output"))
        val tool = FakeTool(definition) { _, _ ->
            started.complete(Unit)
            kotlinx.coroutines.awaitCancellation()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = TaskEngine(repository, TestRegistry(tool), scope, IncrementingClock())
        val json = customPlan("""{"id":"s1","title":"Wait","description":"wait","dependsOn":[],"executorId":"test.blocking","input":{},"verificationRuleIds":["structure.non-empty-output"]}""")
        val policy = TaskEnginePolicy(stepTimeoutMs = 20_000, totalTimeoutMs = 30_000)
        val planned = engine.plan("cancel test", source(json), policy) as TaskPlanResult.Ready
        assertTrue(engine.start(planned.aggregate.task.id, policy))
        withTimeout(2_000) { started.await() }
        assertFalse(engine.start(planned.aggregate.task.id, policy))
        engine.cancel(planned.aggregate.task.id)
        assertEquals(TaskStatus.CANCELLED, repository.tasks.value.single().task.status)
        assertEquals(TaskStepStatus.CANCELLED, repository.tasks.value.single().steps.single().status)
        scope.cancel()
    }

    @Test fun pauseWaitsForCurrentSafeStepThenResumeCompletesVerifiedTask() = runBlocking {
        val repository = InMemoryTaskRepository()
        val started = CompletableDeferred<Unit>()
        val finishStep = CompletableDeferred<Unit>()
        val definition = safeDefinition("test.pause", listOf("structure.non-empty-output", "status.confirmed-success"))
        val tool = FakeTool(definition) { _, _ ->
            started.complete(Unit)
            finishStep.await()
            ToolExecutionResult("done", "completed", confirmedSuccess = true)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = TaskEngine(repository, TestRegistry(tool), scope, IncrementingClock())
        val json = customPlan("""{"id":"s1","title":"Wait","description":"wait","dependsOn":[],"executorId":"test.pause","input":{},"verificationRuleIds":["structure.non-empty-output","status.confirmed-success"]}""")
        val policy = TaskEnginePolicy(stepTimeoutMs = 5_000, totalTimeoutMs = 10_000)
        val planned = engine.plan("pause test", source(json), policy) as TaskPlanResult.Ready
        assertTrue(engine.start(planned.aggregate.task.id, policy))
        withTimeout(2_000) { started.await() }
        val pauseJob = launch(Dispatchers.Default) { engine.pause(planned.aggregate.task.id) }
        delay(30)
        finishStep.complete(Unit)
        withTimeout(2_000) { pauseJob.join() }
        assertEquals(TaskStatus.PAUSED, repository.tasks.value.single().task.status)
        assertTrue(engine.resume(planned.aggregate.task.id, policy))
        withTimeout(2_000) {
            while (repository.tasks.value.single().task.status != TaskStatus.SUCCEEDED) delay(10)
        }
        assertEquals(VerificationStatus.VERIFIED, repository.tasks.value.single().steps.single().verification?.status)
        scope.cancel()
    }

    @Test fun timedOutToolFailsWithRecordedTypeAndDoesNotExceedRetryLimit() = runBlocking {
        val repository = InMemoryTaskRepository()
        val definition = safeDefinition("test.timeout", listOf("status.confirmed-success"), maxRetries = 0)
        val tool = FakeTool(definition) { _, _ ->
            delay(200)
            ToolExecutionResult("late", "late", confirmedSuccess = true)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = TaskEngine(repository, TestRegistry(tool), scope, IncrementingClock())
        val json = customPlan("""{"id":"s1","title":"Slow","description":"timeout","dependsOn":[],"executorId":"test.timeout","input":{},"verificationRuleIds":["status.confirmed-success"]}""")
        val policy = TaskEnginePolicy(maxRetries = 3, stepTimeoutMs = 20, totalTimeoutMs = 1_000)
        val planned = engine.plan("timeout test", source(json), policy) as TaskPlanResult.Ready
        assertTrue(engine.start(planned.aggregate.task.id, policy))
        withTimeout(2_000) {
            while (repository.tasks.value.single().task.status != TaskStatus.FAILED) delay(10)
        }
        val aggregate = repository.tasks.value.single()
        assertEquals("StepTimeout", aggregate.invocations.single().errorType)
        assertEquals(1, aggregate.steps.single().attemptCount)
        scope.cancel()
    }

    @Test fun interruptedProcessRequiresExplicitResumeAndThenRestoresFromCheckpoint() = runBlocking {
        val repository = InMemoryTaskRepository()
        val definition = safeDefinition("test.restart", listOf("structure.non-empty-output", "status.confirmed-success"))
        val tool = FakeTool(definition) { _, _ -> ToolExecutionResult("ok", "verified after resume", confirmedSuccess = true) }
        val activeStep = step("s1", "restart-task", definition.id, definition.verificationRuleIds, emptyMap(), TaskStepStatus.RUNNING)
        repository.save(TaskAggregate(
            Task("restart-task", "resume this safely", 1, TaskStatus.RUNNING),
            steps = listOf(activeStep),
            checkpoints = listOf(TaskCheckpoint("restart-task", "s1", emptyList(), "before step", 2)),
        ))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = TaskEngine(repository, TestRegistry(tool), scope, IncrementingClock())
        engine.load(TaskEnginePolicy())
        val recovered = repository.tasks.value.single()
        assertEquals(TaskStatus.PAUSED, recovered.task.status)
        assertEquals(TaskStepStatus.READY, recovered.steps.single().status)
        assertTrue(engine.resume("restart-task", TaskEnginePolicy()))
        assertEquals(TaskStatus.SUCCEEDED, repository.tasks.value.single().task.status)
        scope.cancel()
    }

    @Test fun recoveryPausesUncertainNonIdempotentCallsButAllowsCheckedSafeReplay() = runBlocking {
        val audit = TaskAuditLogger(IncrementingClock())
        val active = step("s1", "t1", "test.side-effect", listOf("manual.user-confirmation"), emptyMap(), TaskStepStatus.RUNNING)
        val invocation = ToolInvocation("i1", "t1", "s1", active.executorId, "t1:s1", "", ToolInvocationStatus.STARTED, startedAtMs = 1)
        val sideEffect = FakeTool(safeDefinition(active.executorId, listOf("manual.user-confirmation")).copy(
            hasSideEffects = true, idempotent = false, requiresApproval = true, permissions = setOf(ToolPermission.EXTERNAL_WRITE),
        )) { _, _ -> error("not called") }
        val repo = TestRegistry(sideEffect)
        val base = TaskAggregate(Task("t1", "goal", 1, TaskStatus.RUNNING), listOf(active), listOf(invocation))
        val recovered = TaskRecoveryManager(repo, audit, IncrementingClock()).reconcile(base)
        assertEquals(TaskStatus.PAUSED, recovered.task.status)
        assertEquals(TaskStepStatus.UNKNOWN, recovered.steps.single().status)
        assertTrue(recovered.events.last().summary.contains("不会自动重试"))

        val safeStep = active.copy(executorId = "test.safe")
        val safeTool = FakeTool(safeDefinition("test.safe", listOf("status.confirmed-success"))) { _, _ -> error("not called") }
        val safe = TaskRecoveryManager(TestRegistry(safeTool), audit, IncrementingClock()).reconcile(
            base.copy(steps = listOf(safeStep), invocations = listOf(invocation.copy(toolId = "test.safe"))),
        )
        assertEquals(TaskStatus.PAUSED, safe.task.status)
        assertEquals(TaskStepStatus.READY, safe.steps.single().status)
        assertEquals(ToolInvocationStatus.UNKNOWN, safe.invocations.single().status)
    }

    @Test fun manualApprovalRuleCannotBecomeVerifiedWithoutExplicitApproval() {
        val definition = CalculatorTaskTool().definition.copy(verificationRuleIds = listOf("manual.user-confirmation"))
        val step = step("s1", "t1", definition.id, definition.verificationRuleIds, mapOf("expression" to "1+1"))
        val result = ToolExecutionResult("ok", "done", confirmedSuccess = true)
        assertEquals(VerificationStatus.REQUIRES_APPROVAL,
            VerificationEngine().verify("t1", step, definition, result, emptyList(), false, 4).status)
        assertEquals(VerificationStatus.VERIFIED,
            VerificationEngine().verify("t1", step, definition, result, emptyList(), true, 4).status)
    }

    private fun source(json: String) = TaskPlanSource { _, _, _, _ ->
        PlanSourceResult(json, TaskPlannerUsage("profile", "test", "test-model", 10, 3, 4))
    }

    private fun context() = ToolExecutionContext("t", "s", "t:s") { 1L }

    private fun planJson(steps: List<String>) = """{"title":"Plan","steps":[${steps.joinToString(",")}] }"""
    private fun jsonStep(id: String, dependency: String?, expression: String = "1+1", tool: String = TaskToolIds.CALCULATOR): String {
        val dependencies = dependency?.let { "[\"$it\"]" } ?: "[]"
        val rules = if (tool == TaskToolIds.CALCULATOR) "[\"structure.non-empty-output\",\"status.confirmed-success\",\"result.calculation-matches-expression\",\"evidence.current-step\"]"
            else "[\"structure.non-empty-output\",\"status.confirmed-success\"]"
        return """{"id":"$id","title":"$id","description":"calculate","dependsOn":$dependencies,"executorId":"$tool","input":{"expression":"$expression"},"verificationRuleIds":$rules}"""
    }
    private fun customPlan(stepJson: String) = planJson(listOf(stepJson))
    private fun step(
        id: String,
        taskId: String,
        toolId: String,
        rules: List<String>,
        input: Map<String, String>,
        status: TaskStepStatus = TaskStepStatus.READY,
    ) = TaskStep(id, taskId, id, "description", emptyList(), input, toolId, rules, status, 0, idempotencyKey = "$taskId:$id")

    private fun safeDefinition(id: String, rules: List<String>, maxRetries: Int = 0) = ToolDefinition(
        id, id, "test tool", emptyMap(), setOf("test"), emptySet(), false, false, true, 2_000, maxRetries, rules,
    )

    private class TestRegistry(vararg tools: TaskTool) : TaskToolRegistry {
        private val entries = tools.associateBy { it.definition.id }
        override fun all() = entries.values.map { it.definition }
        override fun get(id: String) = entries[id]
    }

    private class FakeTool(
        override val definition: ToolDefinition,
        private val body: suspend (Map<String, String>, ToolExecutionContext) -> ToolExecutionResult = { _, _ ->
            ToolExecutionResult("ok", "ok", confirmedSuccess = true)
        },
    ) : TaskTool {
        override suspend fun execute(input: Map<String, String>, context: ToolExecutionContext) = body(input, context)
    }

    private class InMemoryTaskRepository : TaskRepository {
        private val state = kotlinx.coroutines.flow.MutableStateFlow<List<TaskAggregate>>(emptyList())
        override val tasks = state
        override suspend fun load() = state.value
        override suspend fun save(aggregate: TaskAggregate) {
            state.value = (state.value.filterNot { it.task.id == aggregate.task.id } + aggregate)
        }
        override suspend fun delete(taskId: String) { state.value = state.value.filterNot { it.task.id == taskId } }
        override suspend fun clearFinished(): Int {
            val next = state.value.filterNot { it.task.status in setOf(TaskStatus.SUCCEEDED, TaskStatus.FAILED, TaskStatus.CANCELLED) }
            val count = state.value.size - next.size
            state.value = next
            return count
        }
    }

    private class IncrementingClock : TaskClock {
        private val count = AtomicInteger(1)
        override fun nowMs() = count.getAndIncrement().toLong()
    }
}
