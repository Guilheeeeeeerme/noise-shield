import { getString, setString, STORAGE_KEYS } from '@/services/storage/mmkv';
import type { MaskingSoundId } from '@noise-shield/shared';

export interface LocalFavorite {
  soundId: MaskingSoundId;
  label?: string;
  sortOrder: number;
}

export function getLocalFavorites(): LocalFavorite[] {
  const raw = getString(STORAGE_KEYS.LOCAL_FAVORITES);
  if (!raw) return [];
  return JSON.parse(raw) as LocalFavorite[];
}

export function saveLocalFavorites(favorites: LocalFavorite[]): void {
  setString(STORAGE_KEYS.LOCAL_FAVORITES, JSON.stringify(favorites));
}

export function toggleFavorite(soundId: MaskingSoundId): LocalFavorite[] {
  const favorites = getLocalFavorites();
  const index = favorites.findIndex((f) => f.soundId === soundId);
  if (index >= 0) {
    favorites.splice(index, 1);
  } else {
    favorites.push({ soundId, sortOrder: favorites.length });
  }
  saveLocalFavorites(favorites);
  return favorites;
}

export function isFavorite(soundId: MaskingSoundId): boolean {
  return getLocalFavorites().some((f) => f.soundId === soundId);
}
