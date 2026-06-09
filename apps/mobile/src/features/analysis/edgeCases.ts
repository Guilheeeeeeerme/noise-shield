import type { NoiseEstimate } from '@noise-shield/audio-analysis';
import { checkMicrophonePermission } from '@/services/permissions/microphone';
import { useSessionStore } from '@/stores/sessionStore';
import { stopAnalysis } from './analysisController';

export function handleQuietEnvironment(estimate: NoiseEstimate): boolean {
  if (estimate.levelBucket === 'low' && estimate.confidence < 0.5) {
    return true;
  }
  return false;
}

export async function handlePermissionRevoked(): Promise<void> {
  const granted = await checkMicrophonePermission();
  if (!granted) {
    useSessionStore.getState().setMicGranted(false);
    await stopAnalysis();
  }
}
