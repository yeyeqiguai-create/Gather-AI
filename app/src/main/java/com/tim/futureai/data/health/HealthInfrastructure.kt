package com.tim.futureai.data.health

import android.content.Context
import android.os.Build
import com.tim.futureai.data.ai.AiProviderRegistry
import com.tim.futureai.data.settings.AiSettingsManager
import com.tim.futureai.data.settings.AppPreferencesManager
import com.tim.futureai.core.common.AppResult
import com.tim.futureai.data.update.GitHubUpdateRepository
import com.tim.futureai.feature.update.UpdateCheckResult
import com.tim.futureai.domain.health.HealthCheckRegistration
import com.tim.futureai.domain.health.HealthCheckResult
import com.tim.futureai.domain.health.HealthStatus
import com.tim.futureai.domain.health.RepairAction
import com.tim.futureai.domain.health.RepairOutcome
import com.tim.futureai.domain.health.RepairStrategy
import com.tim.futureai.domain.task.TaskEngine
import com.tim.futureai.domain.task.TaskStatus
import com.tim.futureai.domain.task.TaskStepStatus
import com.tim.futureai.domain.task.toTaskEnginePolicy
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Built-in checks over real app state. Every check returns concrete evidence;
 * network probes are bounded by the monitor's per-check timeout.
 */
class HealthCheckFactory(
    private val context: Context,
    private val settingsManager: AiSettingsManager,
    private val providerRegistry: AiProviderRegistry,
    private val taskEngine: TaskEngine,
    private val preferencesManager: AppPreferencesManager,
    private val updateRepository: GitHubUpdateRepository,
    private val clock: () -> Long,
) {
    fun build(): List<HealthCheckRegistration> = buildList {
        addAll(providerChecks())
        add(taskFlowCheck())
        add(storageCheck())
        add(permissionsCheck())
        add(updateStatusCheck())
    }

    private fun providerChecks(): List<HealthCheckRegistration> {
        val registrations = mutableListOf<HealthCheckRegistration>()
        for (profile in settingsManager.profiles()) {
            val profileId = profile.id
            val profileName = profile.name
            registrations += HealthCheckRegistration(
                id = "provider.$profileId",
                title = "服务商连接 · $profileName",
                category = "providers",
                selfHealable = true,
                check = {
                    val startedAt = clock()
                    if (!profile.isAvailableForRouting) {
                        HealthCheckResult(
                            checkId = "provider.$profileId", status = HealthStatus.UNVERIFIABLE,
                            title = "服务商连接 · $profileName",
                            detail = if (profile.enabled) "配置不完整（缺少 Base URL、模型或密钥），已跳过连接测试" else "该服务商已停用",
                            evidence = "enabled=${profile.enabled};configured=${profile.isConfigured}",
                            checkedAtMs = clock(), durationMs = clock() - startedAt, canSelfHeal = false,
                        )
                    } else {
                        val provider = providerRegistry.create(profile.providerProtocol) { settingsManager.configFor(profileId) }
                        val result = provider.testConnection()
                        val latency = clock() - startedAt
                        when (result) {
                            is AppResult.Success -> if (latency > 8_000) HealthCheckResult(
                                "provider.$profileId", HealthStatus.WARNING, "服务商连接 · $profileName",
                                "连接成功但延迟 ${latency / 1000} 秒，偏高", "latencyMs=$latency", clock(), latency, canSelfHeal = true,
                            ) else HealthCheckResult(
                                "provider.$profileId", HealthStatus.OK, "服务商连接 · $profileName",
                                "连接正常", "latencyMs=$latency", clock(), latency,
                            )
                            is AppResult.Failure -> HealthCheckResult(
                                "provider.$profileId", HealthStatus.FAILED, "服务商连接 · $profileName",
                                "连接失败：${result.error}", "latencyMs=$latency;error=${result.error}", clock(), latency, canSelfHeal = true,
                            )
                        }
                    }
                },
            )
        }
        return registrations
    }

    private fun taskFlowCheck() = HealthCheckRegistration(
        id = "tasks.flow", title = "任务队列与后台任务", category = "tasks", selfHealable = true,
        check = {
            val tasks = taskEngine.tasks.value
            val now = clock()
            val waiting = tasks.filter { it.task.status == TaskStatus.WAITING_FOR_APPROVAL }
            val running = tasks.filter { it.task.status in setOf(TaskStatus.RUNNING, TaskStatus.VERIFYING) }
            val stalledRunning = running.filter { aggregate ->
                val lastEvent = aggregate.events.maxOfOrNull { it.createdAtMs } ?: aggregate.task.createdAtMs
                now - lastEvent > STALL_THRESHOLD_MS
            }
            val staleApprovals = waiting.filter { aggregate ->
                val lastEvent = aggregate.events.maxOfOrNull { it.createdAtMs } ?: aggregate.task.createdAtMs
                now - lastEvent > APPROVAL_STALL_MS
            }
            val recentFailures = tasks.filter { it.task.status == TaskStatus.FAILED }
            when {
                stalledRunning.isNotEmpty() -> HealthCheckResult(
                    "tasks.flow", HealthStatus.FAILED, "任务队列与后台任务",
                    "${stalledRunning.size} 个执行中任务超过 10 分钟无进展",
                    stalledRunning.joinToString(";") { "${it.task.id}:${it.task.goal.take(24)}:lastEvent=${it.events.maxOfOrNull { e -> e.createdAtMs } ?: 0}" },
                    clock(), 0L, canSelfHeal = true,
                )
                staleApprovals.isNotEmpty() -> HealthCheckResult(
                    "tasks.flow", HealthStatus.WARNING, "任务队列与后台任务",
                    "${staleApprovals.size} 个任务等待确认超过 30 分钟",
                    staleApprovals.joinToString(";") { "${it.task.id}:${it.task.goal.take(24)}" },
                    clock(), 0L, canSelfHeal = true,
                )
                recentFailures.isNotEmpty() -> HealthCheckResult(
                    "tasks.flow", HealthStatus.WARNING, "任务队列与后台任务",
                    "存在 ${recentFailures.size} 个失败任务；执行中 ${running.size} 个，等待确认 ${waiting.size} 个",
                    recentFailures.joinToString(";") { "${it.task.id}:${it.task.goal.take(24)}" },
                    clock(), 0L, canSelfHeal = false,
                )
                else -> HealthCheckResult(
                    "tasks.flow", HealthStatus.OK, "任务队列与后台任务",
                    "任务状态正常：执行中 ${running.size}，等待确认 ${waiting.size}，历史 ${tasks.size}",
                    "running=${running.size};waiting=${waiting.size};total=${tasks.size}", clock(), 0L,
                )
            }
        },
    )

    private fun storageCheck() = HealthCheckRegistration(
        id = "storage.state", title = "存储状态", category = "storage", selfHealable = true,
        check = {
            val cacheDir = context.cacheDir
            val cacheBytes = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val freeBytes = context.filesDir.usableSpace
            when {
                freeBytes < 200L * 1024 * 1024 -> HealthCheckResult(
                    "storage.state", HealthStatus.FAILED, "存储状态",
                    "可用空间不足 200MB（剩 ${freeBytes / (1024 * 1024)}MB），可能影响写入安全",
                    "freeBytes=$freeBytes;cacheBytes=$cacheBytes", clock(), 0L, canSelfHeal = false,
                )
                cacheBytes > 200L * 1024 * 1024 -> HealthCheckResult(
                    "storage.state", HealthStatus.WARNING, "存储状态",
                    "缓存目录已达 ${cacheBytes / (1024 * 1024)}MB，建议清理",
                    "cacheBytes=$cacheBytes;freeBytes=$freeBytes", clock(), 0L, canSelfHeal = true,
                )
                else -> HealthCheckResult(
                    "storage.state", HealthStatus.OK, "存储状态",
                    "缓存 ${cacheBytes / (1024 * 1024)}MB · 可用空间 ${freeBytes / (1024 * 1024)}MB",
                    "cacheBytes=$cacheBytes;freeBytes=$freeBytes", clock(), 0L,
                )
            }
        },
    )

    private fun permissionsCheck() = HealthCheckRegistration(
        id = "permissions.state", title = "权限状态", category = "permissions", selfHealable = false,
        check = {
            val problems = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                problems += "通知权限未授予：主动提醒将仅保留在应用内通知中心"
            }
            val canInstall = if (Build.VERSION.SDK_INT >= 26) context.packageManager.canRequestPackageInstalls() else true
            if (!canInstall) problems += "未授予“安装未知应用”权限：应用内更新将无法完成安装"
            if (problems.isEmpty()) HealthCheckResult(
                "permissions.state", HealthStatus.OK, "权限状态", "通知与安装权限正常",
                "notifications=${Build.VERSION.SDK_INT < 33};install=$canInstall", clock(), 0L,
            ) else HealthCheckResult(
                "permissions.state", HealthStatus.WARNING, "权限状态",
                problems.joinToString("；"), problems.joinToString("|"), clock(), 0L,
            )
        },
    )

    private fun updateStatusCheck() = HealthCheckRegistration(
        id = "update.status", title = "更新状态", category = "updates", selfHealable = false,
        check = {
            when (val result = updateRepository.check()) {
                is UpdateCheckResult.Available -> HealthCheckResult(
                    "update.status", HealthStatus.OK, "更新状态",
                    "发现新版本 ${result.release.versionName}（versionCode ${result.release.versionCode}）",
                    "tag=${result.release.versionName};code=${result.release.versionCode}", clock(), 0L,
                )
                is UpdateCheckResult.UpToDate -> HealthCheckResult(
                    "update.status", HealthStatus.OK, "更新状态", "已是最新版本", "state=up_to_date", clock(), 0L,
                )
                is UpdateCheckResult.NoInstallableApk -> HealthCheckResult(
                    "update.status", HealthStatus.WARNING, "更新状态", "最新 Release 缺少可安装 APK", "state=no_apk", clock(), 0L,
                )
                is UpdateCheckResult.InvalidMetadata -> HealthCheckResult(
                    "update.status", HealthStatus.UNVERIFIABLE, "更新状态",
                    "远端版本元数据无效：${result.message}", "state=invalid_metadata", clock(), 0L,
                )
                is UpdateCheckResult.Failed -> HealthCheckResult(
                    "update.status", HealthStatus.UNVERIFIABLE, "更新状态", "检查失败：${result.message}", "state=failed", clock(), 0L,
                )
            }
        },
    )

    private companion object {
        const val STALL_THRESHOLD_MS = 10 * 60 * 1000L
        const val APPROVAL_STALL_MS = 30 * 60 * 1000L
    }
}

/** Bounded repair actions; each only touches its declared, recoverable scope. */
class ProviderRetestAction(
    private val settingsManager: AiSettingsManager,
    private val providerRegistry: AiProviderRegistry,
) : RepairAction {
    override suspend fun perform(issue: com.tim.futureai.domain.health.HealthIssue): Pair<RepairOutcome, String> {
        val profileId = issue.checkId.removePrefix("provider.")
        val profile = settingsManager.profiles().firstOrNull { it.id == profileId }
            ?: return RepairOutcome.FAILED to "未找到对应服务商配置"
        if (!profile.isAvailableForRouting) return RepairOutcome.STOPPED to "配置不完整或已停用，无法自动修复"
        val provider = providerRegistry.create(profile.providerProtocol) { settingsManager.configFor(profileId) }
        return when (val result = provider.testConnection()) {
            is AppResult.Success -> RepairOutcome.SUCCESS to "连接重建成功（等待复检确认）"
            is AppResult.Failure -> RepairOutcome.FAILED to "重试连接仍失败：${result.error}"
        }
    }
}

class TaskResumeRepairAction(private val taskEngine: TaskEngine, private val preferencesManager: AppPreferencesManager) : RepairAction {
    override suspend fun perform(issue: com.tim.futureai.domain.health.HealthIssue): Pair<RepairOutcome, String> {
        val policy = preferencesManager.load().toTaskEnginePolicy()
        val resumed = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        for (aggregate in taskEngine.tasks.value) {
            val canResume = aggregate.task.status == TaskStatus.PAUSED ||
                (aggregate.task.status == TaskStatus.WAITING_FOR_APPROVAL && aggregate.steps.none { it.status == TaskStepStatus.UNKNOWN })
            if (!canResume) continue
            val ok = taskEngine.resume(aggregate.task.id, policy)
            if (ok) resumed += aggregate.task.id else skipped += aggregate.task.id
        }
        return when {
            resumed.isEmpty() && skipped.isEmpty() -> RepairOutcome.STOPPED to "没有可恢复的任务"
            skipped.isEmpty() -> RepairOutcome.SUCCESS to "已恢复 ${resumed.size} 个任务（等待复检确认）"
            else -> RepairOutcome.FAILED to "恢复 ${resumed.size} 个；${skipped.size} 个含结果不确定步骤，需人工处理"
        }
    }
}

class CacheCleanRepairAction(private val context: Context) : RepairAction {
    override suspend fun perform(issue: com.tim.futureai.domain.health.HealthIssue): Pair<RepairOutcome, String> {
        val cacheDir: File = context.cacheDir
        val removed = cacheDir.listFiles().orEmpty().count { it.exists() && it.deleteRecursively() }
        val remaining = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return if (remaining < 50L * 1024 * 1024) RepairOutcome.SUCCESS to "已清理缓存目录（$removed 项，剩余 $remaining 字节；等待复检确认）"
        else RepairOutcome.FAILED to "清理后缓存仍为 $remaining 字节"
    }
}

fun repairStrategies(): List<RepairStrategy> = listOf(
    RepairStrategy("provider.*", "provider.retest", "重建服务商连接", "重新执行连接测试以恢复会话；不修改任何配置或密钥", maxAttempts = 2, timeoutMs = 12_000),
    RepairStrategy("tasks.flow", "task.resume", "恢复停滞任务", "恢复暂停或待确认且无不确定结果的任务；最多尝试 1 次", maxAttempts = 1, timeoutMs = 15_000),
    RepairStrategy("storage.state", "storage.cacheclean", "清理缓存目录", "仅清理应用 cache 目录；不触碰聊天附件、密钥或已校验更新包", maxAttempts = 1, timeoutMs = 10_000),
)
