# Decision record: offline Indonesian ASR for KOBRA OS

Date: 2026-11-16

## Source audited

- Upstream: https://github.com/R-Dilipkumar/LiveSub-Offline-Translator
- Upstream code license: MIT.
- Speech runtime: Sherpa-ONNX (Apache-2.0).
- README lists a streaming multilingual Zipformer model covering Japanese, Chinese, English, Russian, Arabic, Thai, Vietnamese, and **Indonesian**.
- The upstream README states the model is about 247 MB to download; this is a substantial optional download, not a small built-in asset.
- The upstream project explicitly separates code licensing from model licensing. Each model keeps its own license; KOBRA must preserve the model's license/attribution and verify model provenance before shipping/downloading it.
- Upstream app minSdk is 29; KOBRA currently supports minSdk 28. Copying the app configuration or whole app is not appropriate.

## What was inspected

- `ModelManager.kt`: model catalogue, download/extract, atomic install, selection, storage management.
- `SpeechRecognizerEngine.kt`: Sherpa streaming engine, ordered Partial/Final events, model init serialization, audio feed API, recognizer release and capture-session guards.
- `SpeechValidator.kt`: filtering of engine status messages from actual speech.
- `ModelSelectionTest.kt`: model selection, stale folder cleanup, no silent fallback to another model, deletion behavior.
- `app/build.gradle.kts`: Sherpa dependency and NDK ABI requirements.

The key integration boundary is `SpeechRecognizerEngine.feedAudioSamples(FloatArray)`. Upstream does not itself own the microphone in that engine; a capture service feeds PCM samples into it. That is the correct architecture to study.

## KOBRA compatibility finding

KOBRA's `AsistenAccessibilityService` owns the single Android `SpeechRecognizer` instance configured for Indonesian `id-ID`. The current implementation is now on-device-only: it creates the recognizer with `createOnDeviceSpeechRecognizer`, explicitly sets `EXTRA_PREFER_OFFLINE=true`, and never falls back to the generic system recognizer. If Android does not expose an on-device recognizer or the Indonesian offline language pack is missing, KOBRA pauses voice recognition and explains the requirement; microphone audio is not redirected to a network recognizer. KOBRA also has a central TTS/mic state and other speech command paths.

Therefore, adding Sherpa by starting a second `AudioRecord`/microphone listener beside the current SpeechRecognizer would create competing audio ownership, duplicate listening, battery use, and ambiguous command dispatch. Do not do that.

## Recommended implementation sequence

1. Create a small ASR backend interface with explicit states: Unavailable, DownloadRequired, Initializing, Ready, Listening, Error.
2. Keep one central microphone capture owner in KOBRA. Do not let the Sherpa engine open the microphone itself.
3. Refactor capture only after mapping every current mic entrypoint, TTS interruption, lifecycle callback, permissions path, and foreground-service behavior.
4. Feed 16 kHz mono PCM into Sherpa through one serialized channel; preserve backpressure and cancel cleanly.
5. Convert Sherpa Partial/Final events into KOBRA's existing command parser. Only final, validated utterances should dispatch actions by default; partials may be shown/narrated as drafts but must not trigger phone actions.
6. Do not use the generic Android SpeechRecognizer as a fallback: it may use a network service. Keep the current Android on-device recognizer as a temporary local backend only. When Sherpa is integrated, switch backends explicitly and never run two microphone/ASR backends simultaneously.
7. Provide explicit model download consent, Wi-Fi-only option, available storage/RAM estimate, pause/cancel, checksum validation, and model-license disclosure.
8. Benchmark Indonesian word error rate, latency, CPU, RAM, battery and thermal behavior on low/mid-range devices before making it default.
9. Add tests for model absent, corrupted model, download interrupted, language mismatch, duplicate start, stop during inference, TTS speaking, permission revoked, partial false positive, and backend switching.
10. Retain KOBRA's minSdk 28 unless a measured library requirement proves otherwise. If Sherpa artifact has a native ABI/API constraint, gate backend availability by device/API rather than raising minSdk without approval.

## Decision

**Do not copy the LiveSub app or merge its full ASR implementation yet.** Keep it as a high-priority upstream reference. The on-device-only Android recognizer policy is enforced in `AsistenAccessibilityService`; a missing offline language pack now disables recognition with an explicit message rather than using network speech recognition. Integrate only the Sherpa runtime/model manager and selected tests after the central KOBRA audio capture owner has a defined PCM stream contract and CI/device benchmarks are available. This is a deliberate safety/architecture gate, not a rejection of offline Indonesian ASR.
