import * as Network from 'expo-network';
import { dequeue, incrementRetry, remove } from './syncQueue';
import { api } from '../api/client';

const MAX_RETRIES = 5;

export async function processSyncQueue(): Promise<void> {
  const state = await Network.getNetworkStateAsync();
  if (!state.isConnected) return;

  let entry = dequeue();
  while (entry) {
    if (entry.retry_count >= MAX_RETRIES) {
      remove(entry.id);
      entry = dequeue();
      continue;
    }

    try {
      const payload = JSON.parse(entry.payload) as Record<string, unknown>;
      switch (entry.entity_type) {
        case 'preference':
          await api.putPreferences(payload.items as Array<{ key: string; value: unknown }>);
          break;
        case 'favorite':
          await api.putFavorites(payload.items as Array<{ sound_id: string }>);
          break;
        case 'feedback':
          await api.submitFeedback(payload);
          break;
        case 'consent':
          await api.putConsent(
            payload.acoustic_features_opt_in as boolean,
            payload.policy_version as string,
          );
          break;
        case 'feature':
          await api.submitAcousticFeatures(payload);
          break;
      }
      remove(entry.id);
    } catch {
      incrementRetry(entry.id);
      break;
    }
    entry = dequeue();
  }
}

export function startSyncWorker(intervalMs = 30_000): () => void {
  const timer = setInterval(() => {
    processSyncQueue().catch(console.error);
  }, intervalMs);
  processSyncQueue().catch(console.error);
  return () => clearInterval(timer);
}
