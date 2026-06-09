import TrackPlayer, {
  Capability,
  State,
  RepeatMode,
  AppKilledPlaybackBehavior,
} from 'react-native-track-player';
import type { MaskingPlaybackPort, PlaybackState } from '@noise-shield/audio-analysis';
import { getSoundAsset } from './soundAssets';

let initialized = false;

export class TrackPlayerAdapter implements MaskingPlaybackPort {
  async ensureSetup(): Promise<void> {
    if (initialized) return;
    await TrackPlayer.setupPlayer();
    await TrackPlayer.updateOptions({
      capabilities: [Capability.Play, Capability.Pause, Capability.Stop],
      compactCapabilities: [Capability.Play, Capability.Pause],
      android: {
        appKilledPlaybackBehavior: AppKilledPlaybackBehavior.ContinuePlayback,
      },
    });
    await TrackPlayer.setRepeatMode(RepeatMode.Track);
    initialized = true;
  }

  async load(soundId: string): Promise<void> {
    await this.ensureSetup();
    await TrackPlayer.reset();
    const asset = getSoundAsset(soundId);
    await TrackPlayer.add({
      id: soundId,
      url: asset,
      title: soundId,
      artist: 'Noise Shield',
    });
  }

  async play(): Promise<void> {
    await TrackPlayer.play();
  }

  async pause(): Promise<void> {
    await TrackPlayer.pause();
  }

  async stop(): Promise<void> {
    await TrackPlayer.stop();
    await TrackPlayer.reset();
  }

  async setVolume(volume: number): Promise<void> {
    await TrackPlayer.setVolume(Math.max(0, Math.min(1, volume)));
  }

  async crossfadeTo(soundId: string, durationMs: number): Promise<void> {
    const steps = 10;
    const stepMs = durationMs / steps;
    const currentVol = (await TrackPlayer.getVolume()) ?? 1;

    for (let i = steps; i >= 0; i--) {
      await TrackPlayer.setVolume((currentVol * i) / steps);
      await new Promise((r) => setTimeout(r, stepMs));
    }

    await this.load(soundId);
    await TrackPlayer.play();

    for (let i = 0; i <= steps; i++) {
      await TrackPlayer.setVolume((currentVol * i) / steps);
      await new Promise((r) => setTimeout(r, stepMs));
    }
  }

  getState(): PlaybackState {
    return 'idle';
  }

  async getPlayerState(): Promise<PlaybackState> {
    const state = await TrackPlayer.getState();
    switch (state) {
      case State.Playing:
        return 'playing';
      case State.Paused:
        return 'paused';
      case State.Stopped:
        return 'stopped';
      default:
        return 'idle';
    }
  }
}

export const playbackAdapter = new TrackPlayerAdapter();
