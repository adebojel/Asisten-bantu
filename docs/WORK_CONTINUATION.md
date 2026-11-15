# KOBRA OS Development Handoff

Updated: 2026-11-16 (Asia/Jakarta)

## Active development branch

- Repository: https://github.com/adebojel/Asisten-bantu
- Main branch: `main`
- Active PR: https://github.com/adebojel/Asisten-bantu/pull/16
- Branch: `research/open-source-expansion-2026-11-15`
- Live head SHA: see the PR link above (this file is updated on the same branch, so the head advances when the handoff is refreshed).
- PR state: open, mergeable; deliberately not merged while more integration and final verification remain.

## Implemented in this branch

1. Expanded open-source repository catalog with verified license status and integration decisions.
2. Added minimal-overlay contract: only the 44dp microphone orb is persistent; other visual surfaces stay hidden unless explicitly requested.
3. Tightened transparent word-focus overlay: no fill, no dim layer, no touch interception, thinner stroke and smaller padding.
4. Added strict `FocusedWordRangeResolver`: only one real selected word or the word touching a real caret can be outlined. Multi-word selections, absent selection metadata and guessed character-zero fallback are rejected.
5. Added per-character geometry guard: only request `EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY` when the active accessibility provider advertises it. No whole-line/paragraph substitute.
6. Added `TargetLabelMatcher` adapted from ScreenSaathi's MIT target-ranking design. It ranks exact/normalized/word-boundary matches, prefers smaller actionable targets, and rejects near-tied controls at different screen locations.
7. Added unit tests for word-range selection, missing caret, paragraph rejection, Unicode boundaries, ambiguous labels, overlapping controls and risky agent targets.
8. Connected explicit screen-analysis requests to `OnlineAiRouter.askBlocking()` using the current screenshot plus accessibility context. The router is restricted to configured, live-verified, zero-price, image-capable models. If unavailable, KOBRA honestly falls back to local accessibility text.
9. Added a bounded screen-navigation agent for multi-step, low-risk navigation only: open app, uniquely resolved safe click, scroll, Back and Home. No typing, Enter, message sending, calls, payment, purchase, delete, permission grant, upload, settings toggle or generic confirmation action.
10. The agent checks a fresh screen snapshot after each action, stops if the screen does not change, supports `hentikan agen layar` / `batalkan agen layar` / `stop agen layar`, and does not claim the user's goal succeeded merely because the model says `done`. It does not save unverified macros.
11. Added `docs/THIRD_PARTY_NOTICES.md` for the ScreenSaathi MIT design reference.
12. Added ADRs for offline Indonesian Sherpa ASR and droid-mcp tool integration boundaries.

## CI status

- Build and unit tests passed for commit `543c8ffdac2431e7a5a91321a444249dfe5d5955`: https://github.com/adebojel/Asisten-bantu/actions/runs/43740565298
- Build passed for commit `687faec03594bf69ee7cf84c847167548728d6fd`: https://github.com/adebojel/Asisten-bantu/actions/runs/43740555011
- Latest commits `70cf95ee...` and `2752ecfd...` add documentation/decision records only; their Actions runs were still in progress at handoff time.
- Earlier intermediate commits failed due missing not-yet-added files and one test that correctly caught the `panggil` / `panggilan` prefix bug. These were fixed; the later code+test commit `543c8ff...` passed.
- Device-level behavior remains unverified; physical-device testing is still deferred.

## Open-source audit decisions

- High-value sources studied: ScreenSaathi (MIT), droid-mcp (Apache-2.0), Sherpa-ONNX (Apache-2.0), LiveSub offline ASR (MIT code; model license separate), MantiClaw-Agent (MIT), Handy AI (MIT), SIGHT (Apache-2.0), SightBuddy (MIT), OpenDroid (Apache-2.0 license text), OnScreen-Agent (MIT), MobileAgent-Android (MIT), NeuralBridge MCP (Apache-2.0 license text), and others listed in the repository catalog.
- Do not copy AGPL/GPL/BSL or unlicensed code into KOBRA without an explicit license decision.
- droid-mcp has 160+ tools and minSdk 28, but its `droid-mcp-all` aggregator includes duplicate service owners; `droid-mcp-core` also brings Ktor/Netty dependencies. ADR requires a measured, curated read-only SDK subset, no upstream AccessibilityService/camera/overlay/listener, and no HTTP server by default. No droid-mcp runtime dependency has been added yet.
- LiveSub lists a 247MB multilingual Sherpa model including Indonesian. Model license and provenance are separate from MIT app code. Do not start a second AudioRecord/microphone owner; first define a single PCM capture contract and benchmark on-device.
- Qwen3/LiteRT-LM remains KOBRA's only local LLM runtime. Do not add GHOST/Gemma or a second local inference engine without measured benefits.

## Legacy branches

- Legacy branches have no unique file paths compared with main, but several are divergent and modify existing files.
- Do not merge them wholesale. Inspect each meaningful diff and port only missing, tested functionality.
- The connected GitHub tools currently expose no branch-delete operation. Do not claim branch cleanup is complete; after all reviewed work is safely on main, delete obsolete branches through a supported GitHub UI/API path. Preserve main and any required release branch.

## README and release work pending

- Main README already has 3D concept SVGs: `docs/kobra-hero-3d.svg` and `docs/kobra-overview.svg`.
- Main README already links latest APK, releases, Actions and source.
- The only PNG found under `public/assets/aistudio/` is a CI-failure screenshot, not a product screenshot. Do not use it as app UI.
- At final pre-test stage, rewrite README comprehensively, embed the existing 3D concept art and architecture diagram, add only genuine app screenshots supplied/verified, and clearly distinguish implemented, experimental and planned functions.
- Document architecture, permissions, online/offline behavior, model download sizes/licenses, privacy, install/troubleshooting, release links and checksums.
- The main release workflow builds a debug APK and attaches `Kobra-OS-main.apk` plus `SHA256SUMS.txt` on main pushes. Label it a testing/debug build unless proper release signing is configured.

## Next actions

1. Re-check CI for latest head `2752ecfd...`; inspect logs and fix any failure before proceeding.
2. Continue source-level review of droid-mcp's selected read-only modules and assess dependency footprint before adding runtime dependencies.
3. Review the bounded agent code and tests for any remaining side-effect paths; keep no typing/submit or dangerous controls.
4. Verify word bounds only use provider-supplied per-character geometry and that the overlay is mic-only at rest.
5. Review legacy branch diffs and port only missing tested functionality.
6. Once implementation and CI are stable, merge PR #16 and any remaining reviewed changes into main.
7. Update README, verify main build/release artifacts and checksum.
8. Clean obsolete branches only after merge and after confirming no unique work remains.
9. Device testing remains deferred until implementation is ready and the user authorizes the test phase.
