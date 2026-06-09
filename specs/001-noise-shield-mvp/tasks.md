# Tasks: Noise Shield MVP

**Input**: Design documents from `/specs/001-noise-shield-mvp/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Not explicitly requested in spec — test tasks omitted. Validation via `quickstart.md` scenarios in Polish phase.

**Organization**: Tasks grouped by user story for independent implementation and testing.

**Spec revision (2026-06-09)**: Sign-in and server communication are optional. Onboarding skippable. Consent prompt sign-in-only. Bundled analysis defaults. Sign-in in Settings only. First sign-in discards local data with warning.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: User story label (US1–US5)

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Monorepo bootstrap and developer toolchain

- [X] T001 Create pnpm workspace and Turborepo config in `pnpm-workspace.yaml` and `turbo.json`
- [X] T002 [P] Scaffold NestJS API app in `apps/api/package.json` and `apps/api/src/main.ts`
- [X] T003 [P] Scaffold Expo React Native app in `apps/mobile/package.json` and `apps/mobile/app/_layout.tsx`
- [X] T004 [P] Scaffold shared package in `packages/shared/package.json` and `packages/shared/src/index.ts`
- [X] T005 [P] Scaffold audio-analysis package in `packages/audio-analysis/package.json` and `packages/audio-analysis/src/index.ts`
- [X] T006 Add PostgreSQL Docker Compose service in `docker-compose.yml`
- [X] T007 [P] Configure root ESLint and Prettier in `.eslintrc.cjs` and `.prettierrc`
- [X] T008 [P] Add root TypeScript base config in `tsconfig.base.json` with project references
- [X] T009 Create environment example files in `apps/api/.env.example` and `apps/mobile/.env.example`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before user story work

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T010 Define Prisma schema from data model in `apps/api/prisma/schema.prisma`
- [X] T011 Run initial Prisma migration in `apps/api/prisma/migrations/`
- [X] T012 [P] Implement masking sound catalog and profile enums in `packages/shared/src/catalog/sounds.ts`
- [X] T013 [P] Implement Zod schemas aligned with OpenAPI in `packages/shared/src/schemas/`
- [X] T014 [P] Define `AudioAnalysisPort` and `MaskingPlaybackPort` types in `packages/audio-analysis/src/ports.ts`
- [X] T015 Configure NestJS app module, config, and health check in `apps/api/src/app.module.ts` and `apps/api/src/modules/health/`
- [X] T016 Implement JWT auth guard and token service in `apps/api/src/modules/auth/jwt.strategy.ts` and `apps/api/src/modules/auth/auth.service.ts`
- [X] T017 Implement provider token verification (Google/Apple/Facebook) in `apps/api/src/modules/auth/providers/`
- [X] T018 Implement `POST /v1/auth/exchange` and `POST /v1/auth/refresh` in `apps/api/src/modules/auth/auth.controller.ts`
- [X] T019 Implement `GET /v1/users/me` in `apps/api/src/modules/users/users.controller.ts`
- [X] T020 Configure Expo prebuild and react-native-track-player plugin in `apps/mobile/app.json`
- [X] T021 [P] Setup Expo Router navigation shell in `apps/mobile/app/_layout.tsx` and `apps/mobile/app/index.tsx`
- [X] T022 Implement auth store with secure token persistence in `apps/mobile/src/stores/authStore.ts`
- [X] T023 Implement typed API client in `apps/mobile/src/services/api/client.ts`
- [ ] T024 Refactor root navigation in `apps/mobile/app/index.tsx` to route unsigned users directly to session (no sign-in redirect)
- [ ] T025 Remove mandatory auth gate in `apps/mobile/src/features/auth/AuthGate.tsx`; allow unsigned access to all core routes
- [X] T026 [P] Configure MMKV storage wrapper in `apps/mobile/src/services/storage/mmkv.ts`
- [X] T027 [P] Configure SQLite sync queue schema in `apps/mobile/src/services/sync/schema.sql`
- [ ] T028 [P] Add bundled default analysis tuning constants in `packages/shared/src/config/defaults.ts` per FR-034
- [ ] T029 Wire bundled defaults fallback in `apps/mobile/src/services/config/bundledDefaults.ts`

**Checkpoint**: Foundation ready — unsigned users can reach session; optional API auth online

---

## Phase 3: User Story 1 — Core Masking Session (Priority: P1) 🎯 MVP

**Goal**: Any user (signed in or not) can start a masking session, control volume/sound/timer, and play audio offline with background continuity

**Independent Test**: Launch app without sign-in or network, start session, switch sounds, set timer, lock screen 10+ minutes, stop session (quickstart Scenarios 1–3)

### Implementation for User Story 1

- [X] T030 [P] [US1] Bundle masking audio assets in `apps/mobile/assets/audio/`
- [X] T031 [P] [US1] Implement session state machine types and transitions in `apps/mobile/src/features/session/sessionStateMachine.ts`
- [X] T032 [US1] Implement Zustand session store in `apps/mobile/src/stores/sessionStore.ts`
- [X] T033 [US1] Implement react-native-track-player adapter for `MaskingPlaybackPort` in `apps/mobile/src/services/playback/TrackPlayerAdapter.ts`
- [X] T034 [US1] Register playback service and remote events in `apps/mobile/src/services/playback/playbackService.ts`
- [X] T035 [US1] Configure iOS background audio mode in `apps/mobile/app.json` via Expo config
- [X] T036 [US1] Configure Android foreground media service in `apps/mobile/app.json` via Expo config plugin
- [X] T037 [US1] Implement session home screen with start/stop controls in `apps/mobile/app/(main)/session/index.tsx`
- [X] T038 [US1] Implement sound picker UI for 8 masking sounds in `apps/mobile/src/features/session/SoundPicker.tsx`
- [X] T039 [US1] Implement volume slider control in `apps/mobile/src/features/session/VolumeControl.tsx`
- [X] T040 [US1] Implement session timer picker and expiry handler in `apps/mobile/src/features/session/TimerControl.tsx`
- [X] T041 [US1] Implement local favorites persistence (device-only) in `apps/mobile/src/features/session/favoritesLocal.ts`
- [ ] T042 [US1] Verify offline unsigned session path makes zero API calls in `apps/mobile/src/features/session/startSession.ts`
- [X] T043 [US1] Implement session-ended feedback UI state in `apps/mobile/src/features/session/SessionEndedBanner.tsx`
- [X] T044 [US1] Handle audio focus interruptions (calls/other apps) in `apps/mobile/src/services/playback/audioFocus.ts`
- [ ] T045 [US1] Remove sign-in prompts from session flow; ensure `apps/mobile/app/(auth)/sign-in.tsx` is not auto-routed

**Checkpoint**: User Story 1 fully functional — core masking works offline without sign-in

---

## Phase 4: User Story 2 — Onboarding, Permissions, and Privacy (Priority: P2)

**Goal**: First-time users understand the product, can skip onboarding, grant or deny mic access safely; data consent shown only to signed-in users

**Independent Test**: Fresh install → skip or complete onboarding → mic grant/deny paths → consent only after sign-in (quickstart Scenarios 4 and 6)

### Implementation for User Story 2

- [X] T046 [P] [US2] Create onboarding slide content component in `apps/mobile/src/features/onboarding/OnboardingSlides.tsx`
- [ ] T047 [US2] Add skip control and Settings re-entry in `apps/mobile/app/(onboarding)/index.tsx` and `apps/mobile/src/features/settings/OnboardingSetting.tsx`
- [X] T048 [US2] Implement microphone permission rationale screen in `apps/mobile/src/features/onboarding/MicPermissionScreen.tsx`
- [X] T049 [US2] Integrate platform mic permission request via Expo AV in `apps/mobile/src/services/permissions/microphone.ts`
- [X] T050 [US2] Implement limited/manual mode banner and gating in `apps/mobile/src/features/session/LimitedModeBanner.tsx`
- [X] T051 [US2] Disable analysis hooks when mic denied in `apps/mobile/src/features/session/sessionController.ts`
- [ ] T052 [US2] Gate data consent modal to signed-in users only in `apps/mobile/src/features/onboarding/DataConsentModal.tsx`
- [X] T053 [US2] Implement consent API client methods in `apps/mobile/src/services/api/consentApi.ts`
- [X] T054 [US2] Implement `GET/PUT /v1/consent` in `apps/api/src/modules/consent/consent.controller.ts` and service
- [X] T055 [US2] Persist consent record in Prisma and enforce opt-in flag in `apps/api/src/modules/consent/consent.service.ts`
- [X] T056 [US2] Add privacy disclaimer copy constants in `packages/shared/src/copy/privacy.ts`

**Checkpoint**: Skippable onboarding, mic permission, and sign-in-only consent flows complete

---

## Phase 5: User Story 3 — Noise Analysis and Adaptive Suggestions (Priority: P3)

**Goal**: On-device ambient analysis with continuous refresh and automatic masking profile application using bundled defaults when offline/unsigned

**Independent Test**: Grant mic → start session in varied noise → see level indicator → auto-switch profile within 30s (quickstart Scenario 5)

### Implementation for User Story 3

- [X] T057 [P] [US3] Implement `FakeAudioAnalysisPort` for tests in `packages/audio-analysis/src/fakeAudioAnalysisPort.ts`
- [X] T058 [US3] Implement microphone capture adapter in `packages/audio-analysis/src/capture/MicCaptureAdapter.ts`
- [X] T059 [US3] Implement RMS level bucketing in `packages/audio-analysis/src/analysis/levelEstimator.ts`
- [X] T060 [US3] Implement heuristic broad-profile classifier in `packages/audio-analysis/src/analysis/profileClassifier.ts`
- [X] T061 [US3] Implement MVP `AudioAnalysisPort` orchestrator in `packages/audio-analysis/src/heuristicAnalysisPort.ts`
- [X] T062 [US3] Wire analysis start/stop to session lifecycle in `apps/mobile/src/features/analysis/analysisController.ts`
- [X] T063 [US3] Implement noise level and profile UI in `apps/mobile/src/features/analysis/NoiseIndicator.tsx`
- [X] T064 [US3] Implement auto-apply suggestion logic with debounce in `apps/mobile/src/features/analysis/autoApply.ts`
- [X] T065 [US3] Implement crossfade transition in `apps/mobile/src/services/playback/crossfade.ts`
- [X] T066 [US3] Implement manual override precedence rules in `apps/mobile/src/features/analysis/manualOverride.ts`
- [X] T067 [US3] Implement quiet-environment and permission-revoked edge handling in `apps/mobile/src/features/analysis/edgeCases.ts`
- [ ] T068 [US3] Load bundled defaults in `apps/mobile/src/features/analysis/autoApply.ts` when unsigned or offline
- [X] T069 [US3] Seed remote config defaults for thresholds in `apps/api/prisma/seed.ts`

**Checkpoint**: Adaptive analysis operational on-device with bundled-default fallback

---

## Phase 6: User Story 4 — Optional Sign-In and Cross-Device Sync (Priority: P4)

**Goal**: Optional Settings-only sign-in for cross-device sync; first sign-in discards local data with warning

**Independent Test**: Use app unsigned → sign in from Settings → confirm warning → local data discarded → sync when online (quickstart Scenario 7)

### Implementation for User Story 4

- [X] T070 [P] [US4] Implement preferences module service with LWW in `apps/api/src/modules/preferences/preferences.service.ts`
- [X] T071 [P] [US4] Implement favorites module service in `apps/api/src/modules/favorites/favorites.service.ts`
- [X] T072 [US4] Implement `GET/PUT /v1/preferences` in `apps/api/src/modules/preferences/preferences.controller.ts`
- [X] T073 [US4] Implement `GET/PUT /v1/favorites` in `apps/api/src/modules/favorites/favorites.controller.ts`
- [X] T074 [US4] Implement sync queue enqueue/dequeue in `apps/mobile/src/services/sync/syncQueue.ts`
- [X] T075 [US4] Implement connectivity listener and background sync worker in `apps/mobile/src/services/sync/syncWorker.ts`
- [X] T076 [US4] Implement LWW merge using `server_received_at` in `apps/mobile/src/services/sync/lwwMerge.ts`
- [ ] T077 [US4] Move sign-in UI to Settings in `apps/mobile/src/features/settings/SignInSetting.tsx` (remove as entry gate)
- [ ] T078 [US4] Implement first-sign-in discard warning dialog in `apps/mobile/src/features/auth/SignInDiscardWarning.tsx` per FR-035
- [ ] T079 [US4] Discard local favorites/preferences on first sign-in in `apps/mobile/src/features/auth/signInFlow.ts`
- [ ] T080 [US4] Remove offline sign-in block component `apps/mobile/src/features/auth/OfflineSignInBlock.tsx` from default flows
- [ ] T081 [US4] Migrate local favorites to synced model only after sign-in in `apps/mobile/src/features/session/favoritesSync.ts`
- [ ] T082 [US4] Implement preferences pull on sign-in in `apps/mobile/src/features/settings/preferencesHydration.ts`
- [ ] T083 [US4] Implement remote config fetch/cache with bundled fallback in `apps/mobile/src/services/api/remoteConfigApi.ts`
- [X] T084 [US4] Implement remote config fetch endpoint in `apps/api/src/modules/remote-config/remote-config.controller.ts`

**Checkpoint**: Optional sign-in from Settings with discard-local policy and sync when online

---

## Phase 7: User Story 5 — Personalization, Feedback, and Habit Building (Priority: P5)

**Goal**: EN/PT localization, theme control, session feedback stored locally; server upload optional when signed in

**Independent Test**: Switch language and theme unsigned → submit session feedback → verify local storage (quickstart Scenarios 8–9)

### Implementation for User Story 5

- [X] T085 [P] [US5] Configure i18next with English locale in `apps/mobile/src/i18n/locales/en.json`
- [X] T086 [P] [US5] Add Portuguese locale in `apps/mobile/src/i18n/locales/pt.json`
- [X] T087 [US5] Wire i18n provider and language switcher in `apps/mobile/src/i18n/index.ts` and `apps/mobile/src/features/settings/LanguageSetting.tsx`
- [X] T088 [P] [US5] Implement theme tokens and provider in `apps/mobile/src/theme/ThemeProvider.tsx`
- [X] T089 [US5] Implement appearance settings (light/dark/system) in `apps/mobile/src/features/settings/AppearanceSetting.tsx`
- [X] T090 [US5] Persist theme/language preferences to local storage and sync queue in `apps/mobile/src/features/settings/preferencesLocal.ts`
- [X] T091 [US5] Implement session helpfulness feedback UI in `apps/mobile/src/features/feedback/SessionFeedbackModal.tsx`
- [X] T092 [US5] Implement `POST /v1/feedback/session` in `apps/api/src/modules/feedback/feedback.controller.ts`
- [X] T093 [US5] Store feedback records in Prisma in `apps/api/src/modules/feedback/feedback.service.ts`
- [ ] T094 [US5] Store feedback locally when unsigned; upload via sync queue only when signed in in `apps/mobile/src/features/feedback/submitFeedback.ts`

**Checkpoint**: Personalization and local feedback loop complete without sign-in

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Opt-in features, hardening, and end-to-end validation

- [X] T095 [P] Implement `POST /v1/features/acoustic` with consent guard in `apps/api/src/modules/features/features.controller.ts`
- [X] T096 Implement opt-in acoustic feature upload client in `apps/mobile/src/services/sync/featureUpload.ts`
- [X] T097 [P] Add experiment assignment foundation in `apps/api/src/modules/remote-config/experiments.service.ts`
- [X] T098 Implement session restore after unexpected app termination in `apps/mobile/src/features/session/sessionRestore.ts`
- [X] T099 Implement timer-expired background notification in `apps/mobile/src/features/session/timerNotification.ts`
- [X] T100 [P] Add API request logging and error filter in `apps/api/src/common/filters/http-exception.filter.ts`
- [X] T101 [P] Add mobile global error boundary in `apps/mobile/src/components/ErrorBoundary.tsx`
- [ ] T102 Validate quickstart scenarios for unsigned offline flows in `specs/001-noise-shield-mvp/quickstart-results.md`
- [ ] T103 [P] Update `specs/001-noise-shield-mvp/plan.md` constraints to reflect optional auth (FR-024 superseded)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — complete ✅
- **Foundational (Phase 2)**: Depends on Setup — **blocks all user stories**; T024–T025, T028–T029 remain
- **User Stories (Phases 3–7)**: Depend on Foundational completion
- **Polish (Phase 8)**: Depends on desired user stories being complete

### User Story Dependencies

| Story | Depends on | Notes |
|-------|------------|-------|
| US1 (P1) | Foundational T024–T025 | No sign-in or mic required for basic masking |
| US2 (P2) | US1 session shell | Skippable onboarding + sign-in-only consent |
| US3 (P3) | US1, US2 mic grant path | Bundled defaults when unsigned/offline |
| US4 (P4) | Foundational | Optional Settings sign-in; discard-local on first sign-in |
| US5 (P5) | US1 sessions | Local feedback; server upload when signed in |

### Within Each User Story

- Bundled defaults before auto-apply wiring (US3)
- Remove auth gate before unsigned session validation (US1)
- Sign-in discard warning before sync migration (US4)

### Parallel Opportunities

- **Phase 2**: T028–T029 in parallel after T024–T025
- **US1**: T030–T031 already done; T042–T045 sequential
- **US2**: T046–T056; T047 and T052 parallel after T025
- **US3**: T057–T061 parallel; T068 after T028–T029
- **US4**: T070–T071 parallel; T077–T083 mostly sequential
- **US5**: T085–T088 parallel
- **Polish**: T102–T103 parallel

---

## Parallel Example: Remaining Critical Path (optional auth)

```bash
# Unblock unsigned access first:
T024: Refactor root navigation in apps/mobile/app/index.tsx
T025: Remove mandatory auth gate in apps/mobile/src/features/auth/AuthGate.tsx

# Bundled defaults (parallel):
T028: packages/shared/src/config/defaults.ts
T029: apps/mobile/src/services/config/bundledDefaults.ts

# Then US4 sign-in flow:
T077 → T078 → T079 → T081 → T082
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 2 remaining: T024, T025, T028, T029
2. Complete Phase 3 remaining: T042, T045
3. **STOP and VALIDATE** against quickstart Scenarios 1–3 (unsigned + offline)
4. Demo offline masking without sign-in

### Incremental Delivery

1. Phase 2 delta → unsigned app shell
2. US1 delta → offline masking MVP without auth
3. US2 → skippable onboarding + sign-in-only consent
4. US3 → bundled-default analysis
5. US4 → optional Settings sign-in + discard-local
6. US5 → local feedback polish
7. Polish → full quickstart pass

### Suggested MVP Scope

**Minimum shippable slice**: Phase 2 delta (T024–T025, T028–T029) + US1 delta (T042, T045) — **6 open tasks**

Delivers: unsigned offline masking with background audio, timer, volume, local favorites.

**Recommended beta slice**: Above + US2 (T047, T052) + US4 sign-in flow (T077–T079) — adds trust UX and optional sync.

---

## Notes

- Tasks marked `[X]` reflect existing implementation from initial build
- Tasks marked `[ ]` are spec deltas from optional-auth revision and clarify session
- Auth is optional — never block session routes on sign-in state
- First sign-in MUST warn and discard local favorites/preferences (FR-035)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independence per `quickstart.md`
