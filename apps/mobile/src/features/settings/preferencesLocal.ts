import { enqueue } from '@/services/sync/syncQueue';
import { setString, STORAGE_KEYS } from '@/services/storage/mmkv';

export function saveLocalPreference(key: string, value: unknown): void {
  if (key === 'theme') setString(STORAGE_KEYS.THEME, String(value));
  if (key === 'language') setString(STORAGE_KEYS.LANGUAGE, String(value));

  enqueue('preference', {
    items: [{ key, value, client_updated_at: new Date().toISOString() }],
  });
}
