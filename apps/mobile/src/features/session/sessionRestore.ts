import { getString, setString, STORAGE_KEYS } from '@/services/storage/mmkv';
import { useSessionStore } from '@/stores/sessionStore';
import { playbackAdapter } from '@/services/playback/TrackPlayerAdapter';

interface PersistedSession {
  sessionId: string;
  soundId: string;
  volume: number;
  state: string;
}

export async function restoreSessionIfNeeded(): Promise<void> {
  const raw = getString(STORAGE_KEYS.ACTIVE_SESSION);
  if (!raw) return;

  try {
    const persisted = JSON.parse(raw) as PersistedSession;
    if (persisted.state !== 'playing' && persisted.state !== 'paused') return;

    const store = useSessionStore.getState();
    store.setSound(persisted.soundId as Parameters<typeof store.setSound>[0]);
    store.setVolume(persisted.volume);

    await playbackAdapter.load(persisted.soundId);
    await playbackAdapter.setVolume(persisted.volume);
    if (persisted.state === 'playing') {
      await playbackAdapter.play();
    }
  } catch {
    // Corrupt state — ignore
  }
}

export function persistActiveSession(): void {
  const { sessionId, soundId, volume, state } = useSessionStore.getState();
  if (!sessionId || state === 'idle' || state === 'ended') return;

  setString(STORAGE_KEYS.ACTIVE_SESSION, JSON.stringify({
    sessionId,
    soundId,
    volume,
    state,
  }));
}
