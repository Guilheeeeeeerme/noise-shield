# Implementation Plan: Noise Shield MVP (Android)

**Branch**: `001-noise-shield-mvp` | **Date**: 2026-07-25 | **Spec**: [spec.md](./spec.md)

## Summary

Noise Shield MVP is a **local-first, privacy-first, Android-only** app that reduces perceived environmental noise through **adaptive masking**. Users run noise reduction sessions with on-device ambient analysis (Oboe mic capture + heuristic classifier), automatic profile suggestions, background playback, and fully offline masking. There is **no backend**, **no sign-in**, and **no cloud sync**.

Technical approach: Gradle project with `app` (Kotlin + Jetpack Compose) and `audio-engine` (C++ Oboe via NDK/JNI). Procedural looping masks ship in the native engine; optional asset PCM can be loaded via JNI.

## Technical Context

**Language/Version**: Kotlin 2.x; C++17 (NDK); JDK 17

**Primary Dependencies**:
- App: Jetpack Compose, Material 3, Navigation, DataStore, Coroutines, AppCompat locales
- Audio: Google Oboe 1.9 (CMake FetchContent), JNI bridge

**Storage**: DataStore preferences only (onboarding, theme, language, favorites, volume/sound, local feedback)

**Target Platform**: Android API 26+ (target SDK 35)

**Project Type**: Native Android (single product module + NDK library)

**Performance Goals**:
- Masking playback start ≤ 1 s after Start (warm)
- Noise estimate refresh ≤ 2 s; profile auto-apply ≤ 30 s on ambient shift
- Background playback stable ≥ 30 min
- Zero network dependency for any core path

**Constraints**:
- Core session works offline with no account
- No raw audio upload (no upload path exists)
- Bundled/procedural analysis defaults only
- Continuous auto-apply with smooth crossfade
- English + Portuguese UI

## Project Structure

```text
noise-shield/
├── app/                 # Compose UI, ViewModel, foreground service
├── audio-engine/        # Oboe player, mic capture, heuristic analyzer, JNI
├── specs/001-noise-shield-mvp/
└── README.md
```

## Constitution Check

| Principle | Status |
|-----------|--------|
| Mobile-first (Android) | Pass |
| Reduce perceived noise | Pass |
| Local processing | Pass |
| Privacy | Pass |
| Low-latency audio | Pass (Oboe) |
| Adaptive masking | Pass |
| Future ML | Pass (JNI/engine swap point) |
