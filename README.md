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

Gather AI is under active development. The project currently includes foundational AI chat, conversation management, Markdown rendering, request error handling, secure API credential storage, and an in-app update mechanism.

Features such as broader multi-provider management, streaming improvements, multimodal input, voice interaction, usage analytics, and advanced AI workflows are part of the development roadmap and should not be considered complete until implemented and verified.

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

1. Improve the core chat experience and streaming reliability.
2. Expand multi-provider and per-conversation model management.
3. Add image, document, and voice workflows.
4. Introduce usage tracking and cost estimation.
5. Develop optional model verification and intelligent task orchestration.
6. Expand Android integrations with user-controlled permissions.

## 📄 License

The project license has not yet been specified. Please check the repository for licensing information before redistributing or reusing the code.
