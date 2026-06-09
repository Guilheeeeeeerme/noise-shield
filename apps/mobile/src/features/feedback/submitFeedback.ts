import { useSessionStore } from '@/stores/sessionStore';
import { enqueue } from '@/services/sync/syncQueue';
import { getString, setString, STORAGE_KEYS } from '@/services/storage/mmkv';

interface LocalFeedbackRecord {
  session_id: string;
  sound_id: string;
  suggested_profile: string | null;
  helpful: boolean;
  context: Record<string, unknown>;
  created_at: string;
}

export async function submitSessionFeedback(helpful: boolean): Promise<void> {
  const { sessionId, soundId, lastNoiseEstimate, manualOverride, startedAt } =
    useSessionStore.getState();

  if (!sessionId) return;

  const durationSec = startedAt
    ? Math.floor((Date.now() - new Date(startedAt).getTime()) / 1000)
    : 0;

  const record: LocalFeedbackRecord = {
    session_id: sessionId,
    sound_id: soundId,
    suggested_profile: lastNoiseEstimate?.broadProfile ?? null,
    helpful,
    context: {
      noise_level: lastNoiseEstimate?.levelBucket,
      manual_override: manualOverride,
      duration_sec: durationSec,
    },
    created_at: new Date().toISOString(),
  };

  const isAuthenticated = Boolean(getString(STORAGE_KEYS.ACCESS_TOKEN));

  if (!isAuthenticated) {
    const raw = getString(STORAGE_KEYS.LOCAL_FEEDBACK);
    const list: LocalFeedbackRecord[] = raw ? JSON.parse(raw) : [];
    list.push(record);
    setString(STORAGE_KEYS.LOCAL_FEEDBACK, JSON.stringify(list));
    return;
  }

  enqueue('feedback', record as unknown as Record<string, unknown>);
}
