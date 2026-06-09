import type { BroadProfile } from '@noise-shield/shared';
import type { NoiseLevelBucket } from '@noise-shield/shared';

export interface SpectralFeatures {
  lowBandRatio: number;
  midBandRatio: number;
  highBandRatio: number;
  rmsDb: number;
  levelBucket: NoiseLevelBucket;
}

export interface ClassificationResult {
  broadProfile: BroadProfile;
  confidence: number;
}

export function classifyProfile(features: SpectralFeatures): ClassificationResult {
  const { lowBandRatio, midBandRatio, highBandRatio, levelBucket } = features;

  if (levelBucket === 'low') {
    return { broadProfile: 'unknown', confidence: 0.4 };
  }

  if (highBandRatio > 0.45 && midBandRatio < 0.3) {
    return { broadProfile: 'fan', confidence: 0.72 };
  }

  if (lowBandRatio > 0.5 && midBandRatio > 0.25) {
    return { broadProfile: 'traffic', confidence: 0.68 };
  }

  if (midBandRatio > 0.4 && highBandRatio > 0.2 && highBandRatio < 0.4) {
    return { broadProfile: 'cafe', confidence: 0.65 };
  }

  if (lowBandRatio > 0.35 && highBandRatio < 0.25) {
    return { broadProfile: 'rain', confidence: 0.7 };
  }

  if (lowBandRatio > 0.4 && highBandRatio > 0.35) {
    return { broadProfile: 'air_conditioner', confidence: 0.66 };
  }

  if (levelBucket === 'high' && midBandRatio > 0.35) {
    return { broadProfile: 'white_noise', confidence: 0.6 };
  }

  return { broadProfile: 'unknown', confidence: 0.45 };
}

export function extractBandRatios(samples: Float32Array): {
  lowBandRatio: number;
  midBandRatio: number;
  highBandRatio: number;
} {
  const third = Math.floor(samples.length / 3) || 1;
  let low = 0;
  let mid = 0;
  let high = 0;

  for (let i = 0; i < samples.length; i++) {
    const v = samples[i] * samples[i];
    if (i < third) low += v;
    else if (i < third * 2) mid += v;
    else high += v;
  }

  const total = low + mid + high || 1;
  return {
    lowBandRatio: low / total,
    midBandRatio: mid / total,
    highBandRatio: high / total,
  };
}
