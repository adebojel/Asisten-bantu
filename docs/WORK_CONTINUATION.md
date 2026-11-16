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


- Added microphone permission recovery from the accessibility voice service: when microphone permission is missing, KOBRA opens the scoped microphone permission dialog in SettingsActivity once (guarded by the existing one-time warning flag) rather than only telling the user to navigate manually. Voice listening still does not start until Android grants the permission; no command is executed by the permission callback.
- Latest additional code commit: `5dd893aa84cf8754726413547d462acce70e6cd8`. Await CI for this commit or a later code commit.


- Added microphone permission deep link from the accessibility service, plus contact-permission deep links for voice-driven contact lookup/message recipient resolution. The app still does not auto-send a message or place a call after permission grant; the user repeats the command.
- Contact access is requested only when a user asks to resolve a contact name; numeric phone targets do not need READ_CONTACTS.
- Latest code commit: `5ff742a5a0cc5a2aa99fe97d255265520a99e161`. CI for this and subsequent commits must pass before merge.


- Added contact and microphone deep links from the accessibility voice service to the exact permission group picker. No pending call or message is auto-executed after permission grant.
- Split phone permission groups further: CALL_PHONE, ANSWER_PHONE_CALLS, and READ_PHONE_STATE are separate groups; answer/end voice actions request only ANSWER_PHONE_CALLS. Unit tests cover each mapping.
- Latest code/test commits: `5a7a2c6a885bf68446d033d45099e43e10ec4c96`; docs checkpoint follows. The most recent completed success is earlier than these code changes; await CI for this segment.


## Voice SMS-read permission recovery — 16 November 2026

- The voice-command entry point for `baca SMS`, `baca pesan singkat`, and `SMS terbaru` now checks `READ_SMS` before invoking `MessageReader.latestSms()`.
- If missing, it opens `SettingsActivity` with `GROUP_SMS_READ`; the scoped explanation and Android runtime prompt are shown before any inbox query.
- Granting permission does not resume the read automatically. The user repeats the voice command, preventing unexpected disclosure immediately after a permission prompt.
- Without `READ_CONTACTS`, sender names remain unresolved and the reader reports the sender number.
- This is a source-level change only; await CI for the new head. Device behavior remains unverified and device testing stays deferred.

## Voice step-counter permission recovery — 16 November 2026

- Voice commands for daily steps now request only `GROUP_ACTIVITY` (`ACTIVITY_RECOGNITION`) on Android 10+ when permission is missing.
- The step query stops before sensor registration and opens the feature-specific permission flow; the user repeats the command after granting.
- Added `PermsTest` coverage for the activity-recognition mapping on Android 10+ and documented that this does not gate accelerometer-based fall/pocket sensing.
- Await CI for the new source/test commits before calling this change build-verified; device testing remains deferred.

## Voice local-audio permission recovery — 16 November 2026

- The `putar musik lokal` voice command now checks the Android-version-specific media permission before calling the MediaStore audio query.
- Missing access opens only `GROUP_MEDIA`; playback is not resumed after the grant, so the user repeats the command.
- Android 13+ uses `READ_MEDIA_AUDIO`; older supported versions use `READ_EXTERNAL_STORAGE`. Android document-picker access continues to use the Storage Access Framework separately.
- Await CI for the latest source and documentation commits; no device test has been performed.

## Open-source research wave and permission work — 16 November 2026

- Latest confirmed CI success before this research wave: `bf0fa0b0581833279f2618e7020a7bc7da2c44bc`, workflow [43801860862](https://github.com/adebojel/Asisten-bantu/actions/runs/43801860862). This run includes the local-audio source change and the earlier SMS/step permission work.
- Added just-in-time `READ_SMS` gating to the voice SMS reader; no inbox query happens before the permission grant and the user must repeat the command.
- Added just-in-time `ACTIVITY_RECOGNITION` gating for step-count voice commands on Android 10+; sensor registration is blocked until permission is granted. Added pure permission mapping test.
- Added just-in-time media permission gating to the local-audio voice command; it checks `READ_MEDIA_AUDIO` on Android 13+ or `READ_EXTERNAL_STORAGE` on earlier versions before querying MediaStore.
- Expanded `docs/OPEN_SOURCE_REPOSITORY_CATALOG_2026-11.md` with a new research wave: Knotwork, MobileAgent, Atlas Sight, SightBuddy, Sailens, LocalChat, OfflineVoiceAI, CueMate, SENSEA, PhoneClaw and follow-up priorities.
- Research decision: Knotwork is a strong agent workflow candidate but requires license, Android 14+/RAM and tool-boundary review; Sailens is Apache-2.0 but pre-release/BYO-model/high-end target; Atlas Sight claims MIT but root/model license and Android maturity still need verification; OfflineVoiceAI and several other candidates have unclear licensing and remain reference-only.
- No third-party runtime dependency or source code from these newly researched repositories has been imported in this wave. Avoid duplicate AccessibilityService, CameraX, microphone and LiteRT-LM owners.
- Current PR head when this note is authored: `ae17cf7f4e76eb8130cce07768551dcdcc887382`. CI for this head must be checked after the documentation commit; do not assume it passed.
- PR #16 remains open; do not merge until latest head CI, code review, README/release accuracy, and pre-test gates are complete. Device testing remains deferred until implementation is ready and user authorizes the test phase.

### Next work queue

1. Check CI for `ae17cf7...`; if it fails, inspect job logs and fix before further feature work.
2. Audit permission entry points for notification access, calendar, Bluetooth, phone state/call history, nearby Wi-Fi and all media access; remove unused manifest declarations or gate them at the actual feature entry point.
3. Deep-audit Knotwork and MobileAgent source/license/dependencies for reusable isolated workflow and task-verification patterns; preserve KOBRA's single bounded agent and service.
4. Deep-audit Sailens/Atlas Sight/SightBuddy perception code and model licenses; prioritize one measurable offline vision or navigation improvement rather than adding duplicate pipelines.
5. Define and benchmark Indonesian offline ASR/TTS adapter with Sherpa-ONNX/Vosk candidates while preserving one microphone owner.
6. Before approaching device testing, finish memory/lifecycle/error/cancellation audits, regression tests, permissions, privacy docs, and CI.
7. Only near the test-ready gate: comprehensively refresh README with existing 3D artwork, genuine verified screenshots only, architecture and feature status, privacy/permissions, model sizes/licenses, install instructions, latest APK/release/checksum links. Ensure release assets are actually uploaded before linking.
8. Merge reviewed work into `main` only after user-requested consolidation stage and all required CI gates pass. Verify main build and release APK assets/checksum. Delete obsolete branches only after confirming all unique work is safely on main; preserve main and any required release branch. Do not claim cleanup before verifying branch list.

## Security and manifest audit — 16 November 2026

- Reviewed the active branch manifest, network clients, FileProvider paths, Gradle configuration, and accessibility-service metadata.
- Network endpoints inspected in `AiClient`, `AppUpdater`, and `OnlineAiRouter` use HTTPS. The Qwen model build download is also pinned to an HTTPS Hugging Face URL (with an environment override that must remain HTTPS-only).
- Hardened `AndroidManifest.xml` by changing `android:usesCleartextTraffic` from `true` to `false`. This prevents accidental plaintext HTTP traffic by app components and SDKs; any future local development endpoint must use a separately reviewed, narrowly scoped debug configuration rather than reopening cleartext globally.
- FileProvider paths are scoped to app external Downloads, internal files, and app cache; provider is non-exported and grants URI access. Keep APK install flow on content URIs with temporary read grants.
- Accessibility service is deliberately capable of retrieving window content, screenshots, gestures, and filtered key events because these are core assistive features. Treat this as a high-trust capability: keep one service owner, user-enabled consent, bounded actions, and no hidden background interaction.
- Remaining manifest audit: verify every declared permission and special app access against actual call sites and feature UX, especially `REQUEST_INSTALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, exact alarms, notification listener, boot receiver, phone state/call history, calendar, Bluetooth, nearby Wi-Fi, and package visibility. Do not remove a permission until the owning feature and user-facing fallback are understood.
- Current-head CI: workflow [43803302486](https://github.com/adebojel/Asisten-bantu/actions/runs/43803302486) is running for head `21bd8206038adecaa8dfeb9e1c830efcbc934a84`; pure logic tests and Gradle setup passed, Debug APK build is in progress. Do not call the hardened manifest build-verified until this or a later run completes successfully.
- Manifest hardening commit: `972f3d44dcbbafd814aac442f6763ae3a993b3fe`. A new CI run for the updated head must be checked.


## Follow-up hardening — 16 November 2026

- The Gradle model-download task previously accepted `KOBRA_QWEN_MODEL_URL` without validating its scheme. Although the default URL is HTTPS, an overridden URL could have been plain HTTP.
- Added a fail-closed HTTPS requirement for the configured model URL and a second HTTPS check after opening the connection to reject a redirect that downgrades transport security.
- This protects the build-time Qwen model supply path from accidental plaintext transport. It does not by itself authenticate model provenance or guarantee a pinned content digest; a SHA-256 allowlist and upstream artifact/version verification remain recommended before release.
- Change commit: `6850f9cff2790c5de0545c6978932dc2ea518d30`. CI for this commit must complete before the change is treated as build-verified.
- Permission audit remains in progress. In particular, the manifest declares nearby Wi-Fi, Bluetooth advertise, phone state, call history, calendar, exact alarm, overlay, notification listener, battery-optimization exemption, package installation, broad package visibility, and foreground-service special use. Each requires a verified owning feature and Android-version-specific UX; no permission is removed merely to make the manifest shorter.
- The widget provider is exported for the Android AppWidget broadcast contract and also declares a custom voice action. Its external-broadcast trust boundary needs a dedicated review before changing export behavior; avoid breaking launcher widget updates.


## Widget broadcast security fix — 16 November 2026

- Found a trust-boundary issue: the exported `KobraWidgetProvider` accepted the app's custom `ACTION_WIDGET_VOICE` broadcast. Because the receiver must remain exported for Android AppWidget framework delivery, another app could explicitly target that exported component and attempt to trigger voice listening.
- Removed the custom voice action from the exported receiver's intent filter and removed its custom `onReceive` branch.
- Widget voice taps now use an immutable explicit `PendingIntent.getActivity()` to `WidgetVoiceActivity`, a new `android:exported="false"` activity. Only the app's own widget PendingIntent can invoke the private trampoline; it preserves the previous behavior (request one voice listen when accessibility service is active, otherwise launch setup/main with `auto_listen=true`).
- The provider remains exported solely for the Android AppWidget contract. Vision widget action remains an explicit PendingIntent to the non-exported `VisionActivity`.
- Source changes: `WidgetVoiceActivity.java`, `KobraWidgetProvider.kt`, and `AndroidManifest.xml`. CI must pass before this fix is considered build-verified; widget interaction still requires later device validation.


## Camera callback lifecycle hardening — 16 November 2026

- Audited `VisionActivity` CameraX live analysis and one-shot capture paths. Existing analyzer uses `STRATEGY_KEEP_ONLY_LATEST` and closes frames on the ordinary OCR/barcode/object-labeling completion paths.
- Added a volatile teardown flag. Camera provider callbacks now refuse to bind after the Activity has been destroyed/finished, and incoming analysis frames are immediately closed when teardown has begun.
- Prevented the live companion's asynchronous OCR completion from recreating an object detector after Activity teardown; its retained `ImageProxy` is closed instead.
- One-shot capture now closes `ImageProxy` in a `finally` block and discards/recycles its bitmap if the Activity is no longer active before UI delivery.
- The camera executor now uses `shutdownNow()` during Activity destruction to cancel queued work. Runtime behavior, CameraX lifecycle unbinding, and device-specific camera release still require instrumentation/device validation; no physical-device test is claimed.
