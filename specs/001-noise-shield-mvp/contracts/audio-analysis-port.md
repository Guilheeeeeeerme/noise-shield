# Audio Engine Contract (JNI / Oboe)

**Version**: 2.0  
**Feature**: `001-noise-shield-mvp`

Contract between the Kotlin session layer and the native `noise_shield_audio` library.

## NativeAudioEngine (Kotlin)

| Method | Behavior |
|--------|----------|
| `init()` | Open Oboe output stream; synthesize default loops |
| `release()` | Stop player + capture; close streams |
| `setPlaying(Boolean)` | Gate output callback |
| `setVolume(Float)` | 0..1 linear gain |
| `setSound(soundId, crossfadeSeconds)` | Crossfade to sound 0..7 |
| `loadPcm(soundId, samples, sampleRate)` | Optional override buffer |
| `startCapture()` / `stopCapture()` | Oboe input @ 16 kHz |
| `pollEstimate()` | `float[4]?` = `[levelBucket, rmsDb, broadProfile, confidence]` |

## Enums

- **SoundId**: white_noise=0 … cafe_ambience=7
- **LevelBucket**: low=0, medium=1, high=2
- **BroadProfile**: fan, traffic, cafe, rain, air_conditioner, white_noise, unknown

## Analysis

Heuristic ports prior TypeScript classifier: windowed RMS → dB bucket; crude third-band energy ratios → profile. Estimates below confidence 0.55 are suppressed.

## Playback

Mono float output @ 48 kHz, low-latency Oboe. Crossfade between current/target loops. Procedural synth provides offline loops; PCM load optional.
