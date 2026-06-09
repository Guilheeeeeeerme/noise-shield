import {
  BUNDLED_ANALYSIS_DEFAULTS,
  type AnalysisTuningConfig,
} from '@noise-shield/shared';
import { getString, setString, STORAGE_KEYS } from '../storage/mmkv';

export function getAnalysisTuning(): AnalysisTuningConfig {
  const cached = getString(STORAGE_KEYS.REMOTE_CONFIG);
  if (cached) {
    try {
      const parsed = JSON.parse(cached) as Partial<AnalysisTuningConfig>;
      return { ...BUNDLED_ANALYSIS_DEFAULTS, ...parsed };
    } catch {
      // fall through to bundled defaults
    }
  }
  return { ...BUNDLED_ANALYSIS_DEFAULTS };
}

export function cacheRemoteConfig(payload: Partial<AnalysisTuningConfig>): void {
  setString(STORAGE_KEYS.REMOTE_CONFIG, JSON.stringify(payload));
}

export function clearRemoteConfigCache(): void {
  setString(STORAGE_KEYS.REMOTE_CONFIG, '');
}
