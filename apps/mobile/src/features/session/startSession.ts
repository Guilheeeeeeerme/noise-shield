import { useSessionStore } from '@/stores/sessionStore';
import { playbackAdapter } from '@/services/playback/TrackPlayerAdapter';
import { startAnalysis, stopAnalysis } from '@/features/analysis/analysisController';

/** Start masking session — no API calls; works unsigned and offline. */
export async function startSession(): Promise<void> {
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
