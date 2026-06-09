import { MASKING_SOUNDS } from '@noise-shield/shared';

const ASSET_MAP: Record<string, number> = {
  white_noise: require('../../../assets/audio/white_noise.mp3'),
  pink_noise: require('../../../assets/audio/pink_noise.mp3'),
  brown_noise: require('../../../assets/audio/brown_noise.mp3'),
  ocean_waves: require('../../../assets/audio/ocean_waves.mp3'),
  rain: require('../../../assets/audio/rain.mp3'),
  fan: require('../../../assets/audio/fan.mp3'),
  air_conditioner: require('../../../assets/audio/air_conditioner.mp3'),
  cafe_ambience: require('../../../assets/audio/cafe_ambience.mp3'),
};

// RNTP accepts require() asset module IDs at runtime; typed as string for TS.
export function getSoundAsset(soundId: string): string {
  const sound = MASKING_SOUNDS.find((s) => s.id === soundId);
  if (!sound) throw new Error(`Unknown sound: ${soundId}`);
  return (ASSET_MAP[soundId] ?? ASSET_MAP.white_noise) as unknown as string;
}
