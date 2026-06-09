import { MMKV } from 'react-native-mmkv';

export const storage = new MMKV({ id: 'noise-shield' });

export function getString(key: string): string | undefined {
  return storage.getString(key);
}

export function setString(key: string, value: string): void {
  storage.set(key, value);
}

export function getBoolean(key: string): boolean {
  return storage.getBoolean(key) ?? false;
}

export function setBoolean(key: string, value: boolean): void {
  storage.set(key, value);
}

export function remove(key: string): void {
  storage.delete(key);
}

export const STORAGE_KEYS = {
  ACCESS_TOKEN: 'access_token',
  REFRESH_TOKEN: 'refresh_token',
  USER: 'user',
  ONBOARDING_COMPLETED: 'onboarding_completed',
  MIC_PERMISSION_EXPLAINED: 'mic_permission_explained',
  CONSENT_SHOWN: 'consent_shown',
  THEME: 'theme',
  LANGUAGE: 'language',
  LOCAL_FAVORITES: 'local_favorites',
  LOCAL_FEEDBACK: 'local_feedback',
  REMOTE_CONFIG: 'remote_config',
  HAS_SIGNED_IN: 'has_signed_in',
  ACTIVE_SESSION: 'active_session',
} as const;
