# Audio Analysis Port Contract

**Version**: 1.0  
**Feature**: `001-noise-shield-mvp`

Internal contract between the mobile session layer and swappable audio engines (MVP heuristic module or future native DSP/ML module).

## Purpose

Preserve FR-021 flexibility: core session logic depends only on this port, not on a specific native implementation.

## Interface (TypeScript)

```typescript
export type BroadProfile =
  | 'fan'
  | 'traffic'
  | 'cafe'
  | 'rain'
  | 'air_conditioner'
  | 'white_noise'
  | 'unknown';

export type NoiseLevelBucket = 'low' | 'medium' | 'high';

export interface NoiseEstimate {
  levelBucket: NoiseLevelBucket;
  rmsDb: number;
  broadProfile: BroadProfile;
  confidence: number; // 0..1
  capturedAt: string; // ISO-8601
}

export interface AudioAnalysisConfig {
  windowMs: number;           // default 1000
  refreshIntervalMs: number;  // default 1000
  minConfidence: number;      // default 0.55
}

export interface AudioAnalysisPort {
  /** Start mic capture + analysis loop for an active session */
  start(config?: Partial<AudioAnalysisConfig>): Promise<void>;

  /** Stop capture; must be safe to call multiple times */
  stop(): Promise<void>;

  /** Latest estimate; null if insufficient signal or not started */
  getLatestEstimate(): NoiseEstimate | null;

  /** Subscribe to continuous estimate updates */
  onEstimate(callback: (estimate: NoiseEstimate) => void): () => void;
}
```

## Masking playback port (companion contract)

```typescript
export interface MaskingPlaybackPort {
  load(soundId: string): Promise<void>;
  play(): Promise<void>;
  pause(): Promise<void>;
  stop(): Promise<void>;
  setVolume(volume: number): Promise<void>; // 0..1
  crossfadeTo(soundId: string, durationMs: number): Promise<void>;
  getState(): 'idle' | 'playing' | 'paused' | 'stopped';
}
```

**MVP implementation**: `MaskingPlaybackPort` → `react-native-track-player` adapter.  
**Future implementation**: Native low-latency engine implementing the same interface.

## Behavioral requirements

| ID | Requirement |
|----|-------------|
| AP-001 | `start()` MUST only activate mic capture during an active user-initiated session |
| AP-002 | `stop()` MUST release mic resources when session ends |
| AP-003 | Estimates MUST be producible offline |
| AP-004 | No raw PCM leaves this port; only `NoiseEstimate` and derived features |
| AP-005 | Estimate latency ≤ 2 s from ambient change to new estimate (supports SC-010) |
| AP-006 | Adapter MUST be mockable for unit tests |

## Test doubles

Provide `FakeAudioAnalysisPort` emitting scripted estimate sequences for classifier and auto-apply integration tests.
