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


## Continuation update — 16 November 2026, after next review

- PR #16 remains open and mergeable; branch is 51 commits ahead of main and 0 behind at the last comparison.
- Latest confirmed successful build at review time: workflow run [43741094405](https://github.com/adebojel/Asisten-bantu/actions/runs/43741094405), commit `29cbaab83c5fad5b6504f2d6099ac31372268f2f`. Its steps include `testDebugUnitTest assembleDebug` and APK artifact upload. The catalog-only commit `92e2893b29dce087618f970532445038f0392b38` was made after that run; wait for its CI result.
- Reviewed the active bounded screen agent: at most five steps, only open-app/click-label/scroll/back/home/done, refuses risky target names and resolved node labels/resource IDs, rejects ambiguous target matches, captures a fresh screenshot/tree per step, and stops if the screen fingerprint does not change. It does not type text, send messages, call, pay, buy, delete, or change security settings.
- Added pure tests for risky targets, single-word selection resolution, ambiguity, exact label ranking, Unicode word boundaries and hyphenated labels. Latest successful workflow executes Gradle `testDebugUnitTest` before `assembleDebug`.
- Audited [ScreenSaathi](https://github.com/NITISH-R-G/ScreenSaathi), MIT licensed. Its target resolver is an architecture reference; KOBRA keeps its independently implemented Unicode-aware matcher and does not import its voice/planner/overlay/task service. Added the decision to the repository catalog and retained third-party notice.
- Audited droid-mcp as an Apache-2.0 architecture reference; no runtime dependency imported. Avoid `droid-mcp-all` and all duplicate service owners.
- Offline Indonesian ASR remains a design decision only. LiveSub's Sherpa model is optional, around 247 MB, and has separate model licensing; no second microphone capture path has been added.
- Main README and releases have not yet been rewritten; main has existing 3D SVG assets, but no verified current in-app screenshots. Do not use a failed CI screenshot as a product screenshot.
- No merge, release, branch deletion, or physical-device test has occurred. Do not clean branches until all intended changes are safely in main and release artifacts have been verified.


## Permission-scope work — 16 November 2026

- Replaced the Settings permission button's single broad `Perms.required()` request with a feature-group picker.
- Added separate groups for microphone, camera, location, contacts, calls, SMS send, SMS read, call history, calendar, Bluetooth, notifications, activity recognition, audio files, and nearby Wi-Fi.
- Already-granted permissions are filtered before requesting.
- Deprecated `Perms.required()` now returns only microphone permission; optional capabilities must use `Perms.forGroup()`.
- Added `PermsTest` for baseline permission, separate SMS grants, label/group parity, and invalid IDs.
- Updated the Settings subtitle and feature-gap matrix.
- Important limitation: this is feature-group selection from Settings, not yet complete just-in-time prompting from each individual feature entry point. The remaining feature entrypoints need wiring before claiming full least-privilege runtime permission UX.
- Latest code commit at this checkpoint: `790bb57598589cc88404fdcc8e7ce8661fbb5521`. Earlier successful build `29cbaab...` predates the permission-group code. Wait for a CI run containing the latest code before calling this update build-verified.


## CI correction after permission test addition

- The first permission unit-test version failed because it imported Robolectric, which is not a dependency in this project, and used non-static JUnit imports.
- Corrected `PermsTest.java` to use the existing JUnit dependency only, static assertion imports, and no Robolectric runner. Tests cover only permission constants, group labels, and pure mapping behavior, so they do not need an Android runtime.
- Latest code commit: `dc1ad0b6aeca29b51a616fc57cf5ba53959492e2`.
- No completed CI run for this latest code commit was visible at the time of this checkpoint. Do not claim the permission-group change is build-verified until a workflow run for this commit or a later commit succeeds.


## Follow-on permission implementation — 16 November 2026

- Confirmed workflow run [43742950118](https://github.com/adebojel/Asisten-bantu/actions/runs/43742950118) succeeded for commit `3d7c66070be155f9edae2c317842c34cfd385cba`: Gradle build, debug APK, and artifact upload all succeeded.
- Audited runtime permission callers. VisionActivity already requests camera only when camera feature opens. Accessibility voice engine checks RECORD_AUDIO and stops when unavailable. CompassLocationHelper previously only returned an error if location was missing.
- Added just-in-time location permission checks to the three CompassActivity actions: current location, save place, and guide to saved place. They request only `Perms.GROUP_LOCATION` and do not access location until user grants it and taps the action again. Denial leaves the feature stopped.
- Extended PermsTest to verify the location group contains only fine/coarse location permissions.
- Added remaining-gap notes: voice-command/background location consumers and other communication features still need entrypoint-level permission UX; Compass currently requires a second tap after grant.
- Latest commit in this continuation segment before the docs checkpoint: `c473fdf13e801a2235bd94ae92882a154e70ef35`; docs checkpoint follows it. Await CI for the latest branch head before calling this segment build-verified.


## Voice-command permission recovery — 16 November 2026

- Added SettingsActivity internal deep link `EXTRA_PERMISSION_GROUP`; it opens a confirmation dialog for the requested feature group, filters already granted permissions, then launches only Android's corresponding runtime prompt.
- Voice commands for answer/end call and send SMS now route missing-permission cases into that exact prompt rather than leaving users to navigate Settings manually.
- Added `GROUP_ANSWER_CALLS` for only `ANSWER_PHONE_CALLS`, and a unit test proving no unrelated phone permission is bundled.
- SMS contact-name lookup remains a separate READ_CONTACTS request path. SMS send requires SEND_SMS only.
- No action is auto-executed after permission grant; users must repeat the voice command, avoiding an unexpected call/message after a system prompt.
- Newest code/test commits in this segment: `106e20dd9a03e83610d71a2003375224bc9a04cd`; docs changes follow. CI must verify the full current head.
