import TrackPlayer, { Event } from 'react-native-track-player';
import { useSessionStore } from '@/stores/sessionStore';

export function setupAudioFocusHandlers(): () => void {
  const lostSub = TrackPlayer.addEventListener(Event.RemoteDuck, async (event) => {
    if (event.paused) {
      useSessionStore.getState().dispatch({ type: 'AUDIO_FOCUS_LOST' });
      await TrackPlayer.pause();
    } else {
      useSessionStore.getState().dispatch({ type: 'AUDIO_FOCUS_GAINED' });
      await TrackPlayer.play();
    }
  });

  return () => lostSub.remove();
}
