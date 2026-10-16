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
