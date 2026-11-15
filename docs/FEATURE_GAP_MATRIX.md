# KOBRA OS capability and integration gap matrix

Date: 2026-11-16

This matrix is based on source inspection of the current `main` tree, not README feature claims. “Present” means a concrete implementation path was found; it does not mean device-level behavior has been validated.

## Existing KOBRA capabilities found

| Area | Current source evidence | Status |
|---|---|---|
| Voice input | `AsistenAccessibilityService` owns one Android `SpeechRecognizer`, Indonesian `id-ID`, on-device-first attempt and system fallback. | Implemented; offline availability depends on Android speech services/model. |
| Voice output | TTS coordination in accessibility/voice profile components. | Implemented. |
| Local AI | Qwen3/LiteRT-LM runtime, serialized model engine and model registry. | Implemented; keep one runtime. |
| Online AI | `OnlineAiRouter` checks configured OpenRouter credentials and live zero-price/model capability catalog. | Implemented; not guaranteed always free/available and requires configuration. |
| Screen access | One `AsistenAccessibilityService`, accessibility tree snapshots, semantic nodes, gestures and voice targeting. | Implemented; provider/app differences remain. |
| Screen agent | New bounded navigation agent on the active CommandRouter route. | Experimental; only low-risk navigation, model/API and device compatibility not field-tested. |
| Camera and vision | `VisionActivity`, CameraX, ML Kit OCR, image labels/object detection, barcode/QR and stability filtering. | Implemented; avoid another camera owner. |
| Navigation | GPS/location helper, compass and navigation activity. | Implemented; real route/obstacle reliability needs device testing. |
| Haptics and sensors | `HapticFeedback`, `SensorManager`, proximity/accelerometer and fall/pocket logic. | Implemented. |
| Contacts | `ContactsContract` phone lookup by name. | Partial: lookup found; full contact CRUD not established. |
| SMS | `SmsManager` send flow with explicit multi-step confirmation. | Partial: sending found; SMS read/search not found. |
| Calls | Answer/end call paths and call-state parser. | Partial: call controls found; call-log query not found. |
| Alarms/timers | `AlarmClock` intents, timer scheduling and receiver. | Implemented for common create flows. |
| Notifications | `KobraNotificationService`, notification read/reply flows. | Implemented; preserve single notification listener. |
| Bluetooth | State receiver and permission declarations found. | Partial: state monitoring exists; direct toggle/tool coverage not established. |
| Wi-Fi | Connectivity/network monitoring and permissions found. | Partial: state/network awareness exists; direct toggle tool not established. |
| Document reading | `DocumentReaderActivity` exists. | Present; audit SAF URI lifecycle and supported formats before expanding. |

## Gaps confirmed by source search

These API integrations were not found in the current Java/Kotlin service paths during this audit:

1. Calendar event query/search/create/update via `CalendarContract` (manifest already declares `READ_CALENDAR`).
2. Call-log read/search via `CallLog.Calls` (manifest already declares `READ_CALL_LOG`).
3. SMS read/search via `Telephony.Sms` (manifest already declares `READ_SMS`).
4. Full contacts create/update/delete; current evidence is phone-number lookup.
5. General file list/search/copy/move/write/delete tool surface; use Storage Access Framework rather than broad storage access where possible.
6. Explicit clipboard read/write tools beyond text entry/paste plumbing.
7. NFC, Do Not Disturb, and dedicated brightness/system-setting tool APIs.
8. A structured, permission-scoped tool registry that local Qwen can call and that can be audited/revoked per capability.

## Permission UX finding

- The current manifest declares sensitive permissions for contacts, SMS, call log, calendar, phone calls, location, camera, microphone, Bluetooth, storage and more.
- `Perms.required()` currently returns many of these together, and `SettingsActivity` requests that combined list.
- This is broader than the least-privilege model used by droid-mcp, where each tool/module declares its own required permissions.
- Next security/UX improvement: request only core microphone permission at onboarding; request camera, location, contacts, SMS, call log, calendar, media and Bluetooth permissions only when the user enables or invokes the corresponding feature. Explain each use and provide a revoke/disable route.
- Do not silently expand permissions just because a repository offers a tool.

## Integration priority

| Priority | Feature | Why | Safety gate |
|---|---|---|---|
| P0 | Permission-scoped capability registry | Prevents the app requesting SMS/call-log/calendar/location permissions all at once. | Per-feature explanation, just-in-time grant, disable and revoke path. |
| P1 | Calendar read/search | Clear gap; useful to a voice-first assistant. | Explicit READ_CALENDAR opt-in; no write permission for read-only mode. |
| P1 | Call-log read and SMS read/search | droid-mcp has mature contracts, but these are highly sensitive. | Separate explicit opt-in; never read aloud in public by default; respect caller/contact privacy. |
| P1 | File/document tools via SAF | Useful for reading documents and user-selected files. | User-picked URIs, no broad file crawl, writes/deletes require confirmation. |
| P2 | Clipboard and app/device context tools | Improve contextual assistance. | Only read clipboard on explicit request; mask secrets and password fields. |
| P2 | DND, NFC, brightness, Wi-Fi/Bluetooth controls | Extend device control. | Android API restrictions and explicit confirmation for state changes. |
| P3 | Optional Indonesian Sherpa ASR | Strong offline speech fallback. | One mic owner; model download consent; verify model license/size and benchmark. |
| P3 | Curated droid-mcp modules | Structured tool contracts and approval model. | No `droid-mcp-all`; no duplicate service/camera/overlay; measure Ktor/Netty APK impact. |

## Important non-goals

- Do not add a second AccessibilityService, camera, microphone, notification listener, TTS owner, or local LLM runtime.
- Do not expose an HTTP MCP server on the phone by default.
- Do not enable root, Shizuku, ADB, or hidden system-control permissions as a normal path.
- Do not let the AI send SMS, make calls, pay, buy, delete, install, share or change system security without a separate explicit confirmation workflow.
- Do not claim any capability is production-verified until it passes tests and physical-device validation.
