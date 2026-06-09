import { api } from '@/services/api/client';
import { mergeLww } from '@/services/sync/lwwMerge';
import { setString, getString, STORAGE_KEYS } from '@/services/storage/mmkv';

export async function hydratePreferencesOnSignIn(): Promise<void> {
  try {
    const remote = await api.getPreferences();
    const localTheme = getString(STORAGE_KEYS.THEME);
    const localLang = getString(STORAGE_KEYS.LANGUAGE);

    const local: Array<{ key: string; value: unknown; server_received_at: string }> = [];
    if (localTheme) {
      local.push({ key: 'theme', value: localTheme, server_received_at: '1970-01-01T00:00:00Z' });
    }
    if (localLang) {
      local.push({ key: 'language', value: localLang, server_received_at: '1970-01-01T00:00:00Z' });
    }

    const merged = mergeLww(
      local,
      remote.items.map((item) => ({ ...item, value: item.value ?? null })),
    );

    for (const pref of merged) {
      if (pref.key === 'theme') setString(STORAGE_KEYS.THEME, String(pref.value));
      if (pref.key === 'language') setString(STORAGE_KEYS.LANGUAGE, String(pref.value));
    }
  } catch {
    // Offline — use local prefs
  }
}
