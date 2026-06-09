export type SessionState = 'idle' | 'analyzing' | 'playing' | 'paused' | 'ended';

export type SessionEvent =
  | { type: 'START'; micGranted: boolean }
  | { type: 'ANALYSIS_READY' }
  | { type: 'PAUSE' }
  | { type: 'RESUME' }
  | { type: 'STOP' }
  | { type: 'TIMER_EXPIRED' }
  | { type: 'AUDIO_FOCUS_LOST' }
  | { type: 'AUDIO_FOCUS_GAINED' };

export function transition(state: SessionState, event: SessionEvent): SessionState {
  switch (state) {
    case 'idle':
      if (event.type === 'START') {
        return event.micGranted ? 'analyzing' : 'playing';
      }
      return state;

    case 'analyzing':
      if (event.type === 'ANALYSIS_READY') return 'playing';
      if (event.type === 'STOP' || event.type === 'TIMER_EXPIRED') return 'ended';
      return state;

    case 'playing':
      if (event.type === 'PAUSE' || event.type === 'AUDIO_FOCUS_LOST') return 'paused';
      if (event.type === 'STOP' || event.type === 'TIMER_EXPIRED') return 'ended';
      return state;

    case 'paused':
      if (event.type === 'RESUME' || event.type === 'AUDIO_FOCUS_GAINED') return 'playing';
      if (event.type === 'STOP' || event.type === 'TIMER_EXPIRED') return 'ended';
      return state;

    case 'ended':
      return state;

    default:
      return state;
  }
}

export function canStart(state: SessionState): boolean {
  return state === 'idle' || state === 'ended';
}
