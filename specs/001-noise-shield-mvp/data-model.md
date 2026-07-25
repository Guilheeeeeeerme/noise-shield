# Data model (local)

## Entities

- **NoiseReductionSession** (ephemeral UI/service state): playing, sound, volume, timer, limitedMode, estimate, manualOverride.
- **MaskingSound**: enum id + procedural/native buffer.
- **NoiseEstimate**: levelBucket, rmsDb, broadProfile, confidence.
- **UserPreferences** (DataStore): onboardingDone, themeMode, language, lastSound, lastVolume (unused; app gain fixed at 1.0), favorites, adaptiveModeEnabled (default true), adaptiveSwitching (mid 0.5), adaptiveFade (mid 0.5), lastFeedbackHelped. Legacy sensitivity/delay keys migrate to switching/fade.

No User Account, Consent Record, Remote Configuration, or server-side Feedback entities.
