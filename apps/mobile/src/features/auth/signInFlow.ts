import { remove, setBoolean, getBoolean, STORAGE_KEYS } from '@/services/storage/mmkv';
import { useAuthStore } from '@/stores/authStore';
import { hydratePreferencesOnSignIn } from '@/features/settings/preferencesHydration';
import { syncFavoritesFromServer } from '@/features/session/favoritesSync';
import { fetchRemoteConfigIfEligible } from '@/services/api/remoteConfigApi';

export function isFirstSignIn(): boolean {
  return !getBoolean(STORAGE_KEYS.HAS_SIGNED_IN);
}

export function discardLocalData(): void {
  remove(STORAGE_KEYS.LOCAL_FAVORITES);
  remove(STORAGE_KEYS.THEME);
  remove(STORAGE_KEYS.LANGUAGE);
  remove(STORAGE_KEYS.LOCAL_FEEDBACK);
}

export async function executeSignIn(
  provider: 'google' | 'apple' | 'facebook',
  idToken: string,
): Promise<void> {
  discardLocalData();
  await useAuthStore.getState().signIn(provider, idToken);
  setBoolean(STORAGE_KEYS.HAS_SIGNED_IN, true);
  await hydratePreferencesOnSignIn();
  await syncFavoritesFromServer();
  await fetchRemoteConfigIfEligible();
}
