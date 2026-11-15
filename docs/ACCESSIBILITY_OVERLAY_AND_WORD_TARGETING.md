# KOBRA OS — Minimal Overlay & Word-Level Accessibility Targeting

Date: 2026-11-16

## Product requirement

Keep the user's current app unobstructed. The only persistent floating control is the compact microphone orb. Conversation, model routing, vision, memory, navigation and device automation remain in their owning background/service components and are surfaced through speech or the app UI only when explicitly requested.

## Overlay policy

- Persistent overlay: microphone orb only, using the existing `FloatingVoiceOrbManager` and `TYPE_ACCESSIBILITY_OVERLAY`.
- Current orb size: 44dp; it supports tap, double tap, long press and drag. The edge tab and dismiss affordance are transient/hidden, not permanent screen furniture.
- Focus indicator: transient, transparent, `FLAG_NOT_TOUCHABLE`, no fill, no dim layer, no glow. It must never intercept taps.
- Number badges and grid overlays stay hidden during normal operation. Voice-only target indexing remains available.
- No always-on chat panel, catalog, camera preview, status panel, toolbar, edge rail or full-screen scrim.

## Word-level targeting

The active accessibility focus is resolved from a fresh accessibility tree. KOBRA requests
`EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY` only when the provider advertises support, then derives a
tight rectangle from the actual character boxes for the selected word. The visual outline is
drawn only around that rectangle with a 0.75dp stroke and minimal anti-clipping padding.

Important constraints:
- A word is not assumed to be clickable merely because it has character geometry.
- A click is sent only through the currently exposed accessibility action/gesture path and is
  reported as accepted/rejected, not as guaranteed app-level success.
- If the app does not expose selection offsets or character geometry, the word outline stays
  hidden. KOBRA must not substitute a whole line, paragraph, text view or guessed coordinate.
- For normal buttons and controls, use the smallest actionable accessibility node and its fresh
  bounds. Do not create synthetic per-word hitboxes over apps that expose only one large node.
- Never cache `AccessibilityNodeInfo` objects across screen changes; resolve again immediately
  before acting.

## Source-code responsibilities

- `FloatingVoiceOrbManager.kt`: the single persistent microphone control.
- `AsistenAccessibilityService.java`: sole owner of global accessibility events and device
  actions; do not create a second accessibility service.
- `voiceaccess/NodeIndexer.kt`: accessibility tree indexing, word text movement and exact
  character geometry lookup.
- `voiceaccess/FocusOutlineOverlay.kt`: transient transparent focus indicator.
- `voiceaccess/VoiceAccessEngine.kt`: voice commands and overlay lifecycle.

## Open-source references to study, not blindly copy

- Android platform API docs: character geometry via `refreshWithExtraData`, movement granularity
  by word, and accessibility overlays.
- `X-SLAYER/flutter_accessibility_service`: reference for action vocabulary and selection
  granularity; Flutter plugin, not a drop-in native implementation.
- `handy-ai`: voice-first interaction, narrated actions and easy cancellation.
- `MantiClaw-Agent`: bounded action planning, stuck detection, structured result states and audit.
- `V.A.Y.U-Agent` / `OnScreen-Agent`: screen-agent tool schemas and observe-act-verify loop.
- `sherpa-onnx`: offline speech recognition, VAD and TTS adapters.
- `SIGHT`, `SightBuddy`, `atlas-sight`, `Sailens`: assistive vision and navigation patterns.
- Reuse only after checking the exact source and model licenses. AGPL/GPL or unlicensed projects
  remain architecture references unless the project explicitly accepts their obligations.

## Verification required before device testing

1. With KOBRA running over another app, confirm only the microphone orb is visible at rest.
2. Confirm focus outline is absent until a supported word is accessibility-focused.
3. Confirm the outline hugs the selected word, not the line or paragraph.
4. Confirm unsupported providers produce no outline and no guessed click.
5. Confirm the focus overlay never consumes touch input.
6. Confirm app/window changes clear stale focus overlays.
7. Confirm microphone orb can be dragged and does not cover common navigation/typing controls.
8. Confirm no second microphone recognizer or camera owner is started by overlay code.
9. Confirm all overlay views are removed when the accessibility service is destroyed.

This document records intended behavior and code paths. It is not a claim that device-level
compatibility has been verified; physical-device testing remains a separate gate.
