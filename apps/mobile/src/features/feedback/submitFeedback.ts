import { useSessionStore } from '@/stores/sessionStore';
import { enqueue } from '@/services/sync/syncQueue';

export async function submitSessionFeedback(helpful: boolean): Promise<void> {
  const { sessionId, soundId, lastNoiseEstimate, manualOverride, startedAt } =
    useSessionStore.getState();

  if (!sessionId) return;

  const durationSec = startedAt
    ? Math.floor((Date.now() - new Date(startedAt).getTime()) / 1000)
    : 0;

  enqueue('feedback', {
    session_id: sessionId,
    sound_id: soundId,
    suggested_profile: lastNoiseEstimate?.broadProfile ?? null,
    helpful,
    context: {
      noise_level: lastNoiseEstimate?.levelBucket,
      manual_override: manualOverride,
      duration_sec: durationSec,
    },
  });
}
