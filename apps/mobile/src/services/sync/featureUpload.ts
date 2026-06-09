import { enqueue } from './syncQueue';
import { getString, STORAGE_KEYS } from '../storage/mmkv';

export function uploadAcousticFeatures(payload: Record<string, unknown>): void {
  const consentShown = getString(STORAGE_KEYS.CONSENT_SHOWN);
  if (!consentShown) return;
  enqueue('feature', payload);
}
