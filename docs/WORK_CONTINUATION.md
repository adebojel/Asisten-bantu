# KOBRA OS Development Handoff

Updated: 2026-11-16 (Asia/Jakarta)

## Active development branch

- Repository: https://github.com/adebojel/Asisten-bantu
- Main branch: `main`
- Active PR: https://github.com/adebojel/Asisten-bantu/pull/16
- Branch: `research/open-source-expansion-2026-11-15`
- Current documented head at handoff creation: `025838d3ca2a1a84368c22d7a65b9a0683ba758e`
- The PR is intentionally not merged yet; more implementation and final verification are pending.

## Completed in the active branch

1. Expanded open-source repository research catalog with license status and explicit integration decisions.
2. Added transparent minimal-overlay and word-level targeting requirements.
3. Tightened word focus geometry: only request per-character bounds if the provider advertises support; no full-line/paragraph substitute.
4. Reduced focus outline stroke/padding; overlay remains transparent and non-touchable.
5. Connected explicit visual-screen questions in the active `CommandRouter.tryRouteFastOrVisual()` path to `OnlineAiRouter.askBlocking()` with screenshot and accessibility context.
6. Visual analysis is allowed only through the existing provider router, which checks configured credentials, network, zero-price model availability and image support. If unavailable, the app uses local accessibility text and states that visual AI is unavailable.
7. No autonomous tap/type action was connected to this visual-analysis path.

## CI findings

- Earlier commit `45b559df57ef872c48fa0c332bc6c4677fcf1c75` passed the APK workflow.
- New screen-analysis integration initially failed compilation because the base `AccessibilityService` type does not expose `readScreenText()`.
- Corrected fallback to safe-cast to `AsistenAccessibilityService` in commit `6e12860088ff63b9b46b425ffd08dbc9b48331a6`.
- Latest documentation commit `025838d3ca2a1a84368c22d7a65b9a0683ba758e` includes that correction.
- The workflow was still running when this handoff was written. Re-check GitHub Actions for the latest branch commit before merging.

## Repository/branch audit

- Main already contains the primary KOBRA runtime files, Qwen model manager/installer, offline knowledge store, camera/vision modules, accessibility service, voice-access engine, mic orb, and 3D SVG documentation art.
- Legacy branches `feat/bundle-qwen3-in-apk`, `feat/gemini-live-voice-catalog`, `feat/openai-brain-provider`, `feat/unified-auto-vision-start`, `feat/voice-silence-wakeword`, `fix/voice-recognition-language-fallback`, `kobra-os-v7`, and `testable-voice-silence-parser` have no unique file paths compared with main, but some are divergent and contain file modifications. Do not merge whole branches blindly; compare relevant diffs and preserve only missing functionality.
- The current GitHub connector exposes branch creation/update but no branch-delete operation. Do not claim branches were deleted; branch cleanup must be done through a supported GitHub UI/API route after safe merge.

## README and release work still pending

- Main README already links to latest APK, all releases, Actions, source and issues.
- Main already has `docs/kobra-hero-3d.svg` and `docs/kobra-overview.svg`.
- The only PNG found under `public/assets/aistudio/` is a screenshot of a failed CI log, not an app screenshot. Do not present it as product UI.
- Before final test stage: rewrite README comprehensively, show the existing 3D concept art and architecture diagram, add only genuine app screenshots supplied/verified, explain actual vs experimental features, permissions, online/offline behavior, model sizes/licensing, install steps, troubleshooting, privacy, support, releases and checksum.
- Release workflow on main already builds debug APK and publishes `Kobra-OS-main.apk` plus `SHA256SUMS.txt` on each main push. Verify signing and release artifacts before calling a build production-ready.

## Next actions

1. Re-check CI for commit `025838d3ca2a1a84368c22d7a65b9a0683ba758e`; inspect full logs if it fails and fix.
2. Add tests for screen-analysis provider unavailable, missing screenshot, and local fallback where practical.
3. Continue the bounded screen-agent work only after implementing confirmation gates for risky actions and fresh post-action verification. Never let a text-entry action press Enter automatically.
4. Review legacy branch diffs and port only changes missing from main.
5. Audit mic-only overlay behavior and exact word-target fallback paths; no second mic/camera/AccessibilityService owner.
6. Once implementation and CI are stable, merge the active PR and any remaining reviewed PRs into main.
7. Rewrite README and verify the latest release/APK/checksum.
8. Only then clean up obsolete branches. Preserve main and any explicitly retained release branches.
9. Device testing remains deferred until the user says implementation is ready.
