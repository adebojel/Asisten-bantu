# KOBRA OS capability and integration gap matrix

Date: 2026-11-18

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
| Navigation | `PedestrianMapActivity` uses MapLibre offline regions and BRouter with local OSM RD5 segments, SHA-256 cache checks, and Indonesian turn instructions; GPS/compass helpers remain separate. | Partial: map and route calculation can work offline after area/segments are downloaded. Off-route detection and automatic rerouting are implemented with accuracy thresholds, consecutive-fix confirmation, cooldown, and unit tests. Indoor navigation, sidewalk/crossing quality, and field reliability are not verified. |
| Haptics and sensors | `HapticFeedback`, `SensorManager`, proximity/accelerometer and fall/pocket logic. | Implemented. |
| Contacts | `ContactsContract` phone lookup by name. | Partial: lookup found; full contact CRUD not established. |
| SMS | `SmsManager` send flow with explicit multi-step confirmation. | Partial: sending found; SMS database read/search not found in audited paths. |
| Calls | Answer/end call paths and call-state parser. | Partial: call controls found; call-log query not found in audited paths. |
| Calendar | `ProductivityManager.readTodayAgenda()` queries `CalendarContract.Instances` for today's agenda. | Partial: read-today agenda exists; arbitrary date-range search and create/update/delete not found. |
| Alarms/timers | `AlarmClock` intents, timer scheduling and receiver. | Implemented for common create flows. |
| Notifications | `KobraNotificationService`, notification read/reply flows. | Implemented; preserve single notification listener. |
| Bluetooth | State receiver and permission declarations found. | Partial: state monitoring exists; direct toggle/tool coverage not established. |
| Wi-Fi | Connectivity/network monitoring and permissions found. | Partial: state/network awareness exists; direct toggle tool not established. |
| Document reading | `DocumentReaderActivity` uses Android `OpenDocument` picker for TXT/PDF, then text parsing/PDF rendering/OCR. | Present for user-selected documents; general file search/list/write/delete tools are not established. |

## Gaps confirmed by source search

These API integrations were not found in the current Java/Kotlin service paths during this audit:

1. Calendar create/update/delete and arbitrary date-range search; today's agenda read via `ProductivityManager.readTodayAgenda()` already exists.
2. Call-log read/search via `CallLog.Calls` (manifest declares `READ_CALL_LOG`, but a query path was not found in the audited source).
3. SMS database read/search via `Telephony.Sms` (manifest declares `READ_SMS`; SMS sending already exists with confirmation).
4. Full contacts create/update/delete; current evidence is phone-number lookup.
5. General file list/search/copy/move/write/delete tool surface; selected TXT/PDF reading through SAF already exists.
6. Explicit clipboard read/write tools beyond text-entry/paste plumbing.
7. NFC, Do Not Disturb, and dedicated brightness/system-setting tool APIs.
8. A structured, permission-scoped tool registry that local Qwen can call and that can be audited/revoked per capability.

## Permission UX finding

- `Perms.required()` returns only the microphone baseline. `SettingsActivity` uses a feature-group picker and requests only the group the user selected; it no longer requests all sensitive permissions together.
- Just-in-time checks are present at audited entry points for camera, location, contacts, calls, SMS send/read, calendar, activity recognition, and local audio. Voice location lookup, saving favorites and saved-place guidance now open the location permission flow when needed. GPS trip-distance monitoring requires precise location; approximate location remains sufficient for basic position lookup.
- After a permission grant, the user repeats the original command. KOBRA does not automatically execute a pending call, message, location action, or other sensitive operation merely because permission was granted.
- Bluetooth Connect and Android notification posting are separate user-selected runtime groups. Notification-listener access is a separate Android special-access setting and must not be confused with `POST_NOTIFICATIONS`.
- `READ_CALENDAR` is used by today's agenda reader. `READ_CONTACTS`, `SEND_SMS`, and `READ_SMS` are used by scoped flows. Call-log reading and nearby Wi-Fi discovery are not implemented; their permission groups request nothing. Unused `READ_CALL_LOG`, `NEARBY_WIFI_DEVICES`, and `CHANGE_WIFI_STATE` declarations have been removed.
- Remaining permission audit: verify every special access and manifest declaration against its exact entry point and Android-version-specific UX, especially overlay, exact alarms, battery-optimization exemption, package installation, notification listener, foreground-service types, and boot behavior.
- Do not silently expand permissions because a repository offers a tool. Keep the explanation, explicit opt-in, denial path and revoke/disable route.

## Integration priority

| Priority | Feature | Why | Safety gate |
|---|---|---|---|
| P0 | Finish permission-entrypoint and special-access audit | The feature-group picker and many just-in-time gates exist; remaining call sites and special access still need full coverage. | Per-feature explanation, just-in-time grant, denial handling, disable and revoke path. |
| P1 | Calendar create/update and date-range search | Today's agenda read already exists; expand the current CalendarContract implementation. | READ_CALENDAR for read; WRITE_CALENDAR only when a user enables write actions; confirm changes. |
| P1 | Call-log read and SMS read/search | droid-mcp has mature contracts, but these are highly sensitive. | Separate explicit opt-in; remove unused permissions until built; never read aloud in public by default. |
| P1 | File tools via SAF | TXT/PDF reading already exists; add selected-file search/metadata if needed. | User-picked URIs, no broad file crawl, writes/deletes require confirmation. |
| P2 | Clipboard and app/device context tools | Improve contextual assistance. | Only read clipboard on explicit request; mask secrets and password fields. |
| P2 | DND, NFC, brightness, Wi-Fi/Bluetooth controls | Extend device control. | Android API restrictions and explicit confirmation for state changes. |
| P3 | Optional Indonesian Sherpa ASR/TTS | Strong standalone offline speech path beyond Android-installed speech packs. | One mic owner; PCM adapter; model download consent; verify model license/size and benchmark. |
| P3 | Curated droid-mcp modules | Structured tool contracts and approval model. | No `droid-mcp-all`; no duplicate service/camera/overlay; measure Ktor/Netty APK impact. |

## Offline pedestrian routing implementation status

- MapLibre Native and OpenFreeMap are wired into `PedestrianMapActivity`; selected map regions are downloaded for offline use with bounded tile count and storage.
- BRouter is embedded from the pinned `third_party/brouter` submodule. Its trekking profile is packaged from the submodule, while OSM RD5 routing segments are downloaded over HTTPS and locally SHA-256 verified.
- Once the required map region and RD5 segments are cached, route calculation uses local files. The app announces BRouter turn hints in Indonesian and can announce arrival within the 20-metre threshold even when no turn hints are returned.
- This is not yet a complete mobility-navigation system: automatic off-route detection/rerouting, indoor navigation, guaranteed sidewalk/crossing quality, and obstacle depth/distance are not implemented or field-validated. Do not claim route safety.
- The map screen selects a coarse-accuracy provider when only approximate location is granted; this is implemented in `PedestrianMapActivity.bestProvider()`. Provider behavior still requires physical-device verification.

## Important non-goals

- Do not add a second AccessibilityService, camera, microphone, notification listener, TTS owner, or local LLM runtime.
- Do not expose an HTTP MCP server on the phone by default.
- Do not enable root, Shizuku, ADB, or hidden system-control permissions as a normal path.
- Do not let the AI send SMS, make calls, pay, buy, delete, install, share or change system security without a separate explicit confirmation workflow.
- Do not claim any capability is production-verified until it passes tests and physical-device validation.


## Permission UX implementation update — 16 November 2026

The broad permission request from the Settings permission button has been replaced on this branch by a feature-group picker:

- The user selects one capability before Android's permission prompt appears.
- SMS send and SMS read are separate groups.
- Contacts, call control, calendar, camera, location, microphone, Bluetooth, notifications, activity recognition, and audio files are separated.
- Call-log reading and nearby Wi-Fi discovery are not implemented; their picker entries are marked unavailable and request no permission. Their manifest permissions are removed.
- Already-granted permissions are filtered out.
- `Perms.required()` is deprecated and now returns only the microphone baseline to prevent new callers from requesting every sensitive permission at once.
- `PermsTest` covers the baseline, separate SMS grants, picker/group count, and invalid group IDs.

This is a least-privilege improvement to the Settings permission workflow. It is not yet a complete just-in-time runtime permission system: individual feature entry points still need to request their own group when a permission is missing. Keep this gap visible until all feature flows are wired and tested.


## Just-in-time location permission — 16 November 2026

- `CompassActivity` now checks fine/coarse location permission when the user selects "Where am I", "Save place", or "Guide to saved place".
- If missing, it requests only `Perms.GROUP_LOCATION`; no location is fetched until permission is granted and the user repeats the action.
- Denial is announced and the feature remains stopped. The UI does not silently proceed or use a guessed location.
- Remaining limitation: after granting permission the user must tap the feature again. A pending-action resume can be added later if lifecycle and duplicate-action behavior are tested.
- This change is scoped to the compass location screen. Other consumers (voice-command location, background trip monitor, etc.) still need their own just-in-time permission UX and denial tests.


## Voice-action permission deep links — 16 November 2026

- SettingsActivity now accepts an internal feature-group deep link and opens a confirmation dialog for only that feature's missing permissions.
- Voice-command attempts to answer/end a call and send SMS now open the exact permission request instead of only saying "go to Settings".
- Answer/end-call permission is isolated as `GROUP_ANSWER_CALLS` containing only `ANSWER_PHONE_CALLS`; it does not request call placement, phone state, contacts, or call history.
- SMS recipient lookup still requests contacts only when the target is a contact name; sending SMS requires only SEND_SMS.
- The user must still confirm the app's explanation dialog and Android's system permission prompt. A permission grant does not automatically execute the pending call/SMS action; the user must repeat the command. This is intentional for safety.
- The existing Settings group "Telepon" remains a broader user-selected bundle; action-level voice requests use the narrower answer-call group.


## Narrowed communication permission groups — 16 November 2026

- Split phone permissions into three independent user-selectable groups: place a call (`CALL_PHONE`), answer/end a call (`ANSWER_PHONE_CALLS`), and read phone state (`READ_PHONE_STATE`).
- Voice answer/end actions request only `ANSWER_PHONE_CALLS`. Dialing through the app's call action can continue to fall back to the system dialer without CALL_PHONE.
- Unit tests assert each group contains only its own permission.

## Just-in-time SMS read permission — 16 November 2026

- Voice commands `baca SMS`, `baca pesan singkat`, and `SMS terbaru` now check `READ_SMS` before calling `MessageReader.latestSms()`.
- If permission is missing, KOBRA opens the scoped `GROUP_SMS_READ` explanation and Android permission prompt; the inbox is not queried before the grant.
- After granting, the user repeats the voice command. No SMS is read automatically as a side effect of granting permission.
- `READ_CONTACTS` remains optional for resolving a sender's name; without it, the SMS reader uses the sender's number.
- This covers the audited voice entry point. Any future SMS-reading UI or background task must independently enforce the same permission check.

## Just-in-time activity-recognition permission — 16 November 2026

- Voice requests for the step count now check `ACTIVITY_RECOGNITION` on Android 10+ before reading or registering the hardware step sensor.
- If permission is missing, KOBRA opens the scoped `GROUP_ACTIVITY` prompt and stops the command. The user repeats the request after granting; no step sensor is registered before permission.
- Added a unit test for the activity-recognition group mapping (Android 10+ only).
- Accelerometer-based fall/pocket sensing remains separate; this permission gate applies to the step-counter/step-detector sensors.

## Just-in-time local-audio permission — 16 November 2026

- Voice commands to play local music now check the Android-version-specific audio-read permission before querying `MediaStore`.
- Missing permission opens only `GROUP_MEDIA`; playback does not start until the user grants permission and repeats the command.
- Android 13+ uses `READ_MEDIA_AUDIO`; earlier supported versions use `READ_EXTERNAL_STORAGE` as already mapped by `Perms.forGroup(GROUP_MEDIA)`.
- This protects the voice entry point. Document-picker access remains separately mediated by Android's Storage Access Framework and does not require broad media-library access.

## Unimplemented permission removal — 16 November 2026

- Branch-wide source review found no call-log query implementation and no nearby Wi-Fi discovery implementation. Removed `READ_CALL_LOG` and `NEARBY_WIFI_DEVICES` from the manifest and changed their picker groups to request no permission; labels clearly mark both capabilities unavailable.
- Removed unused `CHANGE_WIFI_STATE`; current code reads network/Wi-Fi state but does not toggle Wi-Fi.
- Added regression coverage proving the unimplemented call-log and nearby-Wi-Fi groups return no runtime permissions.
- Do not advertise call-log reading, nearby Wi-Fi discovery, or Wi-Fi toggling as current KOBRA functionality.


## Model asset integrity hardening — 18 November 2026

- The Qwen3 model asset is now checked at installation time against the pinned byte length (347,251,840) and SHA-256 (`2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139`).
- A corrupt or truncated private copy is no longer accepted based on file size alone; the installer verifies the complete digest before atomic activation.
- Pure-Java unit tests cover stable SHA-256 calculation, matching digest, wrong length, and malformed digest handling.
- CI must pass after this change before it is considered release-verified.
