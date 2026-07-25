# Data model (local)

## Entities

- **NoiseReductionSession** (ephemeral UI/service state): playing, sound, volume, timer, limitedMode, estimate, manualOverride.
- **MaskingSound**: enum id + procedural/native buffer.
- **NoiseEstimate**: levelBucket, rmsDb, broadProfile, confidence.
- **UserPreferences** (DataStore): onboardingDone, themeMode, language, lastSound, lastVolume, favorites, adaptiveSensitivity (0=Off), adaptiveDelay, lastFeedbackHelped. Legacy `adaptive_mode_enabled` migrates to sensitivity 0/0.5.

No User Account, Consent Record, Remote Configuration, or server-side Feedback entities.
