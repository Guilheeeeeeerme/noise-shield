# Quickstart (Android)

1. Install Android Studio + NDK + CMake + JDK 17.
2. Open repository root; let Gradle sync (Oboe downloads via CMake FetchContent).
3. Run on device/emulator API 26+:

```bash
./gradlew :app:installDebug
```

4. Airplane mode: skip onboarding → Start → change sound/volume/timer → lock screen → confirm notification controls.
5. Deny mic → confirm limited mode banner; grant mic → confirm level/profile updates during session.
