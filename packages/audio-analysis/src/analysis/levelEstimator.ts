import type { NoiseLevelBucket } from '@noise-shield/shared';

const LOW_THRESHOLD_DB = -40;
const HIGH_THRESHOLD_DB = -20;

export function rmsToDb(rms: number): number {
  if (rms <= 0) return -Infinity;
  return 20 * Math.log10(rms);
}

export function bucketizeLevel(rmsDb: number): NoiseLevelBucket {
  if (rmsDb < LOW_THRESHOLD_DB) return 'low';
  if (rmsDb > HIGH_THRESHOLD_DB) return 'high';
  return 'medium';
}

export function computeRms(samples: Float32Array): number {
  if (samples.length === 0) return 0;
  let sum = 0;
  for (let i = 0; i < samples.length; i++) {
    sum += samples[i] * samples[i];
  }
  return Math.sqrt(sum / samples.length);
}
