# Feature Specification: Noise Shield MVP (Android local)

**Feature Branch**: `001-noise-shield-mvp`  
**Updated**: 2026-07-25  
**Status**: Active — Android-only, offline-first rewrite

## Product

Noise Shield is an Android app that reduces perceived unwanted environmental noise through adaptive masking. All capture, analysis, and playback are on-device. No backend, accounts, or network features.

## Clarifications (rewrite)

- Sign-in / sync / remote config / feature upload: **out of scope** (removed).
- Platform: **Android only** (Kotlin + Oboe).
- Offline: all core capabilities work with airplane mode from first launch.

## User Stories

### US1 — Core masking session (P1)

Start/stop masking, pick sound, volume, timer, background playback — no network.

### US2 — Onboarding & mic (P2)

Skippable onboarding; mic rationale; limited mode if mic denied; no medical/ANC claims.

### US3 — Analysis & auto-suggest (P3)

On-device level + broad profile; continuous refresh; auto-apply with crossfade; manual override until ambient shift.

### US4 — Personalization (P4)

EN/PT, theme, favorites, local helpfulness feedback (device-only).

## Functional requirements (active)

- **FR-001**: Android app for adaptive noise masking.
- **FR-002**: Skippable onboarding; revisit from Settings.
- **FR-003**: No medical claims; no perfect ANC promise.
- **FR-025**: Background masking via foreground media service.
- **FR-006/007**: Mic permission + limited mode.
- **FR-008/009/029/030**: On-device analysis, profiles, auto-apply, crossfade.
- **FR-010/011**: Eight masking sounds; manual controls; favorites.
- **FR-012/013/023**: Fully offline core session.
- **FR-015**: No raw audio upload.
- **FR-017/018**: EN/PT; light/dark/system theme.
- **FR-019**: Local session feedback.
- **FR-021**: Engine/JNI extension point for future DSP/ML.
- **FR-022**: No always-on background mic outside active session.

## Removed (former cloud/auth)

FR-004, FR-005, FR-014, FR-016, FR-024, FR-027, FR-028, FR-031, FR-033, FR-034, FR-035 and related sync success criteria.

## Success criteria (active)

- SC-001/002/012: Core offline journeys succeed without sign-in prompts.
- SC-009: Background ≥ 30 min.
- SC-005/010: Suggestion quality / auto-apply timing targets.
- SC-007: EN/PT switching.
- SC-008: Documented native engine extension point.

## Out of scope

- iOS / cross-platform frameworks
- Backend, accounts, sync, remote config
- Guaranteed ANC, medical claims
- Always-on background mic
- Raw audio upload
