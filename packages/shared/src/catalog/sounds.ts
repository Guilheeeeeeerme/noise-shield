export type BroadProfile =
  | 'fan'
  | 'traffic'
  | 'cafe'
  | 'rain'
  | 'air_conditioner'
  | 'white_noise'
  | 'unknown';

export type NoiseLevelBucket = 'low' | 'medium' | 'high';

export type MaskingSoundId =
  | 'white_noise'
  | 'pink_noise'
  | 'brown_noise'
  | 'ocean_waves'
  | 'rain'
  | 'fan'
  | 'air_conditioner'
  | 'cafe_ambience';

export interface MaskingSound {
  id: MaskingSoundId;
  category: 'noise' | 'nature' | 'mechanical' | 'ambience';
  assetUri: string;
  defaultProfileMap: BroadProfile[];
  labelKey: string;
}

export const MASKING_SOUNDS: MaskingSound[] = [
  {
    id: 'white_noise',
    category: 'noise',
    assetUri: 'white_noise.mp3',
    defaultProfileMap: ['white_noise', 'unknown'],
    labelKey: 'sounds.white_noise',
  },
  {
    id: 'pink_noise',
    category: 'noise',
    assetUri: 'pink_noise.mp3',
    defaultProfileMap: ['white_noise', 'unknown'],
    labelKey: 'sounds.pink_noise',
  },
  {
    id: 'brown_noise',
    category: 'noise',
    assetUri: 'brown_noise.mp3',
    defaultProfileMap: ['white_noise', 'unknown'],
    labelKey: 'sounds.brown_noise',
  },
  {
    id: 'ocean_waves',
    category: 'nature',
    assetUri: 'ocean_waves.mp3',
    defaultProfileMap: ['rain', 'unknown'],
    labelKey: 'sounds.ocean_waves',
  },
  {
    id: 'rain',
    category: 'nature',
    assetUri: 'rain.mp3',
    defaultProfileMap: ['rain', 'unknown'],
    labelKey: 'sounds.rain',
  },
  {
    id: 'fan',
    category: 'mechanical',
    assetUri: 'fan.mp3',
    defaultProfileMap: ['fan', 'air_conditioner'],
    labelKey: 'sounds.fan',
  },
  {
    id: 'air_conditioner',
    category: 'mechanical',
    assetUri: 'air_conditioner.mp3',
    defaultProfileMap: ['air_conditioner', 'fan'],
    labelKey: 'sounds.air_conditioner',
  },
  {
    id: 'cafe_ambience',
    category: 'ambience',
    assetUri: 'cafe_ambience.mp3',
    defaultProfileMap: ['cafe', 'unknown'],
    labelKey: 'sounds.cafe_ambience',
  },
];

export const PROFILE_TO_SOUND: Record<BroadProfile, MaskingSoundId> = {
  fan: 'fan',
  traffic: 'brown_noise',
  cafe: 'cafe_ambience',
  rain: 'rain',
  air_conditioner: 'air_conditioner',
  white_noise: 'white_noise',
  unknown: 'white_noise',
};

export function getSoundById(id: MaskingSoundId): MaskingSound | undefined {
  return MASKING_SOUNDS.find((s) => s.id === id);
}
