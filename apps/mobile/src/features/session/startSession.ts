import { useSessionStore } from '@/stores/sessionStore';
import { playbackAdapter } from '@/services/playback/TrackPlayerAdapter';
import { useAuthStore } from '@/stores/authStore';
import { startAnalysis, stopAnalysis } from '@/features/analysis/analysisController';

/**
 * Start masking session offline-capable path — no API calls during playback.
 */
export async function startSession(): Promise<void> {
  const isAuthenticated = useAuthStore.getState().isAuthenticated;
  if (!isAuthenticated) throw new Error('Sign-in required');

  const { soundId, volume, micGranted, dispatch } = useSessionStore.getState();

  dispatch({ type: 'START', micGranted });

  await playbackAdapter.load(soundId);
  await playbackAdapter.setVolume(volume);
  await playbackAdapter.play();

  if (micGranted) {
    await startAnalysis();
    dispatch({ type: 'ANALYSIS_READY' });
  }
}

export async function stopSession(): Promise<void> {
  const { dispatch } = useSessionStore.getState();
  await stopAnalysis();
  await playbackAdapter.stop();
  dispatch({ type: 'STOP' });
}
