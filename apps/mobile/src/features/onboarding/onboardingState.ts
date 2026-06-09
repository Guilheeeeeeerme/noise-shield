import { getBoolean, setBoolean, STORAGE_KEYS } from '@/services/storage/mmkv';

export function getOnboardingCompleted(): boolean {
  return getBoolean(STORAGE_KEYS.ONBOARDING_COMPLETED);
}

export function setOnboardingCompleted(value: boolean): void {
  setBoolean(STORAGE_KEYS.ONBOARDING_COMPLETED, value);
}
