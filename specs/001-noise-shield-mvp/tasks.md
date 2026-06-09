# Tasks: Noise Shield MVP

**Input**: Design documents from `/specs/001-noise-shield-mvp/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Not explicitly requested in spec — test tasks omitted. Validation via `quickstart.md` scenarios in Polish phase.

**Organization**: Tasks grouped by user story for independent implementation and testing.

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
- [X] T024 Implement sign-in screen with Google/Apple/Facebook buttons in `apps/mobile/app/(auth)/sign-in.tsx`
- [X] T025 Implement auth gate redirect preventing session routes when unsigned in `apps/mobile/src/features/auth/AuthGate.tsx`
- [X] T026 [P] Configure MMKV storage wrapper in `apps/mobile/src/services/storage/mmkv.ts`
- [X] T027 [P] Configure SQLite sync queue schema in `apps/mobile/src/services/sync/schema.sql`

**Checkpoint**: Foundation ready — authenticated mobile shell and API auth online

---

## Phase 3: User Story 1 — Core Masking Session (Priority: P1) 🎯 MVP

**Goal**: Signed-in user can start a masking session, control volume/sound/timer, and play audio offline with background continuity

**Independent Test**: Sign in, disable network, start session, switch sounds, set timer, lock screen for 10+ minutes, stop session (quickstart Scenario 1–3)

### Implementation for User Story 1

- [X] T028 [P] [US1] Bundle masking audio assets in `apps/mobile/assets/audio/`
- [X] T029 [P] [US1] Implement session state machine types and transitions in `apps/mobile/src/features/session/sessionStateMachine.ts`
- [X] T030 [US1] Implement Zustand session store in `apps/mobile/src/stores/sessionStore.ts`
- [X] T031 [US1] Implement react-native-track-player adapter for `MaskingPlaybackPort` in `apps/mobile/src/services/playback/TrackPlayerAdapter.ts`
- [X] T032 [US1] Register playback service and remote events in `apps/mobile/src/services/playback/playbackService.ts`
- [X] T033 [US1] Configure iOS background audio mode in `apps/mobile/ios/NoiseShield/Info.plist` via Expo config
- [X] T034 [US1] Configure Android foreground media service in `apps/mobile/android/` via Expo config plugin
- [X] T035 [US1] Implement session home screen with start/stop controls in `apps/mobile/app/(main)/session/index.tsx`
- [X] T036 [US1] Implement sound picker UI for 8 masking sounds in `apps/mobile/src/features/session/SoundPicker.tsx`
- [X] T037 [US1] Implement volume slider control in `apps/mobile/src/features/session/VolumeControl.tsx`
- [X] T038 [US1] Implement session timer picker and expiry handler in `apps/mobile/src/features/session/TimerControl.tsx`
- [X] T039 [US1] Implement local favorites persistence (device-only) in `apps/mobile/src/features/session/favoritesLocal.ts`
- [X] T040 [US1] Wire offline session path ensuring no API calls during playback in `apps/mobile/src/features/session/startSession.ts`
- [X] T041 [US1] Implement session-ended feedback UI state in `apps/mobile/src/features/session/SessionEndedBanner.tsx`
- [X] T042 [US1] Handle audio focus interruptions (calls/other apps) in `apps/mobile/src/services/playback/audioFocus.ts`

**Checkpoint**: User Story 1 fully functional — core masking works offline after sign-in

---

## Phase 4: User Story 2 — Onboarding, Permissions, and Privacy (Priority: P2)

**Goal**: First-time users understand the product, grant or deny mic access safely, and see separate data-collection consent

**Independent Test**: Fresh install → onboarding copy → mic grant/deny paths → separate consent prompt → limited mode works (quickstart Scenarios 4 and 6)

### Implementation for User Story 2

- [X] T043 [P] [US2] Create onboarding slide content component in `apps/mobile/src/features/onboarding/OnboardingSlides.tsx`
- [X] T044 [US2] Implement onboarding flow with completion flag in `apps/mobile/app/(onboarding)/index.tsx`
- [X] T045 [US2] Implement microphone permission rationale screen in `apps/mobile/src/features/onboarding/MicPermissionScreen.tsx`
- [X] T046 [US2] Integrate platform mic permission request via Expo AV or audio API in `apps/mobile/src/services/permissions/microphone.ts`
- [X] T047 [US2] Implement limited/manual mode banner and gating in `apps/mobile/src/features/session/LimitedModeBanner.tsx`
- [X] T048 [US2] Disable analysis hooks when mic denied in `apps/mobile/src/features/session/sessionController.ts`
- [X] T049 [US2] Implement post-onboarding data consent modal in `apps/mobile/src/features/onboarding/DataConsentModal.tsx`
- [X] T050 [US2] Implement consent API client methods in `apps/mobile/src/services/api/consentApi.ts`
- [X] T051 [US2] Implement `GET/PUT /v1/consent` in `apps/api/src/modules/consent/consent.controller.ts` and service
- [X] T052 [US2] Persist consent record in Prisma and enforce opt-in flag in `apps/api/src/modules/consent/consent.service.ts`
- [X] T053 [US2] Add privacy disclaimer copy constants in `packages/shared/src/copy/privacy.ts`

**Checkpoint**: Onboarding, mic permission, and separate consent flows complete

---

## Phase 5: User Story 3 — Noise Analysis and Adaptive Suggestions (Priority: P3)

**Goal**: On-device ambient analysis with continuous refresh and automatic masking profile application

**Independent Test**: Grant mic → start session in varied noise → see level indicator → auto-switch profile within 30s on ambient change (quickstart Scenario 5)

### Implementation for User Story 3

- [X] T054 [P] [US3] Implement `FakeAudioAnalysisPort` for tests in `packages/audio-analysis/src/fakeAudioAnalysisPort.ts`
- [X] T055 [US3] Implement microphone capture adapter in `packages/audio-analysis/src/capture/MicCaptureAdapter.ts`
- [X] T056 [US3] Implement RMS level bucketing in `packages/audio-analysis/src/analysis/levelEstimator.ts`
- [X] T057 [US3] Implement heuristic broad-profile classifier in `packages/audio-analysis/src/analysis/profileClassifier.ts`
- [X] T058 [US3] Implement MVP `AudioAnalysisPort` orchestrator in `packages/audio-analysis/src/heuristicAnalysisPort.ts`
- [X] T059 [US3] Wire analysis start/stop to session lifecycle in `apps/mobile/src/features/analysis/analysisController.ts`
- [X] T060 [US3] Implement noise level and profile UI in `apps/mobile/src/features/analysis/NoiseIndicator.tsx`
- [X] T061 [US3] Implement auto-apply suggestion logic with debounce in `apps/mobile/src/features/analysis/autoApply.ts`
- [X] T062 [US3] Implement crossfade transition in `apps/mobile/src/services/playback/crossfade.ts`
- [X] T063 [US3] Implement manual override precedence rules in `apps/mobile/src/features/analysis/manualOverride.ts`
- [X] T064 [US3] Implement quiet-environment and permission-revoked edge handling in `apps/mobile/src/features/analysis/edgeCases.ts`
- [X] T065 [US3] Seed remote config defaults for thresholds in `apps/api/prisma/seed.ts`

**Checkpoint**: Adaptive analysis and auto-apply masking operational on-device

---

## Phase 6: User Story 4 — Sign-In and Preference Sync (Priority: P4)

**Goal**: Cross-device preference and favorites sync with offline queue and server-timestamp LWW conflicts

**Independent Test**: Two devices → offline change on B → online sync → later server timestamp wins (quickstart Scenario 7)

### Implementation for User Story 4

- [X] T066 [P] [US4] Implement preferences module service with LWW in `apps/api/src/modules/preferences/preferences.service.ts`
- [X] T067 [P] [US4] Implement favorites module service in `apps/api/src/modules/favorites/favorites.service.ts`
- [X] T068 [US4] Implement `GET/PUT /v1/preferences` in `apps/api/src/modules/preferences/preferences.controller.ts`
- [X] T069 [US4] Implement `GET/PUT /v1/favorites` in `apps/api/src/modules/favorites/favorites.controller.ts`
- [X] T070 [US4] Implement sync queue enqueue/dequeue in `apps/mobile/src/services/sync/syncQueue.ts`
- [X] T071 [US4] Implement connectivity listener and background sync worker in `apps/mobile/src/services/sync/syncWorker.ts`
- [X] T072 [US4] Implement LWW merge using `server_received_at` in `apps/mobile/src/services/sync/lwwMerge.ts`
- [X] T073 [US4] Migrate local favorites to synced model in `apps/mobile/src/features/session/favoritesSync.ts`
- [X] T074 [US4] Implement preferences pull on sign-in in `apps/mobile/src/features/settings/preferencesHydration.ts`
- [X] T075 [US4] Implement remote config fetch/cache in `apps/api/src/modules/remote-config/remote-config.controller.ts` and mobile client `apps/mobile/src/services/api/remoteConfigApi.ts`
- [X] T076 [US4] Block first-time sign-in when offline with explanatory UI in `apps/mobile/src/features/auth/OfflineSignInBlock.tsx`

**Checkpoint**: Preferences and favorites sync across devices with LWW conflict resolution

---

## Phase 7: User Story 5 — Personalization, Feedback, and Habit Building (Priority: P5)

**Goal**: EN/PT localization, theme control, session feedback for suggestion improvement

**Independent Test**: Switch language and theme → submit session feedback → verify API payload (quickstart Scenarios 8–9)

### Implementation for User Story 5

- [X] T077 [P] [US5] Configure i18next with English locale in `apps/mobile/src/i18n/locales/en.json`
- [X] T078 [P] [US5] Add Portuguese locale in `apps/mobile/src/i18n/locales/pt.json`
- [X] T079 [US5] Wire i18n provider and language switcher in `apps/mobile/src/i18n/index.ts` and `apps/mobile/src/features/settings/LanguageSetting.tsx`
- [X] T080 [P] [US5] Implement theme tokens and provider in `apps/mobile/src/theme/ThemeProvider.tsx`
- [X] T081 [US5] Implement appearance settings (light/dark/system) in `apps/mobile/src/features/settings/AppearanceSetting.tsx`
- [X] T082 [US5] Persist theme/language preferences to local storage and sync queue in `apps/mobile/src/features/settings/preferencesLocal.ts`
- [X] T083 [US5] Implement session helpfulness feedback UI in `apps/mobile/src/features/feedback/SessionFeedbackModal.tsx`
- [X] T084 [US5] Implement `POST /v1/feedback/session` in `apps/api/src/modules/feedback/feedback.controller.ts`
- [X] T085 [US5] Store feedback records in Prisma in `apps/api/src/modules/feedback/feedback.service.ts`
- [X] T086 [US5] Link feedback to session context metadata in `apps/mobile/src/features/feedback/submitFeedback.ts`

**Checkpoint**: Personalization and feedback loop complete

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Opt-in features, hardening, and end-to-end validation

- [X] T087 [P] Implement `POST /v1/features/acoustic` with consent guard in `apps/api/src/modules/features/features.controller.ts`
- [X] T088 Implement opt-in acoustic feature upload client in `apps/mobile/src/services/sync/featureUpload.ts`
- [X] T089 [P] Add experiment assignment foundation in `apps/api/src/modules/remote-config/experiments.service.ts`
- [X] T090 Implement session restore after unexpected app termination in `apps/mobile/src/features/session/sessionRestore.ts`
- [X] T091 Implement timer-expired background notification in `apps/mobile/src/features/session/timerNotification.ts`
- [X] T092 [P] Add API request logging and error filter in `apps/api/src/common/filters/http-exception.filter.ts`
- [X] T093 [P] Add mobile global error boundary in `apps/mobile/src/components/ErrorBoundary.tsx`
- [X] T094 Validate quickstart scenarios and document results in `specs/001-noise-shield-mvp/quickstart-results.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — **blocks all user stories**
- **User Stories (Phases 3–7)**: Depend on Foundational completion
- **Polish (Phase 8)**: Depends on desired user stories being complete

### User Story Dependencies

| Story | Depends on | Notes |
|-------|------------|-------|
| US1 (P1) | Foundational | Requires auth gate from Phase 2; no mic/analysis required |
| US2 (P2) | US1 session shell | Adds onboarding/permission layers atop session entry |
| US3 (P3) | US1, US2 mic grant path | Analysis attaches to active session lifecycle |
| US4 (P4) | Foundational auth | Sync extends US1 favorites and settings; can parallel US3 after US1 |
| US5 (P5) | US1 sessions | Feedback attaches to session end; i18n/theme can start after Foundational |

### Within Each User Story

- Shared types/catalog before feature wiring
- Services before screens
- API modules before mobile sync clients (US4)
- Analysis package before auto-apply UI (US3)

### Parallel Opportunities

- **Phase 1**: T002–T005, T007–T008 in parallel
- **Phase 2**: T012–T014, T021, T026–T027 in parallel; API auth (T016–T19) parallel with mobile auth (T022–T025) after T015
- **US1**: T028–T029 in parallel
- **US2**: T043, T051–T052 can overlap after T050 contract defined
- **US3**: T054–T057 in parallel
- **US4**: T066–T067 in parallel
- **US5**: T077–T078, T080 in parallel
- **Polish**: T087, T089, T092–T093 in parallel

---

## Parallel Example: User Story 1

```bash
# Parallel asset and state setup:
T028: Bundle masking audio assets in apps/mobile/assets/audio/
T029: Implement session state machine in apps/mobile/src/features/session/sessionStateMachine.ts

# Then sequential wiring:
T030 → T031 → T032 → T035–T042
```

---

## Parallel Example: User Story 3

```bash
# Parallel analysis core:
T054: FakeAudioAnalysisPort in packages/audio-analysis/src/fakeAudioAnalysisPort.ts
T055: MicCaptureAdapter in packages/audio-analysis/src/capture/MicCaptureAdapter.ts
T056: levelEstimator in packages/audio-analysis/src/analysis/levelEstimator.ts
T057: profileClassifier in packages/audio-analysis/src/analysis/profileClassifier.ts

# Integration:
T058 → T059 → T060–T064
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE** against quickstart Scenarios 1–3
5. Demo offline masking with background playback

### Incremental Delivery

1. Setup + Foundational → authenticated app shell
2. US1 → offline masking MVP
3. US2 → trust, permissions, consent
4. US3 → adaptive differentiation
5. US4 → multi-device continuity
6. US5 → polish and feedback loop
7. Polish → opt-in features and full quickstart pass

### Suggested MVP Scope

**Minimum shippable slice**: Phase 1 + Phase 2 + Phase 3 (US1) — 42 tasks (T001–T042)

Delivers: sign-in, onboarding deferred, core masking with background audio, offline playback, timer, volume, local favorites.

**Recommended beta slice**: Through US2 + US3 — adds privacy UX and adaptive masking.

---

## Notes

- All tasks include concrete file paths for LLM execution
- Auth lives in Foundational (not US4) because FR-024 blocks sessions until sign-in
- US4 focuses on sync and multi-device behavior beyond minimal auth
- Commit after each task or logical group
- Stop at any checkpoint to validate story independence per `quickstart.md`
