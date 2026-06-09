# Implementation Plan: Noise Shield MVP

**Branch**: `001-noise-shield-mvp` | **Date**: 2026-06-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-noise-shield-mvp/spec.md`

## Summary

Noise Shield MVP is a **local-first, privacy-first** React Native mobile app (iOS/Android) that reduces perceived environmental noise through **adaptive masking**. Signed-in users run noise reduction sessions with on-device ambient analysis, automatic profile suggestions, background playback comparable to mainstream media apps, and offline-capable masking. A NestJS/TypeScript API provides authentication, preference sync, consent management, feedback ingestion, remote configuration, and a foundation for future ML — without receiving raw microphone audio by default.

Technical approach: **pnpm monorepo** with `apps/mobile` (Expo prebuild + RNTP), `apps/api` (NestJS + PostgreSQL), and `packages/shared` + `packages/audio-analysis`. Audio capture/analysis and playback are isolated behind versioned **ports** to allow future native low-latency modules.

## Technical Context

**Language/Version**: TypeScript 5.4+; Node.js 20 LTS; React Native 0.76+ via Expo SDK 52 (development build)

**Primary Dependencies**:
- Mobile: Expo, react-native-track-player, i18next, Zustand, MMKV, expo-sqlite, provider auth SDKs (Google/Apple/Facebook)
- API: NestJS, Prisma, PostgreSQL, Zod, passport-jwt
- Shared: Zod schemas generated/aligned with OpenAPI

**Storage**: PostgreSQL (server); MMKV + SQLite sync queue (mobile); bundled audio assets (mobile)

**Testing**: Jest (unit), Supertest (API integration), Maestro or Detox (mobile E2E), contract tests against OpenAPI

**Target Platform**: iOS 15+, Android API 26+ (target SDK 34)

**Project Type**: Mobile + API monorepo

**Performance Goals**:
- Masking playback start ≤ 1 s after user taps Start (warm session)
- Noise estimate refresh ≤ 2 s per window; profile auto-apply ≤ 30 s on ambient shift (SC-010)
- Background playback stable ≥ 30 min (SC-009)
- Preference sync ≤ 5 min after reconnect (SC-003)

**Constraints**:
- Core session works offline without sign-in or network (FR-012, FR-013, FR-032, FR-033)
- No raw audio upload by default (FR-015)
- Sign-in optional; available from Settings only (FR-024 superseded, FR-033)
- Bundled analysis defaults when unsigned or offline; remote config overrides when signed in and online (FR-034)
- First sign-in discards local favorites/preferences with user warning (FR-035)
- Continuous auto-apply with smooth crossfade (FR-029, FR-030)
- English for dev artifacts; EN/PT user-facing (FR-017)

**Scale/Scope**: MVP consumer launch — single-region API, thousands of users, 8 masking sounds, 6+ noise profiles, ~15–20 mobile screens

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How design upholds it |
|-----------|--------|------------------------|
| Mobile-first | ✅ Pass | iOS/Android RN app is primary deliverable |
| Reduce perceived noise on consumer phones | ✅ Pass | Adaptive masking + future-ready audio ports |
| Local processing | ✅ Pass | Analysis and playback offline; API optional for session |
| Privacy | ✅ Pass | No raw audio upload; separate opt-in consent; minimized features |
| Low-latency audio analysis | ✅ Pass (MVP baseline) | Heuristic analyzer with native capture port; native DSP deferred with explicit extension point |
| Adaptive noise masking | ✅ Pass | Continuous refresh + auto-apply + crossfade |
| Future ML enhancements | ✅ Pass | Feature ingestion API, remote config, model version fields, AudioAnalysisPort swap |

**Post-design re-check**: No constitution violations. Complexity Tracking table not required.

## Project Structure

### Documentation (this feature)

```text
specs/001-noise-shield-mvp/
├── plan.md              # This file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/           # Phase 1
│   ├── openapi.yaml
│   └── audio-analysis-port.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
noise-shield/
├── apps/
│   ├── mobile/
│   │   ├── app/                    # Expo Router screens
│   │   ├── src/
│   │   │   ├── features/
│   │   │   │   ├── auth/
│   │   │   │   ├── onboarding/
│   │   │   │   ├── session/
│   │   │   │   ├── analysis/
│   │   │   │   ├── settings/
│   │   │   │   └── feedback/
│   │   │   ├── services/
│   │   │   │   ├── playback/       # RNTP adapter (MaskingPlaybackPort)
│   │   │   │   ├── sync/           # Offline queue + LWW client
│   │   │   │   └── api/            # Typed API client
│   │   │   ├── stores/             # Zustand session/auth/prefs
│   │   │   └── i18n/               # en, pt locales
│   │   ├── assets/audio/           # Bundled masking sounds
│   │   └── tests/
│   └── api/
│       ├── src/
│       │   ├── modules/
│       │   │   ├── auth/
│       │   │   ├── users/
│       │   │   ├── preferences/
│       │   │   ├── favorites/
│       │   │   ├── consent/
│       │   │   ├── feedback/
│       │   │   ├── features/
│       │   │   └── remote-config/
│       │   └── main.ts
│       ├── prisma/
│       └── tests/
├── packages/
│   ├── shared/                     # Zod types, constants, sound catalog
│   └── audio-analysis/             # Heuristic classifier + AudioAnalysisPort impl
├── docker-compose.yml              # PostgreSQL for local dev
├── pnpm-workspace.yaml
└── turbo.json
```

**Structure Decision**: Mobile + API monorepo (Option 3 variant). Chosen because the feature explicitly requires cross-platform mobile and a flexible TypeScript backend with shared contracts. `packages/audio-analysis` isolates DSP/classifier logic for future native replacement.

## Architecture Overview

```text
┌─────────────────────────────────────────────────────────────┐
│                     apps/mobile                              │
│  ┌──────────┐  ┌───────────────┐  ┌──────────────────────┐  │
│  │ UI flows │→ │ SessionStore  │→ │ MaskingPlaybackPort  │  │
│  └──────────┘  └───────┬───────┘  │ (react-native-track- │  │
│                        │           │  player adapter)     │  │
│                        ▼           └──────────────────────┘  │
│               ┌────────────────┐                             │
│               │ AudioAnalysis  │  mic capture (session only) │
│               │ Port (MVP JS)  │                             │
│               └────────────────┘                             │
│  ┌──────────┐  ┌───────────────┐                             │
│  │ MMKV /   │  │ Sync queue    │─── online ───┐             │
│  │ SQLite   │  │ (offline)     │              │             │
│  └──────────┘  └───────────────┘              ▼             │
└─────────────────────────────────────────────────┼─────────────┘
                                                  │
┌─────────────────────────────────────────────────▼─────────────┐
│                     apps/api (NestJS)                          │
│  Auth exchange │ Preferences LWW │ Consent │ Feedback │       │
│  Features (opt-in) │ Remote config │ Experiments (foundation)  │
│                     PostgreSQL                                 │
└───────────────────────────────────────────────────────────────┘
```

## Phase 0: Research

Completed — see [research.md](./research.md). All technical unknowns resolved; no outstanding `NEEDS CLARIFICATION` markers.

## Phase 1: Design & Contracts

| Artifact | Path | Status |
|----------|------|--------|
| Data model | [data-model.md](./data-model.md) | ✅ Complete |
| REST API contract | [contracts/openapi.yaml](./contracts/openapi.yaml) | ✅ Complete |
| Audio port contract | [contracts/audio-analysis-port.md](./contracts/audio-analysis-port.md) | ✅ Complete |
| Validation guide | [quickstart.md](./quickstart.md) | ✅ Complete |

## Implementation phases (for tasks.md)

### Phase A — Foundation
- Monorepo bootstrap, CI lint/test, Docker Postgres, Prisma schema from data model
- Shared package: sound catalog, Zod schemas, profile enums

### Phase B — API core
- Auth exchange (Google/Apple/Facebook verify → JWT)
- Preferences, favorites, consent, feedback endpoints per OpenAPI
- Remote config endpoint with seeded defaults
- Contract tests

### Phase C — Mobile shell
- Expo prebuild, navigation, theme system, i18n EN/PT
- Optional auth (Settings-only sign-in), skippable onboarding, sign-in-only consent modal
- Settings (theme, language, consent toggle, account)

### Phase D — Session core
- Session state machine (data-model transitions)
- RNTP playback adapter, volume, timer, favorites
- Background audio + lock-screen controls
- Limited/manual mode without mic

### Phase E — Adaptive analysis
- AudioAnalysisPort MVP implementation
- Heuristic classifier + continuous refresh + auto-apply crossfade
- Remote config thresholds

### Phase F — Sync & polish
- Offline sync queue, LWW client merge
- Session feedback UI + API
- Opt-in feature upload pipeline
- E2E scenarios from quickstart.md

## Complexity Tracking

> Not required — no constitution gate violations.

## Next Step

Run **`/speckit-tasks`** to generate dependency-ordered `tasks.md` from this plan and the spec.
