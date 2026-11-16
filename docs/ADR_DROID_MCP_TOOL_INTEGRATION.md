# ADR: droid-mcp tool SDK integration boundary

Date: 2026-11-16

## Source audited

- Upstream: https://github.com/stixez/droid-mcp
- Release inspected: `v0.14.0` (2026-11-08).
- License: Apache-2.0.
- Advertised surface: 160+ MCP tools across 50+ Android modules.
- Minimum SDK: 28, matching KOBRA's current minSdk 28.
- The SDK supports in-process tool calls for on-device models and an optional authenticated HTTP MCP server.
- The SDK exposes per-tool gating, read-only mode, output schemas, approval hooks and module-specific permission declarations.

## Useful modules

Potentially useful after dependency and permission review:

- `device`: read-only device, battery, connectivity and storage facts.
- `calendar`: read events and search agendas; write tools only appear with write permission.
- `contacts`: search/read contacts; write tools gated by write permission.
- `apps`: list installed apps, app metadata and usage.
- `alarms`: alarms and timers.
- `location` / `sensors`: location and device sensor information.
- `files`: file listing and read tools; writes/deletes are opt-in.
- `vibration`: haptic patterns.

## Modules that must not be imported as-is

KOBRA already owns one global AccessibilityService, camera pipeline, microphone/audio capture, and overlay orb. Do not import upstream modules that add a second owner or a persistent overlay:

- `droid-mcp-accessibility`: declares its own `DroidMcpAccessibilityService`.
- `droid-mcp-camera`: do not add a second camera capture owner.
- `droid-mcp-screenshot`: uses its own screen capture service/consent flow; adapt only if it can use KOBRA's current screenshot contract.
- `droid-mcp-notification-listener`, `notifications-reply`, `notification-watch`: KOBRA already has notification ownership; do not add duplicate listeners.
- `droid-mcp-overlay`, `droid-mcp-ime`: conflicts with the mic-only overlay and existing keyboard/audio flows.
- `droid-mcp-shizuku` / `droid-mcp-root`: never enable by default.

## Build and runtime concerns

- `droid-mcp-core` includes Ktor server and Netty dependencies even when the host intends to use in-process calls only. Measure APK size, method count, ABI and startup impact before adding it.
- The `droid-mcp-all` aggregator includes many service-owning modules; it is explicitly not suitable as a blind one-line dependency for KOBRA.
- The optional HTTP server listens on phone network interfaces. It must remain disabled by default; if ever enabled, require bearer-token pairing, TLS or trusted-network disclosure, and explicit user start/stop.
- Tool permissions are separate from the upstream app's UI. KOBRA must explain each permission and request only when the user enables the relevant capability.

## Integration plan

1. Do not add `droid-mcp-all` or the upstream host app.
2. Prototype a small, selected read-only subset using the SDK's in-process API only, beginning with device status and calendar read.
3. Keep the model tool schema grounded in the SDK's `listToolsJson()` and validate every tool call against the registered tool and output schema.
4. Default to read-only. Do not register SMS send, contact/calendar/file writes, delete, network toggles or system setting changes until a KOBRA-owned, explicit confirmation gate is implemented and tested.
5. For accessibility/screen tools, adapt to the existing `AsistenAccessibilityService`; never enable upstream `DroidMcpAccessibilityService`.
6. Benchmark dependency footprint and startup/RAM before expanding the module set.

## Decision

**High-priority architecture reference; no runtime dependency imported yet.** A curated SDK integration is promising, but only after the local model tool-call protocol, permission UX, and single-owner service constraints are defined. The 160+ tool count is not a claim that those tools are installed in KOBRA.
