import {
  AuthExchangeResponseSchema,
  UserSchema,
  PreferenceListResponseSchema,
  ConsentRecordSchema,
  RemoteConfigurationSchema,
} from '@noise-shield/shared';
import { getString, setString, STORAGE_KEYS } from '../storage/mmkv';

const API_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:3000/v1';

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message);
  }
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  schema?: { parse: (data: unknown) => T },
): Promise<T> {
  const token = getString(STORAGE_KEYS.ACCESS_TOKEN);
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  };
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(`${API_URL}${path}`, { ...options, headers });
  if (!res.ok) {
    const err = (await res.json().catch(() => ({}))) as { code?: string; message?: string };
    throw new ApiError(res.status, err.code ?? 'error', err.message ?? res.statusText);
  }
  const data = await res.json();
  return schema ? schema.parse(data) : (data as T);
}

export const api = {
  async exchange(provider: 'google' | 'apple' | 'facebook', idToken: string) {
    const result = await request(
      '/auth/exchange',
      {
        method: 'POST',
        body: JSON.stringify({ provider, id_token: idToken }),
      },
      AuthExchangeResponseSchema,
    );
    setString(STORAGE_KEYS.ACCESS_TOKEN, result.access_token);
    setString(STORAGE_KEYS.REFRESH_TOKEN, result.refresh_token);
    setString(STORAGE_KEYS.USER, JSON.stringify(result.user));
    return result;
  },

  async refresh() {
    const refreshToken = getString(STORAGE_KEYS.REFRESH_TOKEN);
    if (!refreshToken) throw new ApiError(401, 'no_refresh', 'No refresh token');
    const result = await request(
      '/auth/refresh',
      { method: 'POST', body: JSON.stringify({ refresh_token: refreshToken }) },
      AuthExchangeResponseSchema,
    );
    setString(STORAGE_KEYS.ACCESS_TOKEN, result.access_token);
    setString(STORAGE_KEYS.REFRESH_TOKEN, result.refresh_token);
    return result;
  },

  async getMe() {
    return request('/users/me', {}, UserSchema);
  },

  async getPreferences() {
    return request('/preferences', {}, PreferenceListResponseSchema);
  },

  async putPreferences(items: Array<{ key: string; value: unknown }>) {
    return request('/preferences', {
      method: 'PUT',
      body: JSON.stringify({ items }),
    });
  },

  async getFavorites() {
    return request('/favorites');
  },

  async putFavorites(items: Array<{ sound_id: string; label?: string; sort_order?: number }>) {
    return request('/favorites', { method: 'PUT', body: JSON.stringify({ items }) });
  },

  async getConsent() {
    return request('/consent', {}, ConsentRecordSchema);
  },

  async putConsent(acousticFeaturesOptIn: boolean, policyVersion: string) {
    return request('/consent', {
      method: 'PUT',
      body: JSON.stringify({
        acoustic_features_opt_in: acousticFeaturesOptIn,
        policy_version: policyVersion,
      }),
    }, ConsentRecordSchema);
  },

  async submitFeedback(body: Record<string, unknown>) {
    return request('/feedback/session', { method: 'POST', body: JSON.stringify(body) });
  },

  async getRemoteConfig() {
    return request('/remote-config', {}, RemoteConfigurationSchema);
  },

  async submitAcousticFeatures(body: Record<string, unknown>) {
    return request('/features/acoustic', { method: 'POST', body: JSON.stringify(body) });
  },
};
