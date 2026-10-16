# Gather AI

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

Gather AI currently supports SSE streaming chat, per-conversation model selection across configurable OpenAI-compatible providers, image and document attachments, system speech input and TTS, usage tracking, and message/session tools. The app also includes secure credential storage and GitHub Release updates. Unsupported attachment formats and unavailable provider usage are reported explicitly.

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
4. Explore optional model verification and intelligent task workflows.
5. Expand Android integrations with user-controlled permissions.

## 📄 License

The project license has not yet been specified. Please check the repository for licensing information before redistributing or reusing the code.

## 当前能力

- SSE 流式聊天，支持停止、重试、重新生成和保留部分回复。
- 多服务商/模型配置、单会话模型选择、Android Keystore 密钥保护与连接测试。
- 图片、文本和 DOCX 附件导入；图片分析需在模型设置中显式启用视觉能力，未支持格式会提示原因。
- 系统语音输入与 TTS 朗读、实际 Token/耗时/错误统计、可选单价下的费用估算。
- 消息编辑/复制/分享、会话文本导出。

## 版本管理

- 版本名和 Android `versionCode` 的唯一配置源是根目录 `gradle.properties` 中的 `gather.versionName` 与 `gather.versionCode`。
- 版本名必须符合 `vX.X.X.XX (ReviseX)`；基础功能完善前，Gradle 校验只接受 `0.1.x`，拒绝升级到 `0.2` 或 `1.0`。
- 每次 Beta 或正式测试分发前都要提高 `gather.versionCode`，不得复用或降低已分发的版本码。它与面向用户显示的版本名分开管理。
- 内部开发使用 `gradlew.bat assembleDebug`，APK 文件名带 `internal-dev` 标记。
- Beta 使用 `gradlew.bat assembleRelease -Pgather.releaseChannel=beta`，APK 文件名带 `beta` 标记。
- 正式测试使用 `gradlew.bat assembleRelease`，默认 APK 文件名带 `formal-test` 标记。也可显式指定 `-Pgather.releaseChannel=formal-test`。
- Beta 和正式测试共用现有 release 构建，不增加构建变体；渠道属性只区分产物文件名。当前未配置 release 签名，产物签名与分发准备仍需单独处理，签名密钥不得提交到仓库。
