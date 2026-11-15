# Deep Audit & Integration Matrix — KOBRA OS (15 Nov 2026)

Target repository: https://github.com/adebojel/Asisten-bantu  
Target branch: `main`  
Research branch: `research/open-source-expansion-2026-11-15`

This is a source-level integration plan grounded in the current KOBRA tree and inspected upstream repository files/README/license files. It is not a claim that upstream code has already been copied, that model weights are redistributable, or that device-level safety has been validated.

## 1. Current KOBRA capabilities confirmed in source tree

The current main branch already contains substantial implementations. Avoid duplicating these subsystems:

| Existing KOBRA source | Existing responsibility visible in source | Integration implication |
|---|---|---|
| `AsistenAccessibilityService.java` | Single Android AccessibilityService; voice recognition, device actions, notifications, screen control and service lifecycle | Keep as the only accessibility service. Do not import another service from an upstream app. |
| `UnifiedAccessibilityEngine.kt` | Screen snapshot, accessibility tree, semantic elements, gestures and TTS coordination | Extend its contracts; do not add a second screen-control driver. |
| `voiceaccess/VoiceAccessEngine.kt`, `NodeIndexer.kt` | Voice-based screen targeting and node indexing | Compare target ranking and ambiguity logic with MantiClaw/JARVIS; do not duplicate overlays. |
| `CommandRouter.kt` | Four-tier fast/local/online command routing and bounded agent steps | Add structured plans/results and verification in this route, not a second agent entrypoint. |
| `VisionActivity.java`, `SafeObjectDetector.java`, `ObjectDetectionStability.java` | CameraX, OCR, barcode, ML Kit image labels, object detection and stability filtering | Use one CameraX lifecycle and extend perception output with measured evidence. |
| `LiteRtLmEngine.kt`, `LocalLlmEngine.java`, `LocalModelRuntimeManager.kt`, `LocalModelRegistry.java` | Local LiteRT-LM model lifecycle and runtime | Keep Qwen3/LiteRT baseline; benchmark any additional model/runtime before adoption. |
| `OnlineAiRouter.kt` | Online provider routing and offline-aware failure | Improve provider health/availability semantics without claiming free service availability. |
| `LocalMemoryStore.kt`, `OfflineKnowledgeStore.kt`, `UnifiedConversationHistory.java` | Local memory, knowledge and conversation history | Extend existing storage and deletion policies; avoid a second memory database. |
| `NavigationActivity.java`, `CompassActivity.java`, `CompassLocationHelper.java`, `HapticFeedback.java` | Existing navigation/location/compass/haptic foundations | Compare SIGHT/Cogniscape/Sailens for safe incremental improvements; don't claim obstacle clearance from bounding-box size alone. |

## 2. New repositories examined in this pass

| Repository | Verified repository license | Useful systems / source direction | KOBRA decision |
|---|---|---|---|
| [MohammadSaifAnsari/MantiClaw-Agent](https://github.com/MohammadSaifAnsari/MantiClaw-Agent) | MIT text present in LICENSE (GitHub metadata says NOASSERTION) | Kotlin agent state machine, planner, executor, success monitor, stuck detection, structured reason codes, skills, app cards, memory/RAG, WorkManager triggers, credential vault, PII masking, audit/export/delete | **Highest-priority source audit.** Selectively port small, isolated logic only after file-by-file dependency and license review. Do not import its whole Compose/Hilt app. |
| [fainir/handy-ai](https://github.com/fainir/handy-ai) | MIT | Voice-first Android agent, screen actions narrated step-by-step, easy mode, task stop, blind/low-vision/motor access | Study voice-first task UX, cancellation and narration. Provider uses Claude and may incur usage cost; not a free offline brain. |
| [youssef061204/SIGHT](https://github.com/youssef061204/SIGHT) | Apache-2.0 | Walking routes, fresh GPS route progress, object finding, image-relative direction, OCR reading order, encrypted landmarks, on-device voice, road-segmentation evaluation and explicit limits | **High-priority validation reference.** Reuse only after evaluating model/data licenses and Android integration. Its README explicitly documents false-positive thresholds and limits. |
| [rinkusharma79346-droid/V.A.Y.U-Agent](https://github.com/rinkusharma79346-droid/V.A.Y.U-Agent) | Apache-2.0 | ReAct screen agent, vision-grounded taps, stuck detection, multi-provider models, MCP remote control and dashboard | Study tool schemas and MCP boundary. Remote control must be opt-in, authenticated, visible, scoped and revocable. |
| [beingDurgesh/OnScreen-Agent](https://github.com/beingDurgesh/OnScreen-Agent) | MIT | Kotlin/Rust agent, multimodal screen loop, STT/VLM/TTS provider slots, stop-first controls | Architecture reference. README says Android 15+ and experimental; KOBRA minSdk 28, so do not copy platform-specific code wholesale. |
| [DivyanshParashar1/drishti](https://github.com/DivyanshParashar1/drishti) | AGPL-3.0 | Voice-first hazard announcements, YOLO tracking, people recognition with consent, OCR reading order, speech queue priorities, camera obstruction/low-light warnings, 11 Indian languages | Feature and test reference only unless KOBRA owners explicitly accept AGPL obligations. Cloud speech depends on Gnani services. |
| [SidhaarthShree07/IKSHA](https://github.com/SidhaarthShree07/IKSHA) | GPLv3 stated by project; verify exact LICENSE and all model terms before reuse | Local Gemma, memory-adaptive model sizing, offline STT/TTS, face continuity, skills and documents | **Architecture reference only** unless copyleft compatibility is intentionally accepted. Its face recognition introduces consent/privacy obligations. |
| [fff122/EchoSight](https://github.com/fff122/EchoSight) | GPL-3.0 | Voice find-object, YOLO-World/ONNX, OCR fallback, reference photos, camera-relative location, optional cloud vision proxy | Reference only unless GPL obligations are accepted. Do not adopt its rough distance claims without calibration evidence. |
| [manav-Mnv/EchoNav](https://github.com/manav-Mnv/EchoNav) | No LICENSE file found | EfficientDet Lite0, Explore/Focus/Silent modes, haptic urgency, direction from bounding boxes, cooldown/deduplication | Feature reference only. Do not copy code without a license. Apparent distance estimates from box area are not reliable metric distance. |
| [Digitalbrainstem/atlas-sight](https://github.com/Digitalbrainstem/atlas-sight) | MIT stated in README; verify root LICENSE and per-model licenses | Offline voice-first modes, scene VLM, OCR, object detection, priority TTS, history-aware descriptions, deduplication, emergency and navigation modes | Strong reference for low-memory/offline UX and scene deduplication. Verify Android implementation maturity, model provenance and actual device benchmarks. |
| [Subhashnaidudulla/Cogniscape-Android](https://github.com/Subhashnaidudulla/Cogniscape-Android) | No reuse license confirmed; README says copyright | Pedestrian routing, compass, spatial audio, haptics, POI, geofence and offline route concepts | Architecture/features only until explicit license and real implementation status are confirmed. README labels some ML features planned. |
| [wnbotoo/sailens-android](https://github.com/wnbotoo/sailens-android) | Apache-2.0 | Camera-fed scene perception, walkable area/road-edge concepts, speech and haptics, model-neutral Android host | High-value research for mobility perception; pre-release, targets flagship Snapdragon hardware, no model weights included. Never describe as safety-certified. |
| [andrachiritoiu/Accessnav-hybrid](https://github.com/andrachiritoiu/Accessnav-hybrid) | License not verified in this audit | Offline OCR/haptics, wearable haptics, BLE indoor beacons, edge RTOS scheduling, voice-first navigation | Architecture reference pending license review. BLE beacon infrastructure is additional hardware, not a software-only feature. |
| [Kashan-Zahid/Blind-AI-Assistant](https://github.com/Kashan-Zahid/Blind-AI-Assistant) | No license confirmed | Gemini vision, OCR/currency, device voice actions, calls, WhatsApp and notification reading | Feature reference only; no code reuse until licensed. Cloud Gemini is not offline. |

## 3. Source-level decisions: what to adapt, what not to copy

### Agent core — adapt first
Source references: MantiClaw, JARVIS Android Agent, Handy AI, V.A.Y.U.

Implement inside existing `CommandRouter` + `UnifiedAccessibilityEngine`:
- `AgentTask`: immutable task ID, user goal, createdAt, current step, bounded step budget.
- `AgentStep`: explicit action type, target descriptor, arguments, risk level, deadline.
- `ActionResult`: SUCCESS, FAILED, PARTIAL, BLOCKED, NEEDS_CONFIRMATION, STALE_TARGET, UNVERIFIED, UNSUPPORTED.
- `ScreenObservation`: timestamp, package/window, fingerprint, compact node list and optional screenshot reference.
- `SuccessVerifier`: observe after every action; verify target state change; do not infer success from a tap callback.
- `StuckDetector`: detect repeated fingerprints/no progress and stop or replan within a strict retry budget.
- `TaskJournal`: persist only the minimum resumable state; encrypt sensitive values; do not store raw screen content by default.
- Global cancellation must stop the agent loop, pending gesture, speech output, screenshot and network request.

Do not import another AccessibilityService, floating overlay manager, audio listener, camera owner, or foreground service.

### Vision and assistive awareness — extend, don't overclaim
Source references: SIGHT, SightBuddy, Atlas Sight, Drishti, Sailens, EchoNav.
- Add temporal tracking only where detections include stable IDs or a defensible matching algorithm.
- Keep image-relative left/center/right distinct from real-world compass direction.
- A bounding box does not measure distance. Metric range requires calibrated depth/stereo/ToF or validated model + device calibration.
- Road segmentation requires a held-out dataset, per-class IoU, false-positive rate and on-device evaluation before any spoken hazard claim.
- Camera blocked, low light, stale frame, permission loss and inference overload must produce explicit spoken degraded-state warnings.
- CameraX ImageProxy/Bitmap/model resources must be released on every success, failure, cancellation and lifecycle path.

### Voice — one audio owner
Source references: sherpa-onnx, IKSHA, Drishti, SightBuddy.
- Put STT, VAD, wake-word, TTS and speaker interruption behind one KOBRA audio coordinator.
- Evaluate Indonesian model availability and accuracy before bundling; many upstream demos are English, Hindi or other language-specific.
- Priority queue: emergency > confirmation > navigation > requested reading > conversational narration.
- Barge-in must cancel current TTS before reopening the microphone; never open competing recognizers.
- Display whether voice runs locally or through a provider. Do not call a cloud recognizer "offline".

### Navigation — conservative staged adoption
Source references: SIGHT, Cogniscape, Sailens, Accessnav.
- First improve GPS freshness, compass calibration, reroute state, route-progress announcements, saved landmarks and haptic semantics.
- Offline maps require licensed/attributed tiles and locally stored routing graphs; map data licensing is separate from engine license.
- BLE beacons require installed beacon hardware and cannot be promised as available everywhere.
- Never instruct a user that a road crossing or path is safe based only on camera classification.

### Memory, model and privacy
Source references: MantiClaw, IKSHA, Pocket LLM, Atlas Sight.
- Keep existing KOBRA LiteRT-LM/Qwen runtime as baseline.
- Memory categories: conversation, user-approved preference, temporary task context, saved landmark, sensitive data. Each category needs retention and delete rules.
- Model manager: manifest, exact version, source URL, license, SHA-256, expected size, ABI/runtime compatibility, atomic download, cancellation, corruption recovery and user-visible storage use.
- Credentials should use Android Keystore-backed encryption; never commit API keys or write them to logs.
- Cloud providers must be opt-in and expose provider/model/status. "Free" is a volatile provider policy, not a guaranteed entitlement.

## 4. Priority backlog against the current KOBRA source

| Priority | Change | Existing integration point | Acceptance evidence |
|---|---|---|---|
| P0 | Structured action outcomes and post-action verification | `CommandRouter.kt`, `UnifiedAccessibilityEngine.kt` | Unit tests for success/failure/stale/ambiguous/unsupported; instrumentation test with real UI target |
| P0 | Snapshot freshness and target re-resolution before every tap | `UnifiedAccessibilityEngine.kt`, `voiceaccess/NodeIndexer.kt` | Target disappears/reorders between observe and act → no tap; explicit stale-target result |
| P0 | Task cancellation and hard step/time budgets | `CommandRouter.kt`, `AsistenAccessibilityService.java` | Stop cancels loop, network call, queued gesture and speech; no post-cancel action |
| P0 | Risk classifier and explicit confirmation | `CommandRouter.kt`, `AsistenAccessibilityService.java` | Messages/calls/payment/delete/share require separate confirmation; negative/timeout cancels |
| P1 | Indonesian offline ASR/VAD/TTS benchmark and adapter | Existing speech owner in `AsistenAccessibilityService.java`, `Speaker.java` | Accuracy, latency, RAM, battery and interruption report on target phone |
| P1 | Object tracking and stale/low-light/covered-camera warnings | `VisionActivity.java`, `SafeObjectDetector.java` | Frame lifecycle tests, temporal tracking tests, degraded-state tests |
| P1 | Scene narration deduplication and priority queue | `Speaker.java`, `ObjectDetectionStability.java` | No repetitive announcements; emergency interrupts reading |
| P2 | Offline walking graph and map cache | `NavigationActivity.java`, `CompassLocationHelper.java` | Reproducible offline route tests, attribution and map update policy |
| P2 | Local RAG/document citations and memory deletion | `LocalMemoryStore.kt`, `OfflineKnowledgeStore.kt`, `DocumentReaderActivity.java` | Query returns source/page; deletion removes content and index |
| P2 | Optional MCP bridge with explicit pairing and scopes | New isolated module; no default background listener | Authentication, allowlist, revoke, audit, no internet exposure by default |

## 5. Why no upstream runtime code was blindly copied in this commit

The KOBRA project is already a native Android Java/Kotlin application (minSdk 28, targetSdk 36) with an existing accessibility service, CameraX, ML Kit, LiteRT-LM, Qwen3 model pipeline, voice router, memory and navigation. Many candidates use Compose/Hilt, React Native/Expo, Python/FastAPI, Rust/JNI, proprietary cloud APIs, AGPL/GPL, or different Android API targets.

Blindly copying them would create duplicate services and resource owners, incompatible dependencies, license obligations, and potentially unsafe claims. This audit therefore adds a precise source-level integration matrix first. The next code change should be one isolated P0 feature with tests, not a large multi-repository code dump.

## 6. Repository links
- https://github.com/MohammadSaifAnsari/MantiClaw-Agent
- https://github.com/fainir/handy-ai
- https://github.com/youssef061204/SIGHT
- https://github.com/rinkusharma79346-droid/V.A.Y.U-Agent
- https://github.com/beingDurgesh/OnScreen-Agent
- https://github.com/DivyanshParashar1/drishti
- https://github.com/SidhaarthShree07/IKSHA
- https://github.com/fff122/EchoSight
- https://github.com/manav-Mnv/EchoNav
- https://github.com/Digitalbrainstem/atlas-sight
- https://github.com/Subhashnaidudulla/Cogniscape-Android
- https://github.com/wnbotoo/sailens-android
- https://github.com/andrachiritoiu/Accessnav-hybrid
- https://github.com/Kashan-Zahid/Blind-AI-Assistant


## 7. Critical implementation gaps found by reading KOBRA source (not just README)

These findings are based on the current `main` source inspected during this audit:

1. **The screen-agent execution path is not wired into the active command route.** `runMultiStepAgentAsync`, `analyzeScreenVisuallyAsync`, and `shouldUseScreenAgent` appear as private helpers but no call site was found in `CommandRouter.kt`. The user-facing multi-step automation therefore must not be advertised as fully operational based only on those methods existing.
2. **The legacy screen-agent provider stub always returns null.** `callGeminiRest(...)` in `CommandRouter.kt` returns null unconditionally, and `callGeminiBlocking(...)` is also explicitly disabled. Any path that relies on these helpers cannot get an AI decision from that implementation.
3. **A better provider path already exists but needs deliberate wiring and tests.** `OnlineAiRouter.askBlocking(context, userPrompt, systemPrompt, imageBase64)` has a path for OpenRouter image requests and discovers vision models only when the live catalog reports both prompt and completion prices as zero and image input support. It requires a configured OpenRouter key and a successful live provider catalog; it is not guaranteed to be available or free forever. Do not silently fall back to a paid Gemini API.
4. **The current safe object detector does not provide geometry.** `SafeObjectDetector.DetectedItem.boundingBox` is nullable and the current ML Kit image-labeling fallback constructs each item with `null` bounds. The class comment explicitly says image labeling does not provide bounding boxes. Therefore the current fallback cannot truthfully produce left/right location or metric distance from those labels.
5. **The camera activity owns multiple detector references.** `VisionActivity.java` declares stream and single-shot detector clients. Confirm both are closed on all lifecycle/error/cancel paths and avoid concurrent duplicate inference before adding a second detector model.
6. **Existing local model runtime is already serialized.** `LiteRtLmEngine.kt` owns one executor and serializes engine/conversation operations. A second LLM runtime must not be added without a measured need and explicit memory arbitration.

### Required next code change (P0)

- Wire one bounded screen-agent entrypoint from the active `CommandRouter` decision path.
- Use `OnlineAiRouter.askBlocking(..., imageBase64)` only when a configured key exists and the live catalog confirms an image-capable zero-price model; otherwise return a clear `UNAVAILABLE` result and use the existing local Qwen answer path (do not invent a local tool decision).
- After every action, capture a fresh snapshot and compare it with the pre-action snapshot. A successful Android API return is not proof the intended UI state changed.
- If the action result cannot be verified, report `UNVERIFIED` rather than claiming completion and do not cache the sequence as a known-good macro.
- Disable or reject risky actions in the autonomous loop until a separate confirmation gate is implemented for message sending, calls, payments, deletion, purchases and data sharing.
- Add tests for provider unavailable, no model found, malformed JSON, stale screen, action API false, unchanged screen, changed screen and cancellation.

This gap analysis takes precedence over aspirational feature lists in README files. Build success alone does not prove the screen agent or vision geometry works on a physical phone.
