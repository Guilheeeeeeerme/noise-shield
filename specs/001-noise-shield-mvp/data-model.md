# Data Model: Noise Shield MVP

**Date**: 2026-06-09  
**Feature**: `001-noise-shield-mvp`  
**Spec**: [spec.md](./spec.md)

## Overview

Noise Shield uses **local-first storage** on mobile with **optional server persistence** for authenticated users. The backend does not store raw microphone audio. Derived anonymized features are stored only when the user has opted in.

## Entity Relationship Summary

```text
User 1──* UserPreference
User 1──* FavoriteProfile
User 1──1 ConsentRecord
User 1──* SessionFeedback
User 1──* AcousticFeatureSubmission (opt-in only)
User 1──* SyncEvent

NoiseReductionSession (primarily local) ── embeds ── NoiseEstimate snapshots
MaskingProfile (catalog + user favorites)
RemoteConfiguration (server-managed, cached client-side)
```

## Server entities (PostgreSQL)

### User

| Field | Type | Rules |
|-------|------|-------|
| id | UUID | PK |
| provider | enum | `google` \| `apple` \| `facebook` |
| provider_subject | string | unique per provider |
| email | string? | nullable, from provider when available |
| display_name | string? | nullable |
| created_at | timestamptz | required |
| updated_at | timestamptz | required |
| last_login_at | timestamptz | required |

**Uniqueness**: `(provider, provider_subject)` unique.

### UserPreference

| Field | Type | Rules |
|-------|------|-------|
| id | UUID | PK |
| user_id | UUID | FK → User |
| key | string | e.g. `theme`, `language`, `default_volume` |
| value | jsonb | schema per key |
| server_received_at | timestamptz | set on write; used for LWW conflict resolution |
| client_updated_at | timestamptz | audit only |

**Uniqueness**: `(user_id, key)` unique.  
**Conflict rule**: Latest `server_received_at` wins (FR-031).

**Known keys (MVP)**:
- `theme`: `system` \| `light` \| `dark`
- `language`: `en` \| `pt`
- `default_volume`: number 0–1
- `onboarding_completed`: boolean
- `mic_permission_explained`: boolean

### FavoriteProfile

| Field | Type | Rules |
|-------|------|-------|
| id | UUID | PK |
| user_id | UUID | FK → User |
| sound_id | string | references masking catalog id |
| label | string? | user custom label |
| sort_order | int | default 0 |
| server_received_at | timestamptz | LWW |
| created_at | timestamptz | required |

**Uniqueness**: `(user_id, sound_id)` unique.

### ConsentRecord

| Field | Type | Rules |
|-------|------|-------|
| user_id | UUID | PK, FK → User |
| acoustic_features_opt_in | boolean | default false |
| consented_at | timestamptz? | set when opted in |
| revoked_at | timestamptz? | set when revoked |
| policy_version | string | e.g. `2026-06-09` |
| server_received_at | timestamptz | LWW |

**Rule**: No `AcousticFeatureSubmission` unless `acoustic_features_opt_in = true` (FR-028).

### SessionFeedback

| Field | Type | Rules |
|-------|------|-------|
| id | UUID | PK |
| user_id | UUID | FK → User |
| session_id | string | client-generated UUID |
| sound_id | string | masking sound at feedback time |
| suggested_profile | string? | classifier label if auto-suggested |
| helpful | boolean | user rating |
| submitted_at | timestamptz | required |
| context | jsonb | `{ noise_level, manual_override, duration_sec }` |

### AcousticFeatureSubmission (opt-in only)

| Field | Type | Rules |
|-------|------|-------|
| id | UUID | PK |
| user_id | UUID | FK → User (pseudonymous bucket allowed at ingest) |
| session_id | string | client session reference |
| feature_schema_version | string | e.g. `1.0` |
| features | jsonb | derived vectors only — no raw audio |
| broad_profile_label | string? | local classifier output |
| captured_at | timestamptz | required |

**Prohibited fields**: raw PCM, audio URLs, reversible voice fingerprints.

### RemoteConfiguration

| Field | Type | Rules |
|-------|------|-------|
| id | string | PK e.g. `production` |
| version | int | monotonic |
| payload | jsonb | flags, thresholds, model_version |
| published_at | timestamptz | required |

### ExperimentAssignment (MVP foundation)

| Field | Type | Rules |
|-------|------|-------|
| user_id | UUID | PK, FK → User |
| experiment_key | string | |
| variant | string | |
| assigned_at | timestamptz | required |

## Client-local entities

### NoiseReductionSession (local)

| Field | Type | Rules |
|-------|------|-------|
| id | UUID | client-generated |
| state | enum | `idle` \| `analyzing` \| `playing` \| `paused` \| `ended` |
| sound_id | string | current masking sound |
| volume | number | 0–1 |
| timer_end_at | datetime? | nullable |
| started_at | datetime | required |
| ended_at | datetime? | |
| manual_override | boolean | true when user picked sound manually |
| last_noise_estimate | NoiseEstimate? | embedded snapshot |

**State transitions**:
- `idle` → `analyzing` (session start + mic granted)
- `idle` → `playing` (session start, limited/manual mode or skip analysis)
- `analyzing` → `playing` (first suggestion applied)
- `playing` ↔ `paused` (user or audio focus interruption)
- `playing` → `ended` (timer, user stop, or fatal error)
- `ended` → `idle` (cleanup)

### NoiseEstimate (local snapshot)

| Field | Type | Rules |
|-------|------|-------|
| level_bucket | enum | `low` \| `medium` \| `high` |
| rms_db | number | derived locally |
| broad_profile | string | fan, traffic, cafe, rain, air_conditioner, white_noise, unknown |
| confidence | number | 0–1 |
| captured_at | datetime | required |

### MaskingSound (catalog — bundled)

| Field | Type | Rules |
|-------|------|-------|
| id | string | e.g. `white_noise` |
| category | string | noise, nature, mechanical, ambience |
| asset_uri | string | local bundled file |
| default_profile_map | string[] | profiles this sound matches |

**MVP catalog ids**: `white_noise`, `pink_noise`, `brown_noise`, `ocean_waves`, `rain`, `fan`, `air_conditioner`, `cafe_ambience`.

### SyncQueueEntry (local SQLite)

| Field | Type | Rules |
|-------|------|-------|
| id | UUID | |
| entity_type | string | `preference`, `favorite`, `feedback`, `consent`, `feature` |
| payload | json | outbound body |
| created_at | datetime | FIFO processing |
| retry_count | int | max 5 |

## Validation rules (selected)

1. **Sign-in gate**: `NoiseReductionSession` cannot transition out of `idle` into active states unless `AuthSession.valid = true` (FR-024).
2. **Mic denied**: `analyzing` state disabled; `manual_override` defaults true in limited mode (FR-007).
3. **Consent gate**: `SyncQueueEntry` of type `feature` is dropped if `ConsentRecord.acoustic_features_opt_in` is false (FR-028).
4. **Auto-apply debounce**: New `broad_profile` auto-applies only if confidence ≥ threshold AND differs from current profile AND debounce window elapsed (FR-029, FR-030).
5. **Timer expiry in background**: Transition to `ended`, stop playback, enqueue local notification (FR-025).

## Indexing (server)

- `UserPreference(user_id, key)` unique
- `SessionFeedback(user_id, submitted_at desc)`
- `AcousticFeatureSubmission(captured_at)` for batch ML export
- `User(provider, provider_subject)` unique
