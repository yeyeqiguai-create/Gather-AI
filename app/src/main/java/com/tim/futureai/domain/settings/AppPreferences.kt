package com.tim.futureai.domain.settings

import com.tim.futureai.domain.task.TaskAuthorizationPolicy

enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class RoutingMode { MANUAL, AUTOMATIC }

/** Liquid Reality theme identity; persisted and mapped to full visual specs in the UI layer. */
enum class VisualTheme { LIQUID_INDIGO, OBSIDIAN, ARCTIC, AURORA }

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Liquid Reality theme; falls back to themeMode mapping when absent in storage. */
    val visualTheme: VisualTheme = VisualTheme.LIQUID_INDIGO,
    val glassIntensity: Float = 0.6f,
    val lowPerformanceMode: Boolean = false,
    val dynamicBackground: Boolean = false,
    val fontScale: Float = 1f,
    val onboardingDone: Boolean = false,
    val compactChat: Boolean = false,
    val animationsEnabled: Boolean = true,
    val showModelNames: Boolean = true,
    val showRoutingReason: Boolean = true,
    val routingMode: RoutingMode = RoutingMode.MANUAL,
    val inheritGlobalModel: Boolean = true,
    val voiceInputEnabled: Boolean = true,
    val textToSpeechEnabled: Boolean = true,
    val startupUpdateCheck: Boolean = true,
    /** 0 means retain all sessions; otherwise the active session and newest N-1 are retained. */
    val historyLimit: Int = 0,
    val taskModeEnabled: Boolean = true,
    val taskAuthorizationPolicy: TaskAuthorizationPolicy = TaskAuthorizationPolicy.CONFIRM_SIDE_EFFECTS,
    val taskMaxSteps: Int = 12,
    val taskMaxRetries: Int = 1,
    val taskStepTimeoutMs: Long = 30_000,
    val taskTotalTimeoutMs: Long = 300_000,
    val requireTaskPlanReview: Boolean = true,
    val retainTaskHistory: Boolean = true,
) {
    fun normalized(): AppPreferences {
        val stepTimeout = taskStepTimeoutMs.coerceIn(5_000, 120_000)
        return copy(
            fontScale = fontScale.coerceIn(0.85f, 1.25f),
            glassIntensity = glassIntensity.coerceIn(0f, 1f),
            historyLimit = historyLimit.coerceIn(0, 5000),
            taskMaxSteps = taskMaxSteps.coerceIn(1, 30),
            taskMaxRetries = taskMaxRetries.coerceIn(0, 3),
            taskStepTimeoutMs = stepTimeout,
            taskTotalTimeoutMs = taskTotalTimeoutMs.coerceIn(10_000, 900_000).coerceAtLeast(stepTimeout),
        )
    }

    fun usesAutomaticModelSelection(): Boolean = routingMode == RoutingMode.AUTOMATIC || !inheritGlobalModel
}

enum class SettingsSection(val title: String, val description: String) {
    GENERAL("通用", "版本、默认行为、搜索和重置"),
    APPEARANCE("外观", "主题、字体、间距和动画"),
    CHAT("聊天", "路由默认值、模型显示与历史管理"),
    PROVIDERS("AI 服务商", "服务商配置、密钥、模型与连接测试"),
    MODEL_ROUTING("自动选模", "任务分类、模型能力、偏好和回退边界"),
    TASKS("任务引擎", "计划审核、工具授权、执行限制与任务历史"),
    USAGE("用量与费用", "真实 Token、耗时、错误与费用估算"),
    ATTACHMENTS("附件", "支持格式、处理边界和缓存管理"),
    VOICE("语音", "语音识别、系统 TTS 与隐私状态"),
    UPDATES("更新管理", "检查、下载、哈希校验与安装引导"),
    HEALTH("健康与自愈", "自我诊断、有限修复、复检与提醒中心"),
    AUDIT("审计与历史", "任务、修复与通知的可追踪记录"),
    ARENA("Model Arena 与信誉", "多模型对比评审、客观验证与按任务类型信誉"),
    SECURITY("安全与隐私", "密钥保护、本地存储和服务商数据传输"),
    STORAGE("数据与存储", "聊天、用量、缓存和配置迁移状态"),
    DIAGNOSTICS("诊断与关于", "版本、配置状态、脱敏诊断和项目链接");

    companion object {
        val registered: List<SettingsSection> = entries.toList()
    }
}

data class SettingsSearchEntry(val title: String, val section: SettingsSection, val keywords: List<String> = emptyList())

/** Searchable items are the settings actually presented by the registered category pages. */
object SettingsSearchRegistry {
    val entries: List<SettingsSearchEntry> = listOf(
        SettingsSearchEntry("应用版本与构建信息", SettingsSection.GENERAL, listOf("版本号", "versionCode", "复制")),
        SettingsSearchEntry("默认服务商与模型", SettingsSection.PROVIDERS, listOf("默认模型", "API Key", "Base URL")),
        SettingsSearchEntry("设置中心搜索与偏好重置", SettingsSection.GENERAL, listOf("重置", "搜索")),
        SettingsSearchEntry("简体中文界面", SettingsSection.GENERAL, listOf("语言")),
        SettingsSearchEntry("主题模式", SettingsSection.APPEARANCE, listOf("深色", "浅色", "跟随系统")),
        SettingsSearchEntry("字体大小", SettingsSection.APPEARANCE, listOf("字体比例", "字号")),
        SettingsSearchEntry("聊天间距与动画", SettingsSection.APPEARANCE, listOf("紧凑", "转场")),
        SettingsSearchEntry("新会话模型继承与显示", SettingsSection.CHAT, listOf("会话", "模型名称", "选模原因")),
        SettingsSearchEntry("聊天历史保留与清理", SettingsSection.CHAT, listOf("消息", "历史", "删除")),
        SettingsSearchEntry("流式生成、取消与重试", SettingsSection.CHAT, listOf("SSE", "重新生成")),
        SettingsSearchEntry("添加、编辑与测试服务商", SettingsSection.PROVIDERS, listOf("连接测试", "OpenAI-compatible")),
        SettingsSearchEntry("服务商密钥与模型管理", SettingsSection.PROVIDERS, listOf("API Key", "模型列表")),
        SettingsSearchEntry("自动选模与任务分类", SettingsSection.MODEL_ROUTING, listOf("模型", "编程", "推理", "长文本", "能力")),
        SettingsSearchEntry("失败回退与跨服务商授权", SettingsSection.MODEL_ROUTING, listOf("重试", "权限", "隐私")),
        SettingsSearchEntry("Verified Task Engine", SettingsSection.TASKS, listOf("任务模式", "授权", "步骤上限", "超时", "重试", "历史")),
        SettingsSearchEntry("Token 用量与费用估算", SettingsSection.USAGE, listOf("请求", "成本", "USD")),
        SettingsSearchEntry("用量时间范围与价格", SettingsSection.USAGE, listOf("7 天", "30 天", "模型价格")),
        SettingsSearchEntry("图片与文档附件限制", SettingsSection.ATTACHMENTS, listOf("文件", "10 MB", "DOCX", "PDF")),
        SettingsSearchEntry("附件临时缓存清理", SettingsSection.ATTACHMENTS, listOf("缓存")),
        SettingsSearchEntry("系统语音输入与权限", SettingsSection.VOICE, listOf("识别", "麦克风")),
        SettingsSearchEntry("文字转语音与引擎", SettingsSection.VOICE, listOf("TTS", "朗读")),
        SettingsSearchEntry("启动更新检查", SettingsSection.UPDATES, listOf("自动检查")),
        SettingsSearchEntry("更新下载、校验与安装", SettingsSection.UPDATES, listOf("APK", "SHA-256", "授权")),
        SettingsSearchEntry("API Key 加密与隐私", SettingsSection.SECURITY, listOf("Android Keystore", "AES-GCM")),
        SettingsSearchEntry("脱敏诊断信息", SettingsSection.SECURITY, listOf("日志", "复制")),
        SettingsSearchEntry("会话数据与缓存大小", SettingsSection.STORAGE, listOf("存储")),
        SettingsSearchEntry("数据迁移与导出", SettingsSection.STORAGE, listOf("配置", "聊天文本")),
        SettingsSearchEntry("应用诊断与 GitHub", SettingsSection.DIAGNOSTICS, listOf("错误", "项目仓库", "许可证")),
    )

    fun search(query: String, providers: List<Pair<String, List<String>>> = emptyList()): List<SettingsSearchEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val dynamic = providers.flatMap { (provider, models) ->
            listOf(SettingsSearchEntry(provider, SettingsSection.PROVIDERS, listOf("服务商", "配置"))) +
                models.distinct().map { SettingsSearchEntry("$provider · $it", SettingsSection.PROVIDERS, listOf("模型")) }
        }
        return (entries + dynamic).filter { entry ->
            (entry.title + " " + entry.section.title + " " + entry.section.description + " " + entry.keywords.joinToString(" "))
                .lowercase().contains(q)
        }.distinctBy { it.title to it.section }
    }
}
