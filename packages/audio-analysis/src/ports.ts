import type { BroadProfile, NoiseLevelBucket } from '@noise-shield/shared';

export type { BroadProfile, NoiseLevelBucket };

export interface NoiseEstimate {
  levelBucket: NoiseLevelBucket;
  rmsDb: number;
  broadProfile: BroadProfile;
  confidence: number;
  capturedAt: string;
}

export interface AudioAnalysisConfig {
  windowMs: number;
  refreshIntervalMs: number;
  minConfidence: number;
}

export const DEFAULT_ANALYSIS_CONFIG: AudioAnalysisConfig = {
  windowMs: 1000,
  refreshIntervalMs: 1000,
  minConfidence: 0.55,
};

export interface AudioAnalysisPort {
  start(config?: Partial<AudioAnalysisConfig>): Promise<void>;
  stop(): Promise<void>;
  getLatestEstimate(): NoiseEstimate | null;
  onEstimate(callback: (estimate: NoiseEstimate) => void): () => void;
}

export type PlaybackState = 'idle' | 'playing' | 'paused' | 'stopped';

export interface MaskingPlaybackPort {
  load(soundId: string): Promise<void>;
  play(): Promise<void>;
  pause(): Promise<void>;
  stop(): Promise<void>;
  setVolume(volume: number): Promise<void>;
  crossfadeTo(soundId: string, durationMs: number): Promise<void>;
  getState(): PlaybackState;
}
