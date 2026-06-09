import { playbackAdapter } from './TrackPlayerAdapter';

export async function crossfadeToSound(soundId: string, durationMs = 1200): Promise<void> {
  await playbackAdapter.crossfadeTo(soundId, durationMs);
}
