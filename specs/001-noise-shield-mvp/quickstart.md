# Quickstart: Noise Shield MVP Validation

**Feature**: `001-noise-shield-mvp`  
**Plan**: [plan.md](./plan.md)  
**Contracts**: [openapi.yaml](./contracts/openapi.yaml), [audio-analysis-port.md](./contracts/audio-analysis-port.md)  
**Data model**: [data-model.md](./data-model.md)

This guide defines **runnable validation scenarios** to prove the MVP end-to-end once implementation exists. It is not an implementation tutorial.

## Prerequisites

- Node.js 20 LTS, pnpm 9+
- Xcode 15+ (iOS simulator or device)
- Android Studio / SDK 34+ (emulator or device)
- PostgreSQL 16 (local or Docker)
- Provider sandbox credentials: Google, Apple, Facebook (auth testing)
- Physical device recommended for microphone and background audio tests

## Repository bootstrap (after implementation)

```bash
pnpm install
docker compose up -d postgres
pnpm --filter @noise-shield/api prisma migrate dev
pnpm --filter @noise-shield/api dev
pnpm --filter @noise-shield/mobile start
```

Environment files (examples):
- `apps/api/.env` — `DATABASE_URL`, `JWT_SECRET`, provider verify keys
- `apps/mobile/.env` — `API_BASE_URL`, provider client ids

## Scenario 1: Auth gate and first session (SC-001 partial)

**Proves**: FR-024, FR-004, User Story 1

1. Launch app unsigned → attempt Start Session → expect redirect to sign-in.
2. Sign in with Google (or Apple/Facebook sandbox).
3. Complete onboarding and grant microphone permission.
4. Start session → white noise plays at default volume.
5. Set 1-minute timer → wait → playback stops with session-ended feedback.

**Pass**: No masking before auth; session completes after auth.

## Scenario 2: Offline core masking (SC-002)

**Proves**: FR-012, FR-013, User Story 1

1. Sign in while online; wait for token persistence.
2. Enable airplane mode.
3. Start session, change sound, adjust volume, stop session.

**Pass**: Zero network errors; playback and controls work.

## Scenario 3: Background playback (SC-009)

**Proves**: FR-025, FR-026

1. Start 30+ minute session (or shorter with clock override in dev menu).
2. Lock screen / switch to another app for 10+ minutes.
3. Return to Noise Shield.

**Pass**: Audio continued; session state intact; lock-screen controls respond.

## Scenario 4: Limited mode without microphone (FR-007)

1. Deny microphone permission.
2. Open app → confirm limited/manual mode label.
3. Start session with manual sound selection.

**Pass**: Masking works; no analysis UI claims active listening.

## Scenario 5: Adaptive auto-apply (SC-005, SC-010)

**Proves**: FR-029, FR-030, User Story 3

1. Grant mic permission; start session in quiet room → note profile.
2. Play looped traffic or fan noise near device.
3. Within 30 s, app auto-applies new masking profile without confirmation.
4. Verify crossfade is smooth (no hard pop).

**Pass**: ≥70% match in scripted six-profile test suite; auto-apply within 30 s in profile-shift test.

## Scenario 6: Separate data consent (FR-027, FR-028)

1. Complete onboarding → separate consent modal appears (not mic dialog).
2. Decline → inspect network traffic during session → no `POST /v1/features/acoustic`.
3. Enable consent in Settings → new session → derived features may upload; still no raw audio.

**Pass**: Consent is independent; opt-in gating enforced server-side (403 when declined).

## Scenario 7: Preference sync and LWW conflict (SC-003, SC-011)

**Proves**: FR-020, FR-031, User Story 4

1. Device A online: set theme dark.
2. Device B offline: set theme light.
3. Device B online: sync.
4. Device A online: sync.

**Pass**: Final theme matches change with later `server_received_at` (inspect via `GET /v1/preferences`).

## Scenario 8: Localization and theme (SC-007, FR-017, FR-018)

1. Settings → language Portuguese → primary flows translated.
2. Toggle light/dark/system theme → persists after restart.

**Pass**: No reinstall required; theme and language restored from local/synced prefs.

## Scenario 9: Session feedback (SC-006)

1. Complete session with auto-suggested fan profile.
2. Submit helpfulness rating.
3. Verify `POST /v1/feedback/session` payload includes `session_id`, `sound_id`, `suggested_profile`.

**Pass**: ≥80% submissions correctly associated in integration test harness.

## Scenario 10: API contract smoke (contracts/openapi.yaml)

```bash
pnpm --filter @noise-shield/api test:contract
```

**Pass**: OpenAPI request/response validation for auth exchange, preferences, consent, feedback, remote-config.

## Automated test mapping

| Layer | Tool | Covers |
|-------|------|--------|
| API unit/integration | Jest + Supertest | Auth, LWW, consent gate |
| Mobile unit | Jest | Classifier, session state machine, ports |
| Mobile E2E | Maestro or Detox | Scenarios 1–4, 8 |
| Manual/device | Checklist above | Scenarios 3, 5, 6 (mic/audio) |

## Definition of ready for `/speckit-tasks`

- [ ] All scenarios mapped to epics in `tasks.md`
- [ ] OpenAPI validates against running API
- [ ] `AudioAnalysisPort` fake usable in CI without microphone hardware
