# Research: Noise Shield MVP

**Date**: 2026-06-09  
**Feature**: `001-noise-shield-mvp`

## 1. Mobile framework and project bootstrap

**Decision**: React Native with Expo (development build / prebuild) and TypeScript strict mode.

**Rationale**: Matches stakeholder mandate. Expo accelerates iOS/Android toolchain, OTA config, and auth plugin integration while still allowing native modules required for background audio and microphone analysis via `expo prebuild` and config plugins.

**Alternatives considered**:
- **Bare React Native CLI**: Maximum native control but slower bootstrap and more manual platform maintenance.
- **Flutter**: Strong audio ecosystem but conflicts with mandated React Native/TypeScript stack.

## 2. Background masking playback (Spotify/YouTube Premium behavior)

**Decision**: `react-native-track-player` v4 with registered playback service, lock-screen/media controls, and platform background audio configuration.

**Rationale**: Purpose-built for continuous background playback on iOS and Android. Playback service keeps remote events (lock screen, headset) alive when UI is unmounted. Matches clarified requirement for media-app-style background sessions (FR-025, SC-009).

**Alternatives considered**:
- **expo-av**: Simpler but weaker background/lock-screen integration for long sessions.
- **react-native-sound**: Legacy; limited background and queue management.

**Platform notes**:
- iOS: Enable `audio` background mode in Xcode / Expo `UIBackgroundModes`.
- Android: Foreground service type `mediaPlayback` where required by target SDK.
- Register `TrackPlayer.registerPlaybackService()` at app entry per RNTP lifecycle docs.

## 3. On-device microphone capture and noise analysis (MVP)

**Decision**: Hybrid architecture — TypeScript heuristic analyzer in `packages/audio-analysis` with a thin native capture adapter (`react-native-audio-api` or Expo AV + native PCM bridge) behind a stable `AudioAnalysisPort` interface.

**Rationale**: MVP needs local RMS/level estimation and broad profile heuristics (fan, traffic, cafe, etc.) without ML dependency. Abstracting behind a port allows future swap to native low-latency DSP/ML modules (FR-021, SC-008) without rewriting session logic.

**Alternatives considered**:
- **Cloud streaming analysis**: Violates privacy and offline requirements.
- **Full native module day one**: Higher MVP cost; defer optimized DSP to post-MVP native package.

**MVP heuristic approach**:
- Compute short-window RMS → noise level bucket.
- Extract simple spectral features (band energy ratios) on-device.
- Rule-based mapping to broad profiles with confidence score.
- Continuous refresh loop (1–2 s windows) during active session; auto-apply when confidence delta exceeds threshold (FR-029, SC-010).

## 4. Smooth auto-applied profile transitions

**Decision**: Crossfade playback over 800–1500 ms when auto-switching masking tracks; debounce profile changes to avoid rapid oscillation.

**Rationale**: Clarified requirement for continuous auto-apply (Q4) requires non-jarring transitions (FR-030). Crossfade via dual-player or RNTP queue with volume ramp is standard for media apps.

**Alternatives considered**:
- **Hard cut**: Faster but fails UX acceptance for sleep/focus use cases.
- **Require user confirmation**: Rejected in clarification session.

## 5. Authentication (Google, Apple, Facebook)

**Decision**: Mobile provider SDKs (`@react-native-google-signin/google-signin`, `expo-apple-authentication`, `react-native-fbsdk-next`) exchanging provider tokens with backend; API issues Noise Shield JWT/session after verification.

**Rationale**: Avoids custom passwords (FR-005). Backend verifies provider ID tokens (Firebase Admin, Apple JWKS, Facebook Graph) and owns user record for sync/consent/feedback.

**Alternatives considered**:
- **Firebase Auth only**: Fast but couples product to Firebase user model and complicates custom backend entities.
- **Auth0**: Strong but adds cost/vendor lock for MVP.

## 6. Backend stack

**Decision**: NestJS (TypeScript) REST API with PostgreSQL (Prisma ORM), modular domains: `auth`, `users`, `preferences`, `feedback`, `consent`, `remote-config`, `features` (anonymized acoustic features).

**Rationale**: Meets JavaScript/TypeScript backend mandate and FR-014 future needs (model versioning, A/B testing, feature ingestion). NestJS modules map cleanly to bounded contexts and scale to ML serving endpoints.

**Alternatives considered**:
- **Fastify + hand-rolled structure**: Lighter but more glue code for growing domains.
- **Supabase-only backend**: Good for CRUD but less flexible for custom ML pipeline endpoints.

## 7. Local storage and offline sync

**Decision**: MMKV for fast preference cache; SQLite (expo-sqlite) for outbound sync queue and session history; last-write-wins by server receipt timestamp (FR-031, SC-011).

**Rationale**: Masking must work offline after auth (SC-002). Queue-based sync with server timestamps implements clarified conflict policy without user dialogs.

**Alternatives considered**:
- **AsyncStorage only**: Too slow and unstructured for sync queue.
- **CRDT merge**: Over-engineered for MVP preference sync.

## 8. Internationalization and theming

**Decision**: `i18next` + `react-i18next` with JSON locale files (`en`, `pt`); React Navigation native themes + user override stored in preferences (FR-017, FR-018).

**Rationale**: Standard RN pattern; easy to add locales. System theme via `useColorScheme` with manual override persisted locally/synced.

## 9. Privacy and anonymized feature collection

**Decision**: Post-onboarding separate consent modal (FR-027); transmit only derived feature vectors (no raw PCM) to `POST /v1/features/acoustic` when opted in.

**Rationale**: Aligns with clarification Q3 and constitution privacy-first vision. Feature payload schema versioned for future ML (FR-014).

## 10. Monorepo and tooling

**Decision**: pnpm workspaces + Turborepo; shared Zod schemas in `packages/shared`; ESLint/Prettier unified; Jest (mobile unit), Detox/Maestro (E2E), Supertest (API).

**Rationale**: Single repo for mobile, API, and shared contracts reduces drift between client and server validation.

**Alternatives considered**:
- **Separate repos**: Harder contract sync for small team MVP.

## 11. Remote configuration and experimentation (MVP foundation)

**Decision**: `GET /v1/remote-config` returns versioned JSON blob (feature flags, classifier thresholds, model version id); client caches with TTL; A/B assignment stored server-side per user id.

**Rationale**: Satisfies FR-014 without blocking offline core session. Enables tuning suggestion thresholds without app store release.

## Resolved technical unknowns

| Unknown | Resolution |
|---------|------------|
| Background playback library | react-native-track-player |
| Analysis MVP approach | Heuristic on-device with native capture port |
| Auth pattern | Provider SDK → backend token verify → app JWT |
| Backend framework | NestJS + PostgreSQL + Prisma |
| Offline sync conflict | Server receipt timestamp LWW |
| Consent UX timing | Separate post-onboarding prompt |
