## v0.1.11.02 (Revise1) — Continuous Surfaces & Readability

- Replace standard rounded rectangles across the app with sampled superellipse corners for continuous curvature; retain true circular controls.
- Refine liquid glass with more visible backdrop blur, lower opaque tint, quieter specular highlights and thinner edge reflections so panels read as glass rather than glossy plastic.
- Raise tiny caption text to a more readable size and let long arena answers display in full instead of silently clipping after twelve lines.
- Keep glass lightweight/API fallbacks and follow-system appearance work from the in-progress 0.11.02 branch.
- Version code: 65. Internal development signing. Build and device acceptance pending.
# Changelog

## v0.1.11.01 (Revise1) — Interface & Interaction Refactor

- Rebuild chat around a restrained graphite/black/white palette, a searchable conversation drawer, full-width assistant replies, and a floating composer. Retain six selectable themes and configurable accents.
- Connect the real background recording layer to optical glass: frosted headers, rounded input lens, restrained highlights and API 33+ edge refraction. Reuse blur effects, avoid recursive sampling, and provide opaque lightweight fallbacks.
- Unify forward/back page transitions, message entry, composer resizing, and send/stop state transitions. Respect app/system reduced motion; stop idle ambient animation when it is disabled.
- Measure headers and composer dynamically across chat, settings, providers, tasks, health, arena, audit and link. Follow streamed replies after layout/keyboard changes, allow browsing older content, and hide the keyboard when sending or opening the drawer.
- Add Markdown tables with horizontal scrolling/alignment, language labels for code, inline code and clickable HTTP(S) links. Correct code-action contrast in light themes.
- Reorganize settings into readable groups; restore glass intensity and lightweight rendering controls; preserve system font scaling, draft editing, search, model switching, saved messages and instruction libraries.
- Make onboarding connection tests transient and surface save failures. Persist instruction categories/favorites correctly. Restore the previous conversation on relaunch. Use a neutral launcher/splash mark.
- Includes the unshipped 0.1.10.25 cross-protocol custom-header work. Version code: 64. Internal development signing.

## v0.1.10.25 (Revise1) — Internal Development

- Extend encrypted custom request headers to Anthropic Messages, Gemini Interactions, and Gemini GenerateContent, including chat, model-list, and connectivity requests.
- Keep protocol-owned authentication/version headers reserved and redact configured custom values from provider error messages across all four adapters. versionCode 63.

## v0.1.10.24 (Revise1) — Internal Development

- Add per-profile connection timeout (1–120 seconds) and response idle timeout (1–600 seconds) for OpenAI-compatible providers, applied to chat, model catalog, and connection checks.
- Keep blank values on existing defaults (15-second connect, 60-second read); explain that read timeout limits the gap between streamed chunks rather than total generation time.
- Persist timeout choices and include them in portable profile JSON, validation, cloning, and duplicate detection. versionCode 62.

## v0.1.10.23 (Revise1) — Internal Development

- Add OpenAI-compatible custom request headers for tenant, gateway, and version metadata; send them with chat, model-catalog, and connection-test requests.
- Validate header names, duplicate keys, control characters, reserved transport/auth headers, entry count, and total size; redact configured header values from provider error text.
- Store headers in the encrypted profile, retain them when cloning, and exclude/reject them in portable JSON because values can contain credentials. versionCode 61.

## v0.1.10.22 (Revise1) — Internal Development

- Add custom categories and favorite markers to saved system-instruction templates, with category/favorites filters and favorite-first sorting.
- Upgrade portable template JSON to v2 while retaining v1 import compatibility; templates without category or favorite metadata migrate to 通用 and unstarred. versionCode 60.

## v0.1.10.21 (Revise1) — Internal Development

- Add versioned JSON export and validated import for the encrypted system-instruction template library, with duplicate-ID skipping and a 1 MB input cap.
- Copy exports to the clipboard and allow pasted imports from both provider settings and session instructions; imports preserve existing templates and respect the 100-entry limit. versionCode 59.

## v0.1.10.20 (Revise1) — Internal Development

- Add a shared encrypted system-instruction template library usable from provider settings and per-session instructions.
- Support local template creation, editing, search, replace/append application, and deletion; cap the library at 100 entries and each instruction at 10,000 characters. versionCode 58.

## v0.1.10.19 (Revise1) — Internal Development

- Add persistent, per-conversation system instructions with concise, learning, coding, and meeting presets, including replace, append, and clear actions.
- Include the instruction in new, retried, and regenerated requests without adding it to the visible conversation transcript; older session files load with an empty instruction. versionCode 57.

## v0.1.10.18 (Revise1) — Internal Development

- Add four ready-to-use provider system-instruction templates for concise assistance, coding, research, and translation, with explicit replace or append actions.
- Add a one-tap clear action and retain the live 10,000-character limit and counter. versionCode 56.

## v0.1.10.17 (Revise1) — Internal Development

- Add a profile-level system instruction shared across that provider's requests; it is combined with existing system messages and mapped to each protocol's native field.
- Persist instructions with encrypted profiles and secret-free single/batch JSON exports; limit the editor and validation to 10,000 characters. versionCode 55.

## v0.1.10.16 (Revise1) — Internal Development

- Implement Gemini GenerateContent for profiles using the legacy Gemini wire format, including text/image parts, system instructions, streamed responses, usage metadata, model catalog, and connectivity checks.
- Keep Gemini Interactions as the modern protocol option; existing GenerateContent profile values now route through their named adapter rather than being rejected. versionCode 54.

## v0.1.10.15 (Revise1) — Internal Development

- Make temperature validation protocol-aware: Anthropic accepts 0–1, while OpenAI-compatible and Gemini settings accept 0–2; blank still omits the parameter.
- Add temperature presets (default, precise, balanced, creative) and output-limit presets (default, short, standard, long), while retaining direct numeric editing and model-specific compatibility guidance. versionCode 53.

## v0.1.10.14 (Revise1) — Internal Development

- Add per-provider temperature and maximum-output-token controls, with range validation, encrypted persistence, and inclusion in single-profile and batch JSON backups.
- Map these controls to OpenAI-compatible, Anthropic Messages, and Gemini Interactions request fields; blank values leave each protocol's existing/default behavior in place. versionCode 52.

## v0.1.10.13 (Revise1) — Internal Development

- Add native Gemini Interactions support with stateless conversation history, system instructions, image/text inputs, SSE streaming, and token usage capture.
- Add Google API-key header authentication, paginated model catalog loading, and a read-only connectivity check; requests set `store=false` so conversation history remains client-managed. versionCode 51.

## v0.1.10.12 (Revise1) — Internal Development

- Add one-file export and import for up to 50 provider profiles, retaining model catalogs, protocols, capabilities, and prices without API Keys or test history.
- Batch import validates every profile before writing, assigns fresh IDs, skips matching entries, and stores new entries disabled until credentials are re-entered and the user enables them. versionCode 50.

## v0.1.10.11 (Revise1) — Internal Development

- Add portable JSON export/import for provider profiles, including model inventory, protocol, routing capabilities, and pricing metadata.
- Exports omit API Keys, profile IDs, and connection history; imports reject secret-bearing files, validate schema and field bounds, create an unsaved draft, and require the user to enter a key before saving. versionCode 49.

## v0.1.10.10 (Revise1) — Internal Development

- Add provider-profile search across profile name, protocol, and default model.
- Add encrypted profile duplication for building independent model/protocol variants; credentials stay inside the encrypted provider bundle, and copied connection-test status is cleared. versionCode 48.

## v0.1.10.09 (Revise1) — Internal Development

- Add authenticated model-catalog loading for OpenAI-compatible and Anthropic providers; Anthropic results follow provider pagination up to 300 IDs.
- Keep fetched model IDs with the profile, let users pick a default from the in-settings menu, and retain manual model entry for custom gateways. versionCode 47.

## v0.1.10.08 (Revise1) — Internal Development

- Add native Anthropic Messages support with text and image inputs, streaming replies, token usage capture, and an authenticated model-catalog connectivity check that does not generate a response.
- Add a per-provider protocol selector in AI settings; existing profiles remain OpenAI-compatible, and the unimplemented Gemini option continues to fail closed. versionCode 46.

## v0.1.10.07 (Revise1) — Internal Development

- Add a distraction-free expanded editor for long chat drafts with explicit save/cancel behavior.
- Add confirmed text-draft clearing that leaves selected attachments intact, while keeping the live character count visible. versionCode 45.

## v0.1.10.06 (Revise1) — Internal Development

- Allow individual chat messages to be bookmarked, persist bookmarks with session history, and expose a searchable current-session collection with jump-to-message and share actions. versionCode 44.

## v0.1.10.05 (Revise1) — Internal Development

- Add portable JSON export and import for custom prompt templates, with format/version validation, a 1 MB input bound, duplicate-ID skipping, and merge behavior that preserves existing prompts.
- Keep prompt backups separate from resettable app preferences and store the in-app library through encrypted local storage. versionCode 43.

## v0.1.10.04 (Revise1) — Internal Development

- Add independent removal for shared files, plus local search and sorting for the shared list and inbox.
- Show file type, received time, item count, and known aggregate sizes to make transfer lists easier to review. versionCode 42.

## v0.1.10.03 (Revise1) — Internal Development

- Add a searchable, categorized prompt library with reusable built-in templates and locally encrypted custom prompts that can be created, edited, and deleted.
- Add quick-start prompts to the empty chat state and a composer shortcut; selecting a prompt appends it to the draft without sending or replacing existing text.
- Show a live character count while composing. versionCode 41.

## v0.1.10.02 (Revise1) — Internal Development

- Add pure black and pure white themes with a global accent-color picker, plus graphite, midnight blue, deep sea, and arctic presets; migrate the prior graphite default to pure black once.
- Simplify appearance settings around base palette and accent selection, and keep monochrome surfaces flat with no blur. versionCode 40.

## v0.1.10.01 (Revise1) — Internal Development

- Refresh the default visual system to restrained graphite and slate blue-gray, reduce ambient glow and glass blur, and migrate prior Indigo defaults once while preserving later explicit theme choices.
- Bring Gather Link into the shared appearance settings and palette; simplify transfer cards and controls. The supplied 1.1.08 browser client matches the embedded client and uses the existing pairing, download, inbox, and upload routes. versionCode 39.

## v0.1.9.15 (Revise1) — Internal Development

- Include message delivery state in Markdown exports so failed, cancelled, or still-generating messages remain distinguishable. versionCode 38.

## v0.1.9.14 (Revise1) — Internal Development

- Add batch export to save selected conversations as one Markdown file, alongside the existing share action; use a timestamped filename and the shared session formatter. versionCode 37.

## v0.1.9.13 (Revise1) — Internal Development

- Make tapping a conversation row toggle its selection in batch mode, keep the active conversation unselectable, and hide single-session controls until batch mode ends. versionCode 36.

## v0.1.9.12 (Revise1) — Internal Development

- Fix batch selection so “select all visible” can also be toggled off, excludes the active conversation and preserves selections outside the current filtered view. versionCode 35.

## v0.1.9.11 (Revise1) — Internal Development

- Save conversation exports with the Markdown MIME type and share selected sessions through a single formatter, with exactly one title per session and clear empty-selection handling. versionCode 34.

## v0.1.9.10 (Revise1) — Internal Development

- Standardized single-session export and multi-session sharing on one complete Markdown format with conversation and message timestamps, model-profile ID, attachment status, and unsent draft text and filenames. versionCode 33.

## v0.1.9.09 (Revise1) — Internal Development

- Session manager batch mode now supports archive, restore and sharing selected conversations as one Markdown message, including message attachment names and unsent drafts. The active session cannot be batch-archived. versionCode 32.

## v0.1.9.08 (Revise1) — Internal Development

- Delete imported attachment files from the app-private store when removing a session or draft, while retaining files referenced by any other session or active message. versionCode 31.

## v0.1.9.07 (Revise1) — Internal Development

- Search conversation attachments by file name alongside message and draft text; previews label the match as an attachment. Only local metadata is searched. versionCode 30.

## v0.1.9.06 (Revise1) — Internal Development

- Search local conversation titles, message text and unsent drafts from session management, with a short matching snippet. Archived and active tabs remain separate; queries stay on device.
- versionCode 29.

## v0.1.9.05 (Revise1) — Internal Development

- Recover session history from an interrupted write's backup, including preserved recovery copies, and retain the original recovery file before subsequent saves.
- If history cannot be read, block writes and history clearing for that run so a temporary empty session cannot overwrite the existing store; display a visible warning.
- Serialize reads and writes across repository instances for the same path, preventing Activity recreation from reading during replacement. versionCode 28.

## v0.1.9.04 (Revise1) — Internal Development

- Added per-conversation local drafts: unsent text and attachment metadata survive conversation switches and reloads. Text edits are saved after a 350 ms pause; leaving the foreground flushes pending changes.
- Conversation drawer and session manager now show draft previews, including attachment-only drafts. Draft edits count as recent activity.
- Sending moves the current draft into message history and clears its draft marker; other drafts never enter the model request. Failed imports retain their error state after restoration.
- Editing a historical message asks users to send or clear any existing draft first, preventing accidental draft replacement.
- Fresh conversations are created after history finishes loading, replacing the old 500 ms startup timer. Activity recreation restores the current conversation.
- Session store remains compatible with existing version-2 records without drafts. versionCode 27.
- Enabled core library desugaring for date APIs on API 24–25, switched periodic work to the compatible time-unit overloads, made glass effect API guards explicit, and declared the notification permission already checked at runtime. Updated AndroidX test dependencies for Android 17 instrumentation.

## v0.1.9.03 (Revise2) — Beta / Internal Development

- Fixed local model chat: Android cleartext traffic is now allowed so http:// Ollama endpoints work; local presets fill a placeholder key (Ollama ignores it) so the profile counts as configured; preset message explains emulator vs phone base URL and OLLAMA_HOST.
- versionCode 26.

## v0.1.9.03 (Revise1) — Beta / Internal Development

- Local model presets: 0.4B / 1B / 2B quick-add buttons create Ollama-style profiles (editable default base URL and tags).
- Smart local routing: in automatic mode the 0.4B profile classifies each request (strict JSON) and selects 0.4B / 1B / 2B, with reason shown in the model explanation; failures fall back to normal auto selection.
- versionCode 25.

## v0.1.9.02 (Revise2) — Beta / Internal Development

- First-run onboarding: connect a provider with Base URL, model and key, real connection test, save or skip; persisted once so it does not reappear.
- versionCode 24.

## v0.1.9.02 (Revise1) — Beta / Internal Development

- Audit & history screen: unified task events, repair records and proactive notifications with kind filters and non-sensitive clipboard export.
- versionCode 23.

## v0.1.9.01 (Revise3) — Beta / Internal Development

- Chat: welcome no longer shows prompt cards; conversation list moved to a top-left button that slides the whole UI right and opens a left drawer (new chat, recent sessions, manage); every app launch opens a fresh conversation.
- Readability: user bubbles are solid accent with white label; text overflow guarded; composer uses IME padding so the keyboard no longer covers the conversation.
- versionCode 22.

## v0.1.9.01 (Revise2) — Beta / Internal Development

- HTTP 429 now parses Retry-After (seconds or RFC 1123 date) and surfaces it; chat retry waits accordingly.
- Update check gate: in-flight blocking plus bounded cooldown, guaranteed release in finally, explicit reset on restart; manual checks recover after cooldown.
- Glass: edge refraction enabled on Frosted and Liquid materials with higher transparency and stronger edge band; settings cards use vector icons instead of text glyphs; screen transitions slide from/to the origin edge.
- versionCode 21.

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
