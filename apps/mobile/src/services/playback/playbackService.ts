import TrackPlayer, { Event } from 'react-native-track-player';

async function playbackService() {
  TrackPlayer.addEventListener(Event.RemotePlay, () => TrackPlayer.play());
  TrackPlayer.addEventListener(Event.RemotePause, () => TrackPlayer.pause());
  TrackPlayer.addEventListener(Event.RemoteStop, () => TrackPlayer.stop());
}

export function registerPlaybackService() {
  TrackPlayer.registerPlaybackService(() => playbackService);
}
