# Changelog

## v0.1.9.01 (Revise1) — Beta / Internal Development

- Session management: archive and restore sessions, archived filter tab, batch selection with confirmed bulk delete (archive keeps messages; active session cannot be archived).
- versionCode 20; no business logic changes.

## v0.1.8.01 (Revise2) — Beta / Internal Development

- Chat layout root-cause fixes: title stays horizontal (single-line, compact toolbar), bubbles size from the real window width (removed forced 86% fill), message action row scrolls horizontally instead of stacking characters vertically.
- Request-completed card is transient (1.5s) and never blocks the conversation; failure/cancel states still persist until dismissed.
- versionCode 19; no business logic changes.

## v0.1.7.01 (Revise2) — Beta / Internal Development

- Readability-first Liquid Reality: raised glass tint opacity across all four themes so chat body, composer and dialogs stay clearly readable; blur and refraction remain style accents.
- Replaced the emoji microphone and text plus icons in the composer with vector glyphs matching the shared stroke style and sizing (functionality unchanged).
- Compact settings home: slimmer version card (one-line version/build, model and update/provider summary inline) and reduced top padding so search and categories enter the first screen sooner.
- Fixed system back: settings center and its subpages (and task center) now return step-by-step via system back / edge swipe instead of closing the app; opted into Android predictive back (`enableOnBackInvokedCallback`).
- New launcher icon: glassy planet-and-orbit brand mark as the adaptive icon foreground over the dark ambient background, with a matching planet/ring monochrome layer; legacy (pre-Android 8) launcher entries updated to the same art.
- versionCode 17; no business logic changes.

## v0.1.7.01 (Revise1) — Beta / Internal Development

- Project Liquid Reality: full visual identity rebuild with a shared design system (`DESIGN_SYSTEM.md` + `ui.liquid`), not a color swap.
- Four complete, persistable themes — Liquid Indigo (default), Obsidian, Arctic (light), Aurora — each with its own ambient backdrop, glass material parameters, semantic colors and motion tokens; switching themes changes the whole atmosphere.
- Real liquid glass rendering: stages record background + scrolling content into a GraphicsLayer; fixed glass surfaces (top bars, composer, headers, prototype panels) sample and truly blur what is behind them via RenderEffect (API 31+), with tint, specular and edge highlight passes.
- AGSL edge refraction on the Liquid material (API 33+): background is displaced outward near panel edges for a thickness/refraction approximation; gracefully disabled below API 33 or on shader failure (blur + highlight only).
- Five reusable materials — Clear, Frosted, Liquid, Dark, Accent — with per-theme parameters; scrolling content uses fast tint-only soft surfaces, and low-performance mode / dialogs fall back to tint-only glass.
- Chat home rebuilt on a glass stage: messages scroll under the glass top bar and composer (both blur real content), theme-aware bubbles, code blocks and input states; session manager and dialogs follow the same tokens.
- Settings center rebuilt on glass stages with a new Appearance page: theme picker with live preview cards, glass intensity slider (real blur radius/tint control), low-performance mode, dynamic background drift (Aurora), plus existing font/spacing/animation controls.
- Task center rebuilt: status rail timeline with per-step glyphs + colors + labels (not color alone), waiting-for-approval badge in the header, theme-driven status colors.
- Liquid Glass Prototype page (Settings → Appearance) for material acceptance: textured scrolling test field, all five materials, multi-layer glass, scroll-through blur and edge refraction.
- New user controls are real render behavior: glass intensity, low-performance mode and dynamic background change actual rendering, persisted in encrypted preferences with legacy theme migration.

### Scope notes

- True refraction is an AGSL approximation of edge displacement; it is not full physical light simulation and is disabled on API < 33.
- Dialogs render in separate windows and use tint-only glass (no background sampling); documented in DESIGN_SYSTEM.md.
- No backend/business logic changes: provider configuration, key encryption, chat flow, task engine state machine and update flow behavior are unchanged.

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
