import type { MaskingSoundId } from '@noise-shield/shared';
import { api } from '@/services/api/client';
import { remove, STORAGE_KEYS } from '@/services/storage/mmkv';
import { saveLocalFavorites } from './favoritesLocal';

/** Pull server favorites after sign-in; local favorites already discarded (FR-035). */
export async function syncFavoritesFromServer(): Promise<void> {
  remove(STORAGE_KEYS.LOCAL_FAVORITES);
  try {
    const response = (await api.getFavorites()) as {
      items: Array<{ sound_id: string; label?: string; sort_order?: number }>;
    };
    saveLocalFavorites(
      response.items.map((item, index) => ({
        soundId: item.sound_id as MaskingSoundId,
        label: item.label,
        sortOrder: item.sort_order ?? index,
      })),
    );
  } catch {
    saveLocalFavorites([]);
  }
}
