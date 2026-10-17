# Changelog

## v0.1.6.01 (Revise1) — Beta / Internal Development

- Added a verified task execution core: constrained task states, dependency-checked plans, persisted checkpoints and audit events, guarded retries, pause/resume/cancel, and restart recovery.
- Task plans may be proposed by the currently routed AI provider, but local schema, tool ID, parameter, dependency, cycle and verification-rule checks must pass before a plan can be approved or run.
- Added local-only calculator, JSON validation, literal text-rule and task/evidence query tools. No model-generated code, arbitrary shell, network write, screen control or other-app automation is executed.
- Every tool result is checked by explicit structural, execution-status, result, evidence, business-rule or manual-approval verifiers. Only VERIFIED steps can satisfy dependencies or produce a successful task conclusion; insufficient evidence remains inconclusive.
- Added task center access from chat, real task/step status observation, approval prompts, execution controls, result/evidence/audit detail, and task retention/cleanup settings.
- Task planning usage is attributed to task IDs with actual provider/model, Token counts when reported, duration and price-based estimate when known.
- Replaced the launcher mark with a simpler high-contrast Gather “G” monogram and monochrome adaptive icon.

### Scope notes

- Task planning sends the goal and registered local-tool descriptions to the chosen AI provider. Tool execution itself is local; first-party tools are read-only or deterministic and have no external side effects.
- The task engine establishes extensible permission and recovery interfaces; it does not provide phone-screen automation, arbitrary app control, shell execution, or unattended background-agent behavior.
- Plan review defaults to required. Unsupported goals or model plans that fail local validation are rejected instead of being treated as completed work.

## v0.1.5.02 (Revise1) — Beta

- Added a searchable, categorized settings center for General, Appearance, Chat, Providers, Model Routing, Usage & Cost, Attachments, Voice, Updates, Security & Privacy, Data & Storage, and Diagnostics & About.
- Theme (dark/light/system), font scale, compact chat spacing, transition motion, global routing defaults, voice toggles, startup update checks, and session retention are persisted and affect runtime behavior.
- Added confirmed operations for resetting app preferences, deleting chat history and its referenced attachments, clearing usage records, and clearing temporary cache; each action keeps unrelated data intact.
- Provider connection test result/time is persisted in the encrypted provider bundle. Provider deletion confirms API-key removal and shows linked-session count.
- Added 7/30-day usage filters, refresh, provider/model and session-level totals, voice service status/test entry, release/download summaries, and secret-free diagnostics copying. New requests retain session attribution; legacy records remain marked unknown.

### Scope notes

- Only Chinese strings are bundled, so language switching is not offered. Voice uses Android's system recognition/TTS services; TTS settings open only when the device exposes the system page. Export remains per-conversation from Chat.
- PDF extraction and native non-OpenAI provider protocols are not implemented; unsupported attachments are rejected and unsupported protocols fail closed. Imported files are limited to 10 MB each and five attachments per message.
- Resetting app preferences preserves provider credentials, chat history, and usage. Clearing cache does not remove downloaded/verified update APKs or conversation attachments.

## v0.1.5.01 (Revise1) — Beta

- Provider profiles now support independent encrypted credentials, enable/disable state, a default model plus alternate model IDs, capability declarations, context capacity, and routing preference.
- Added deterministic Auto mode for general chat, coding, reasoning, analysis, long text, and vision requirements. The selected model and rejected-candidate reasons are shown in chat.
- Model choice is saved per conversation; a separate “next message only” action does not change the session default.
- Requests are routed through an explicit provider-protocol registry. OpenAI-compatible HTTP is implemented; unregistered native protocols fail closed instead of receiving the wrong request format.
- Usage summaries are grouped by provider and model. Real token counts are preferred; missing usage and model prices remain unknown rather than being inferred.
- Existing encrypted single-provider configuration is retained and migrated on save; legacy model/session defaults remain usable.

### Scope notes

- Capability checkboxes are provider-declared, not benchmark-verified. Context is treated as unknown unless explicitly configured.
- Automatic mode selects one provider for each request and does not retry or forward the prompt elsewhere after failure. Retrying is user-initiated; Auto mode then makes a fresh routing decision.
- Pricing fields apply to the configured default model. Other model IDs have unknown price until configured separately.
