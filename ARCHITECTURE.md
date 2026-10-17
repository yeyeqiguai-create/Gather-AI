# Gather AI 架构规范

本文档约定 Gather AI（`com.tim.futureai`，Kotlin + Jetpack Compose，单 Gradle 模块 `:app`）的代码分层与规则。所有新代码必须遵守。

## 分层职责

| Package | 职责 | 不允许 |
|---|---|---|
| `feature` | 界面与交互：Compose 页面、ViewModel、导航、UI 状态 | 直接调用 API、数据库、或任何 SDK 网络客户端 |
| `domain` | 业务规则、用例（UseCase）、核心模型（纯 Kotlin） | 依赖任何 Android UI 类（Compose/Activity/Context） |
| `data` | 数据库（Room）、网络请求（Retrofit/Ktor）、Repository 接口的实现 | 被 domain 或 feature 反向依赖 |
| `platform` | 系统能力：通知、无障碍（Accessibility）、语音、后台服务（Foreground Service/WorkManager） | 直接持有业务逻辑；只能通过接口向业务层提供能力 |
| `core` | 通用工具、网络基础设施（OkHttp/拦截器/序列化）、安全（KeyStore、加密）、设计系统（主题、颜色、字体、通用组件） | 依赖 feature/domain/data/platform |

## 依赖方向

```
feature ──▶ domain ◀── data
                ▲
                │（通过接口）
             platform
各层共用 core（core 不依赖任何其他层）
```

## 本地聊天记录

- `domain.chat.ChatSessionsRepository` 定义本地多会话读写边界；`ChatSessionData` 将会话元数据与消息隔离，快照记录当前会话 ID。
- `data.chat.FileChatSessionsRepository` 使用应用私有目录中的版本化 JSON 文件持久化会话；读写通过 IO dispatcher 执行。旧版 `chat-history.json` 只读迁移至新格式，源文件保留；迁移失败不会覆盖源数据。旧消息缺少发送状态时按成功消息读取。
- `ChatSession.isPinned` 持久化于现有 v2 会话 JSON；读取缺少该字段的历史记录时默认为未置顶，不改变旧消息。界面排序为展示层计算：置顶优先，其余按最近活动或创建时间倒序，不重排存储数据。
- `feature.chat.ChatMarkdown` 是纯 Kotlin 的受限 Markdown 区块解析器；Compose 只将解析后的文本和代码块作为文本绘制，不解释 HTML 或加载远程资源。代码块复制使用代码原文独立写入剪贴板。
- AI 请求状态由 `domain.ai.AiRequestPhase` 与 `feature.chat.ChatRequestStatus` 分层表达；Provider 负责报告请求/响应阶段，`AppError` 统一区分连接、超时、配置、模型、HTTP、鉴权、限流、服务端和解析错误。每次请求带代次与会话 ID，切换会话取消旧请求并丢弃迟到结果。
- 聊天记录不包含 AI 配置、API Key 或 Authorization Header；API 凭据仍只由 `SecureKeyStore` 管理。

- `feature → domain`：界面只调用用例/模型。
- `data → domain`：Repository 实现依赖 domain 中的接口和模型。
- `platform` 通过 **domain 或 core 中定义的接口**向业务层提供系统能力（如通知发送器、语音服务），由 DI 注入实现；业务层不感知具体系统 API。
- **禁止循环依赖**：任何 package 不得反向 import 上游层；domain 是被依赖的中心，不依赖任何人。

## 强制规则

1. **UI 不直接调用 API 或数据库**。`feature` 只能通过 `domain` 的用例访问数据；Room DAO 和网络接口只允许被 `data` 层引用。
2. **API Key 安全**：不得硬编码在源码、`BuildConfig` 明文字段或 `gradle.properties` 提交文件中；不得写入日志（Logcat）或异常信息。优先使用 `local.properties`（不入库）+ `BuildConfig`（排除 release 日志）或远端配置。
3. **异步**：一律使用 Kotlin Coroutines / Flow。UI 层用 `StateFlow`/`collectAsState`；Repository 对外暴露 suspend 函数或 Flow；禁止在主线程做阻塞 IO。
4. **权限与安全**：任何关键操作（敏感权限、无障碍、后台、外部通信）必须先经过权限检查与安全策略（`core` 安全模块）确认，由 `platform` 层封装系统调用。

## 何时升级为独立 Gradle Module

当前保持单模块 + package 分层。**只有满足以下条件之一**才把某个 package 升级为独立 module（`feature:xxx`、`data:xxx` 等）：

1. 模块边界已稳定（接口至少经一次真实迭代验证，不再频繁变动）。
2. 确实需要独立编译提速或并行构建（单模块构建时间已成为瓶颈）。
3. 需要隔离依赖（如某能力需要引入与主工程冲突的重型 SDK）。
4. 需要复用到另一个工程。

**不要现在就拆**。过早拆 module 只会带来配置和维护成本。

## 与现有配置的一致性

- 现有 `ui/` 目录（Compose 主题/组件）保留，可逐步并入 `core`（设计系统）；并入前 `feature` 可引用它。
- `MainActivity.kt` 仅作为 Compose 入口承载 `feature` 的根导航，不写业务逻辑。
- minSdk 24 / targetSdk 37 / compileSdk 37，Java 11 兼容：所有层代码需兼容该版本。

## AI 交互模块

- `AiProvider.streamWithUsage` 是兼容层入口；OpenAI-compatible Provider 通过 SSE 逐段读取 `delta.content`，取消时断开 HTTP 连接。请求代次和会话 ID 仍由 `ChatViewModel` 校验，部分回复在失败/停止时保留并标记状态。
- `domain.ai.AiSettings` 保存无密钥的服务商/模型元数据；`data.settings.AiSettingsManager` 通过 `SecureKeyStore` 加密存储密钥。旧版单配置首次保存时迁移；旧会话的 `modelProfileId` 仍映射到服务商默认模型。
- `ModelCapabilityRegistry` 从启用的配置生成无密钥模型目录；`AutomaticModelSelector` 按任务、图片需求、上下文容量、能力来源、服务商最近状态和用户偏好单次选出一个模型。手动模型不会被静默替换；仅用户主动重试时才基于 Auto 模式重新路由，一次请求不会在服务商之间自动转发。
- `AiProviderRegistry` 按 `ProviderProtocol` 选择网络适配器。适配器由 data 层注册；当前只注册 OpenAI-compatible，未注册的原生协议会在发送前失败。
- 附件导入由 `platform.files.AttachmentImporter` 执行：图片存于应用私有目录并生成缩略图，TXT/MD/CSV/JSON/XML 与 DOCX 提取为本地文本；未支持的格式明确拒绝。只有启用图片能力的配置可发送图片，网络请求中图片以 data URL 发送。
- `UsageRepository` 按实际 Provider/模型保存耗时、错误类别和真实 Token 数。费用仅按模型 ID 精确匹配的用户单价估算；缺少真实 Token 或对应单价时保留为未知。
- `platform.speech.SpeechController` 封装 Android TTS；语音输入交给系统识别 Intent，未安装识别服务或权限被拒绝时回退为明确提示。
- 用户消息支持编辑（截断后续依赖回复）、分享；整段会话通过 Android 文件保存器导出。

## 全量设置中心

- `domain.settings.AppPreferences` 是通用偏好与设置分类注册表；`SettingsSearchRegistry` 注册真实可用设置项与动态 Provider/模型条目，搜索结果直接导航到所属分类。
- `data.settings.AppPreferencesManager` 经 `SecureValueStore` 加密保存主题、聊天显示、路由默认值、语音、启动更新和历史保留配置；UI 仅发出事件，由宿主协调数据与平台操作。
- Light/Dark/System 共享动态调色板；字体比例通过 Compose `Density` 生效，紧凑间距仅调整消息列表布局。
- `AiSettingsManager` 持久化脱敏连接测试状态。用量记录新写入会话 ID 并按 Provider/模型/会话聚合，旧格式缺省字段继续读取并标为未知。设置页将历史、用量、cache 与设置偏好清理分开；cache 不包含引用附件或已校验更新 APK。

## Verified Task Engine

- `domain.task` 保存纯 Kotlin 任务、步骤、验证、工具调用、检查点与有序事件模型。`TaskStateMachine` 集中约束任务/步骤状态；失败任务没有到成功的直接转换，步骤只有在 `VerificationStatus.VERIFIED` 后才能满足依赖。
- `TaskPlanner` 只接收 `TaskPlanSource` 提案。`AiTaskPlanSource` 通过当前 Provider 与自动选模路径请求 JSON 计划；Planner 随后验证 JSON 结构、步骤上限、依赖存在性、环、已注册工具、参数类型/必填项和工具声明的验证规则。无效计划被拒绝，不会进入调度器。`TaskPlanReviewer` 为未来人工调整保留接口，调整结果仍须重新验证。
- `TaskEngine` 是任务生命周期与调度所有者；只按依赖顺序执行，阻止重复启动，以确定的幂等键标记调用，并把 invocation-start/checkpoint 在工具执行前写入仓库。安全且幂等工具只允许按用户上限有限重试；非幂等或不确定结果进入暂停/UNKNOWN，不盲目重放。
- `VerificationEngine` 按每个工具声明的规则进行输出结构、执行器成功状态、可计算结果、当前步骤证据、业务规则和人工确认检查。FAILED/INCONCLUSIVE/REQUIRES_APPROVAL 与 VERIFIED 分开处理；只有所有步骤 VERIFIED 才生成成功结论。
- `TaskToolRegistry` 注册本地计算、JSON 验证、文本字面规则和任务/证据只读查询。参数经声明 Schema 检查；所有工具注明权限、副作用、确认要求、幂等性、超时、重试和验证规则。注册表不执行模型生成代码，也不提供手机屏幕或其他应用控制能力。
- `FileTaskRepository` 把任务 aggregate（步骤、调用、检查点、证据、事件）写在单个应用私有版本化 JSON 快照中，以临时文件+备份重命名提交。恢复先询问工具检查既有结果；只有已确认无副作用且幂等的调用可在显式继续后重新执行。历史清理只作用于终态任务并需 UI 确认。
- `feature.task.TaskCenterScreen` 订阅 `TaskEngine.tasks` Flow，而非合成进度；展示计划、依赖、实际结果、证据、审核、等待授权、失败、恢复和最终结论。`MainActivity` 仅组装 task store、registry、当前 AI route 与屏幕入口。
- 规划请求把目标和工具说明发送给所选 Provider；实际规划 usage（模型、Provider、Token、耗时、可估费用）使用 `taskId` 写入既有用量 ledger。首批工具本地执行，不产生模型费用。
- `AppPreferences` 持久化任务模式、授权默认策略、步骤/重试上限、单步/总时限、计划复核与历史保留；工具自身 `requiresApproval` 始终优先于全局默认策略。

## Liquid Reality Design System

- `ui.liquid` 持有完整设计系统：`LiquidSpec`（每套主题的环境背景、玻璃材质参数、语义色与动效令牌）、四套主题规格（Liquid Indigo / Obsidian / Arctic / Aurora）、`GatherLiquidTheme` 根组合与 `GlassRenderPrefs` 用户渲染偏好。规范文档见 `DESIGN_SYSTEM.md`，与实现同步维护。
- 主题标识 `domain.settings.VisualTheme` 为纯 Kotlin 枚举，经 `AppPreferences` 加密持久化；旧存储缺失该字段时按 `themeMode` 迁移（LIGHT→Arctic，其余→Liquid Indigo）。
- `LiquidStage` 把页面分为 source（环境背景 + 滚动内容，经 `Modifier.liquidSource` 记录进 GraphicsLayer）与 overlay（固定玻璃表面）。`Modifier.glassSurface` 采样 source 层并经 RenderEffect 真实模糊（API 31+），叠加 tint、镜面与边缘高光；Liquid 材质在 API 33+ 经 AGSL RuntimeShader 做边缘折射近似。
- 位于 source 内的滚动内容使用 `Modifier.softSurface`（仅 tint，无模糊），避免递归采样与逐卡片模糊开销；对话框窗口无 stage source，自动退化为 tint-only。低性能模式关闭实时模糊与折射，仅保留主题配色。
- 兼容层 `ui.theme.FuturePalette` / `glassPanel` / `FutureBackdrop` 委托到同一设计系统；新代码应直接使用 `ui.liquid` 令牌。页面内禁止硬编码颜色、圆角与动画参数。
