# Changelog

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
