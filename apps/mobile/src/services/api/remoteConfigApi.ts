import { api } from './client';
import { getString, STORAGE_KEYS } from '../storage/mmkv';
import { cacheRemoteConfig, getAnalysisTuning } from '../config/bundledDefaults';
import type { AnalysisTuningConfig } from '@noise-shield/shared';

export type { AnalysisTuningConfig };

/** Fetch remote config when signed in; fall back to bundled defaults. */
export async function fetchRemoteConfigIfEligible(): Promise<AnalysisTuningConfig> {
  const token = getString(STORAGE_KEYS.ACCESS_TOKEN);
  if (!token) return getAnalysisTuning();

  try {
    const config = await api.getRemoteConfig();
    cacheRemoteConfig(config.payload);
    return getAnalysisTuning();
  } catch {
    return getAnalysisTuning();
  }
}
