# Gather AI

Current internal build: **v0.1.11.02 (Revise2)**, versionCode **66**.

The 0.11 interface uses restrained monochrome and low-saturation themes, optical glass headers/composer, coordinated page transitions, a searchable conversation drawer, rich Markdown and a reorganized settings center. Glass intensity, lightweight rendering, animations and typography are configurable. Android 7+ is supported; background blur requires Android 12+, and edge refraction requires Android 13+.

Validation: 261 JVM tests and 6 emulator instrumentation tests passed; debug APK assembly and lint passed (0 errors; existing warnings remain). The emulator checks use a local SSE service and the embedded Gather Link HTTP server. Real-provider billing/availability, physical-device frame rate, speech services and multi-device/router behavior remain outside this acceptance run. See `DESIGN_SYSTEM.md` for the implemented visual contract.

**Your AI. Your Models. Your Workflow.**

Gather AI is an Android-native AI assistant built with Kotlin and Jetpack Compose. It aims to bring multiple AI providers, personalized conversations, and intelligent task workflows into one unified mobile experience.

Instead of being limited to a single AI model, Gather AI is designed to evolve into a flexible AI workspace where users can choose models, manage conversations, and gradually extend their assistant with tools and automation.

## ✨ Vision

- **Multi-Model Intelligence** — Connect different AI providers and choose the right model for each task.
- **A Better Chat Experience** — Build a reliable conversation interface with streaming responses, rich message rendering, and practical message controls.
- **Personal AI Workflows** — Extend conversations into multi-step tasks with tools, permissions, and execution tracking.
- **Evidence-Based Verification** — Explore ways to verify important AI responses using evidence, tests, and optional secondary-model review.
- **User-Controlled Privacy** — Keep API credentials protected and make integrations and permissions explicit.
- **Continuous Evolution** — Expand the assistant through modular architecture and incremental releases.

## 🚀 Current Development

Gather AI currently supports SSE streaming chat, per-conversation model selection across configurable OpenAI-compatible providers, image and document attachments, system speech input and TTS, usage tracking, and message/session tools. The app also includes secure credential storage, GitHub Release updates, and Gather Link for short-lived local-network file transfer between Android and browser-based devices.

## 🛠️ Technology Stack

- Kotlin
- Android SDK
- Jetpack Compose
- Kotlin Coroutines and Flow
- OpenAI-compatible API integration
- Android Keystore for API credential protection

## 🔒 Security Principles

- API credentials must not be hard-coded or committed to source control.
- Sensitive configuration should use protected local storage.
- Tool execution and Android integrations should follow explicit permission boundaries.
- Network failures and model errors should be handled without exposing secrets.

## 📦 Development Status

Gather AI is an evolving project. Features, interfaces, and architecture may change as development progresses. Internal test releases are intended for testing and feedback rather than as guarantees of production readiness.

## 🗺️ Roadmap

1. Improve streaming reliability and broaden provider compatibility.
2. Expand document format support and accessibility.
3. Improve usage analytics and provider-specific cost reporting.
4. Expand verified local task tools while retaining evidence and permission boundaries.
5. Expand Android integrations with user-controlled permissions.

## 📄 License

The project license has not yet been specified. Please check the repository for licensing information before redistributing or reusing the code.

## 当前能力

- 会话导出和批量分享使用统一 Markdown 格式，包含会话时间、消息时间、附件状态和未发送草稿。
- 会话管理支持批量归档、恢复与合并分享所选会话，导出内容会标注消息、附件名称及未发送草稿。
- 删除会话或草稿时自动清理不再引用的应用内附件，仍被其他会话使用的文件会保留。
- 会话管理支持按会话名称、消息、草稿及附件文件名搜索，匹配结果显示本地上下文预览。
- 每个会话独立保存未发送的文字和附件草稿，切换会话可恢复；输入停顿 350 ms 后自动写入本地，进入后台时触发保存。会话侧栏与管理列表显示草稿预览，发送后清除当前草稿。重新打开应用仍从新会话开始，之前的草稿可从侧栏找回；系统重建界面时恢复当前会话。编辑历史消息前需先处理当前草稿，以免覆盖。
- SSE 流式聊天，支持停止、重试、重新生成和保留部分回复。
- 多服务商/模型配置、单会话模型选择、Android Keystore 密钥保护与连接测试。
- 图片、文本和 DOCX 附件导入；图片分析需在模型设置中显式启用视觉能力，未支持格式会提示原因。
- 系统语音输入与 TTS 朗读、实际 Token/耗时/错误统计、可选单价下的费用估算。
- 消息编辑/复制/分享、会话文本导出。
- Liquid Reality 视觉体系：四套完整主题（Liquid Indigo、Obsidian、Arctic 浅色、Aurora）、真实液态玻璃渲染（背景采样模糊 + AGSL 边缘折射近似）与五种玻璃材质；用户可控制材质强度、低性能模式与动态背景，全部真实作用于渲染。
- Verified Task Engine：从聊天进入任务中心，由当前路由模型提议结构化计划，再经本地依赖/Schema/权限/验证规则校验后执行；任务状态、检查点、恢复记录和审计事件持久化。
- 首批任务工具只在本地运行：安全算术、JSON 必需字段检查、文本包含/排除规则、任务与验证证据查询。所有步骤均需明确验证，只有 `VERIFIED` 步骤能完成依赖或形成成功结论。
- **Gather Link 设备互联**：Android 设备临时开启局域网接收端，手机、平板和电脑通过浏览器输入 6 位配对码连接；可下载手机选中的文件、向手机发送文件，并让本次会话中已配对的设备下载彼此发送到收件箱的文件。支持最多 12 台配对设备，单个接收文件上限 2 GB。收到的文件存放在应用私有收件箱，用户可另存到所选位置或删除；离开互联页后服务器和配对会话关闭。

Gather Link 目前仅支持同一可信局域网内的 IPv4 连接，不提供云端中继或传输加密；请勿在公共 Wi‑Fi 或不可信网络中传送敏感文件。Android 17 设备会在开启互联前请求本地网络权限。

任务规划会把用户目标和本地工具清单发送给所选 AI 服务商；执行器不会运行模型生成代码，也不会操作系统界面或任意应用。计划默认需要用户检查后开始，高风险工具即使加入也必须独立逐次确认。

## 版本管理

- 版本名和 Android `versionCode` 的唯一配置源是根目录 `gradle.properties` 中的 `gather.versionName` 与 `gather.versionCode`。
- 版本名必须符合 `vX.X.X.XX (ReviseX)`；基础功能完善前，Gradle 校验只接受 `0.1.x`，拒绝升级到 `0.2` 或 `1.0`。
- 每次 Beta 或正式测试分发前都要提高 `gather.versionCode`，不得复用或降低已分发的版本码。它与面向用户显示的版本名分开管理。
- 内部开发使用 `gradlew.bat assembleDebug`，APK 文件名带 `internal-dev` 标记。
- Beta 使用 `gradlew.bat assembleRelease -Pgather.releaseChannel=beta`，APK 文件名带 `beta` 标记。
- 正式测试使用 `gradlew.bat assembleRelease`，默认 APK 文件名带 `formal-test` 标记。也可显式指定 `-Pgather.releaseChannel=formal-test`。
- Beta 和正式测试共用现有 release 构建，不增加构建变体；渠道属性只区分产物文件名。当前未配置 release 签名，产物签名与分发准备仍需单独处理，签名密钥不得提交到仓库。

## 应用内更新协议

- 客户端先读取 GitHub Releases API；遇到 API 限流或网络故障时回退至 `raw.githubusercontent.com` 的静态 Release 清单；发版时同步更新 `update-manifests/internal-dev.json` 或 `formal.json`。兼容标签格式为 `vX.X.X.XX-ReviseN[-internal-dev]`。内部开发版保留 GitHub Pre-release 标记。
- Release 正文必须包含用户更新说明，并在隐藏的 `<div hidden>` 中提供 `versionCode: N`，供旧客户端识别；新客户端解析后不会把该字段展示为更新说明。
- 每个版本只上传一个 `.apk`，并同时上传 `update.json`。旧客户端忽略 JSON 文件，仍从 Release 正文、APK 资源和 GitHub SHA-256 digest 检查更新。
- 新客户端通过该 Release 的长期资源地址读取 `update.json`，并以 GitHub 资源 SHA-256 作缓存键：`https://github.com/yeyeqiguai-create/Gather-AI/releases/download/<tag>/update.json?download=1&sha256=<asset-digest>`。其中包含 `versionName`、`versionCode`、`releaseNotes`、`apkUrl`、`sha256`；客户端会与 Release 标签、正文及 APK 资源交叉校验。
- 没有 `update.json` 的历史 Release 使用兼容解析；元数据不匹配、APK 缺失或校验信息无效时不会允许安装。

## v0.1.5.02 (Revise1) Beta

- 新增可搜索的分类设置中心：通用、外观、聊天、服务商、自动选模、用量、附件、语音、更新、安全隐私、存储、诊断关于。搜索由设置分类注册表生成。
- 深色/浅色/跟随系统、字体比例、紧凑消息间距、界面转场、默认路由模式、语音开关、启动更新检查和会话数量上限均会持久化并影响运行行为。
- 用量页支持 7 天/30 天/全部筛选、刷新、清除确认，并按服务商、模型和会话汇总；新请求写入会话归属，旧记录显示为会话未知。Provider 测试结果会安全持久化，删除前提示关联会话数。
- 删除聊天历史、清除用量与临时缓存分别确认且影响范围独立；脱敏诊断不包含 API Key、Base URL 或请求正文。
- 搜索来自实际设置项注册表，并包含已配置的服务商和模型；TTS 设置仅在设备提供系统入口时可打开。
- 语言资源目前只有简体中文；PDF 文本提取和非 OpenAI-compatible 原生协议未实现。单个附件上限 10 MB、每条消息最多 5 个附件；会话导出仍从聊天页操作。

## v0.1.5.01 (Revise1) Beta

- 服务商配置按独立加密配置保存，可启停；每个配置有默认模型、额外模型 ID、上下文容量、任务能力声明与自动路由偏好。
- 聊天模型菜单支持按会话固定、自动选模，以及只覆盖下一条消息；临时覆盖不会更改会话默认值。
- 自动选模根据任务、图片要求、上下文需求、能力来源、用户偏好和最近服务商状态路由。每个请求只发送给一个服务商；不会在失败后自动转发，重试由用户触发。
- 当前实际网络适配器为 OpenAI-compatible。其他原生协议若未注册会明确拒绝请求。
- 用量按服务商和模型汇总。费用仅按已配置单价和真实 Token 数估算；多模型的未配置价格保持未知。
- 旧版单服务商加密配置保留，首次保存时迁移；新增字段有默认值，不影响旧会话和密钥。
