# Data model (local)

## Entities

- **NoiseReductionSession** (ephemeral UI/service state): playing, sound, volume, timer, limitedMode, estimate, manualOverride.
- **MaskingSound**: enum id + procedural/native buffer.
- **NoiseEstimate**: levelBucket, rmsDb, broadProfile, confidence.
- **UserPreferences** (DataStore): onboardingDone, themeMode, language, lastSound, lastVolume, favorites, lastFeedbackHelped.

No User Account, Consent Record, Remote Configuration, or server-side Feedback entities.
