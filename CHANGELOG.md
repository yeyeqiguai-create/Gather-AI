# Changelog

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
