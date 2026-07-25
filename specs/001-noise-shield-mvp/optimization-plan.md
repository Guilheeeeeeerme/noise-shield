# Noise Shield Production-Grade Optimization

## Audit verdict

Current repository is an incomplete prototype, not a verified MVP.

| Feature | Current implementation | Selected alternative | Decision |
|---|---|---|---|
| Build | Invalid Compose `Modifier` imports | Restore a compiling baseline first | Replace |
| Playback lifecycle | Custom sticky/bound service with split state | Media3 `MediaSessionService` owning playback state | Replace |
| Native audio | Mutexes inside Oboe callbacks; no recovery | Lock-free callbacks and asynchronous restart | Rework |
| Sounds | Unused placeholder MP3s and crude four-second loops | Procedural colored noise plus seamless Ogg natural loops | Replace |
| Analysis | Temporal thirds incorrectly treated as frequency bands | FFT/mel spectral matching designed for masking | Replace |
| ML alternative | None | YAMNet adds unnecessary model and CPU overhead for this use case | Reject |
| Auto-adaptation | Unstable hard-coded classifications | Smoothed scoring, hysteresis, cooldown, adaptive toggle | Replace |
| Background microphone | Capture can outlive the foreground UI | Foreground-UI capture only | Replace |
| Timer | ViewModel countdown | Service-owned absolute deadline | Replace |
| Audio focus | Delayed/transient states mishandled | Complete focus state machine | Replace |
| Persistence | Preferences DataStore | Keep, debounce, validate, and remove duplicate locale state | Keep |
| UI | Compose and Material 3 | Keep, repair accessibility and localization | Keep |
| Safety | Linear gain only | Perceptual volume, ramps, warnings, break reminder | Add |
| Startup | Native stream opened at application startup | Lazy initialization plus measured Baseline Profile | Replace |

Build verification was blocked because the inspection environment had no JDK or
`JAVA_HOME`. Static inspection confirmed three compile-blocking imports.

## Target architecture and interfaces

- Target Android 16/API 36, retain minSdk 26, and preserve all eight sound IDs.
- Use stable Media3 1.10.1, Oboe 1.9.3 through Maven/Prefab, DataStore 1.2.1,
  Lifecycle 2.11.0, Navigation 2.9.8, Compose 1.11.4, and Material 3 1.4.0.
  Upgrade AGP, Kotlin, and Gradle to their latest mutually compatible stable set.
- Replace `MaskingPlaybackService` with a `MediaSessionService`. A
  `SimpleBasePlayer` adapter wraps the native engine and exposes sounds as media
  items whose IDs equal the existing `MaskingSoundId` names.
- The service owns play/pause, selected sound, effective volume, audio focus,
  timer deadline, adaptive mode, and engine errors. The ViewModel combines
  `MediaController` state with preferences.
- Use standard player commands for playback, sound selection, and volume. Add
  custom commands for `SET_TIMER`, `SET_ADAPTIVE_MODE`, and analysis events.
- Replace the JNI estimate payload with:

```text
NoiseAnalysis(
  relativeDbfs,
  levelBucket,
  suggestedSoundId,
  confidence,
  melBandEnergies,
  capturedAtElapsedRealtime
)
```

- Present relative ambient level and a suggested mask, never an uncalibrated
  SPL or an unsupported claim that a particular environmental source was
  detected.
- Preserve existing preferences. Add `adaptiveModeEnabled=true`, safety-warning
  acknowledgement, and per-sound feedback counters. AppCompat locale storage
  becomes the sole language source.

## Implementation sequence

### 1. Restore a trustworthy baseline

- Configure JDK 17, repair invalid imports, and build debug plus minified release
  variants for all configured ABIs.
- Correct the completion checklist so it reports verified behavior only.
- Add a version catalog and dependency locking. Remove Oboe `FetchContent`.
- Raise compile/target SDK to 36 while retaining API 26 compatibility.

### 2. Centralize session ownership

- Introduce `MediaSessionService` and `NativeMaskingPlayer`; remove bound-service
  calls and static running state.
- Move the timer to the service as an `elapsedRealtime` deadline. Run it only
  during playback.
- Implement delayed focus, ducking, transient loss/resume, and permanent loss.
- Start foreground playback only from a user action. Stop microphone capture
  when the app UI leaves the foreground. Release playback resources on stop or
  after 30 seconds paused.
- Request notification permission when background playback is first used, not
  at application launch.

### 3. Replace audio-engine internals

- Remove mutexes, allocations, JNI calls, logging, and stream lifecycle work
  from Oboe callbacks.
- Use preallocated SPSC command/audio rings. Decode on worker threads and
  publish immutable buffer slots to the callback.
- Prefer exclusive low-latency output, with shared fallback. Initially leave
  sample rate and format unspecified, inspect actual stream properties, and
  resample away from the callback only when necessary.
- Prefer `UNPROCESSED` microphone input, then `GENERIC`; remove
  `VoicePerformance`.
- Recover disconnections and route changes outside callbacks.
- Use 750 ms equal-power sound crossfades and 150 ms start, stop, and volume
  ramps. Map slider position perceptually to gain.
- Remove `-ffast-math`.

### 4. Install the hybrid sound library

- Generate white, pink, and brown noise continuously with stable filters.
- Replace placeholder ocean, rain, fan, AC, and café files with owned or CC0
  seamless mono Ogg loops: 48 kHz, 30–60 seconds, normalized consistently, true
  peak at or below −3 dBFS.
- Store source, license, and checksum metadata with every asset.
- Lazy-decode only the current and next sounds into preallocated 16-bit buffers.
- Precompute a 24-band mel spectrum for each mask at build time.

### 5. Replace ambient classification with masking-oriented DSP

- Resample capture to 16 kHz on an analysis worker.
- Process 2,048-sample Hann windows at 50% overlap.
- Compute RMS dBFS, 24 mel-band energies from 80–8,000 Hz, spectral centroid,
  flatness, and low/mid/high ratios.
- Compare the smoothed ambient spectrum with stored mask templates and select
  the mask offering the best spectral coverage. Do not run a general scene
  classifier.
- Update once per second with a three-second EMA. Auto-switch only when a
  candidate beats the active score by at least 0.10 for three updates, followed
  by a 15-second cooldown.
- Keep a manual selection until normalized spectral distance from its override
  baseline exceeds 0.25 for five seconds.
- Never raise volume automatically. Low confidence retains the active sound.

### 6. Repair product behavior and UI

- Add an adaptive-mode toggle, default enabled. Disabled mode may suggest but
  never switch sounds.
- Debounce volume persistence by 300 ms and clamp stored values.
- Show favorites first and separate sound-selection and favorite click targets.
- Localize sound, analysis, notification, accessibility, error, and safety
  labels in English and Portuguese.
- Represent initialization, permission, capture, focus, and recovery states.
- Disable cloud backup or explicitly exclude Noise Shield DataStore content.
- Add a 30% default volume, smooth ramps, a warning when app or system media
  volume exceeds 70%, and a dismissible break reminder after 60 continuous
  minutes. Do not claim exact dBA.

### 7. Optimize after correctness

- Add a Baseline/Startup Profile for launch, onboarding, session start, sound
  switching, and settings.
- Measure release builds with Perfetto, Macrobenchmark, Oboe xrun counters, CPU,
  memory, and battery.
- Remove dead assets, dependencies, JNI methods, and unused extended icons.

## Verification and acceptance

- Native tests: colored-noise spectra, FFT/mel extraction, ring wrapping,
  equal-power crossfades, gain clamping, and corrupt inputs.
- Golden audio cases: silence, broadband noise, low-frequency traffic-like
  noise, speech/café mixture, rain, fan, and AC.
- State tests: playback, timer, every focus transition, ducking, manual
  override, adaptive cooldown, controller reconnection, and service teardown.
- Instrumented coverage on API 26, 33, 35, and 36: permission outcomes,
  rotation, Activity recreation, screen lock, task removal, route changes,
  airplane mode, and 30-minute background playback.
- Validate TalkBack, 200% font scale, narrow screens, both languages, and all
  themes.
- Release gates:
  - Debug and minified release builds succeed for every configured ABI.
  - Warm playback starts within one second; sound switching begins within
    100 ms.
  - Audio callbacks allocate nothing and take no blocking synchronization.
  - Thirty-minute playback and repeated route changes produce no audible
    discontinuity or unexpected xrun increase.
  - Analysis refreshes within two seconds and respects stability/cooldown rules.
  - Microphone closes within one second of backgrounding or stopping playback.
  - No network permission, raw-audio persistence, backup, upload, account, or
    analytics path exists.
  - APK remains below 15 MB and steady-state PSS below 80 MB.

## Assumptions

- Optimize for balanced quality, battery, package size, and reliability.
- Remain offline-only, account-free, analytics-free, and non-medical.
- Preserve API 26 support, all sound IDs, favorites, theme, language, and saved
  selection.
- Use Media3 for service/session/notification integration while retaining Oboe
  as the renderer.
- Research basis: official Android Media3, audio-focus, foreground-service,
  Baseline Profile, and Play API-level guidance; Oboe documentation; official
  YAMNet/TFLite material; and WHO-ITU safe-listening guidance.
