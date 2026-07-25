# Noise Shield

Android-only, offline-first app that reduces perceived environmental noise through **adaptive masking**.

All audio capture, analysis, and playback run on-device via a **C++ Oboe** engine. No account, no backend, no network required.

## Stack

| Layer | Tech |
|-------|------|
| UI | Kotlin, Jetpack Compose, Material 3 |
| Audio | Google Oboe (NDK), JNI |
| Storage | DataStore (prefs, favorites, local feedback) |
| Background | Foreground media-playback service |

## Modules

```
app/            Compose UI, session, settings, media service
audio-engine/   Oboe player + mic capture + heuristic classifier
```

## Prerequisites

- Android Studio Ladybug (or newer) with NDK + CMake
- JDK 17
- Android device or emulator (API 26+)

## Build & run

1. Open this folder in Android Studio (Gradle sync will FetchContent Oboe).
2. Or from CLI (with `ANDROID_HOME` set):

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

## Core features

- Masking session: 8 procedural loops (white/pink/brown, ocean, rain, fan, AC, cafe), volume, timer
- Smooth crossfade when switching sounds or auto-applying a suggestion
- On-device mic analysis during an active session; limited mode if mic denied
- Background playback with notification controls
- Local favorites, EN/PT, light/dark/system theme
- Skippable onboarding; no medical/ANC claims

## Privacy

- Raw microphone audio never leaves the device
- No cloud sync or analytics backend in this build
