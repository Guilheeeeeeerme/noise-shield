import { api } from '@/services/api/client';
import { setString, STORAGE_KEYS } from '@/services/storage/mmkv';

/** Pull server preferences after sign-in; local prefs already discarded (FR-035). */
export async function hydratePreferencesOnSignIn(): Promise<void> {
  try {
    const remote = await api.getPreferences();
    for (const pref of remote.items) {
      if (pref.key === 'theme' && pref.value != null) {
        setString(STORAGE_KEYS.THEME, String(pref.value));
      }
      if (pref.key === 'language' && pref.value != null) {
        setString(STORAGE_KEYS.LANGUAGE, String(pref.value));
      }
    }
  } catch {
    // Offline — defaults apply until sync succeeds
  }
}
