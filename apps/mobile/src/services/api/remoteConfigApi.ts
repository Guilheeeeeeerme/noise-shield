import { api } from './client';
import { setString, getString } from '../storage/mmkv';

const CACHE_KEY = 'remote_config_cache';

export const remoteConfigApi = {
  async fetchAndCache() {
    const config = await api.getRemoteConfig();
    setString(CACHE_KEY, JSON.stringify(config));
    return config;
  },

  getCached() {
    const raw = getString(CACHE_KEY);
    if (!raw) return null;
    return JSON.parse(raw);
  },
};
