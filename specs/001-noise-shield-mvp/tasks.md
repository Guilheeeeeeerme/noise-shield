# Tasks — Android local MVP

## Implemented

- [x] Android API 26–36 app + Oboe/Media3 architecture
- [x] Lazy native playback, 750 ms equal-power switching, perceptual gain, 150 ms ramps
- [x] MediaSessionService ownership, full audio-focus handling, service timer
- [x] Foreground-UI-only microphone capture and asynchronous route recovery
- [x] Continuous colored noise plus owned 48 kHz/30-second mono Ogg loops
- [x] 2,048-point FFT, 24-band mel masking analysis, EMA/hysteresis/cooldown
- [x] Adaptive toggle, favorites ordering, volume persistence debounce
- [x] Local preferences, EN/PT resources, AppCompat locale storage
- [x] Backup disabled, no network/account/analytics/raw-audio persistence path
- [x] Safety warning, 60-minute break reminder, baseline/startup profiles

## Verification still required

- [ ] Android Studio debug and minified release builds for configured ABIs
- [ ] Device matrix and accessibility checks from `optimization-plan.md`
- [ ] Perfetto/Macrobenchmark, Oboe xrun, CPU, memory, battery, APK-size measurements
- [ ] Thirty-minute playback and repeated route-change acceptance run
