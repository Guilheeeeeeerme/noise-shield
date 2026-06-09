import { getLocalFavorites, saveLocalFavorites } from './favoritesLocal';
import { api } from '@/services/api/client';
import { enqueue } from '@/services/sync/syncQueue';

export async function syncFavoritesToServer(): Promise<void> {
  const local = getLocalFavorites();
  const items = local.map((f, i) => ({
    sound_id: f.soundId,
    label: f.label,
    sort_order: f.sortOrder ?? i,
  }));

  try {
    const remote = (await api.getFavorites()) as {
      items: Array<{ sound_id: string; label?: string; sort_order?: number }>;
    };
    const merged = new Map<string, { sound_id: string; label?: string; sort_order: number }>();

    for (const f of remote.items) {
      merged.set(f.sound_id, { ...f, sort_order: f.sort_order ?? 0 });
    }
    for (const f of items) {
      if (!merged.has(f.sound_id)) merged.set(f.sound_id, { ...f, sort_order: f.sort_order ?? 0 });
    }

    saveLocalFavorites(
      Array.from(merged.values()).map((f) => ({
        soundId: f.sound_id as Parameters<typeof saveLocalFavorites>[0][0]['soundId'],
        label: f.label,
        sortOrder: f.sort_order,
      })),
    );
  } catch {
    enqueue('favorite', { items });
  }
}
