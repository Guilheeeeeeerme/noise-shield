import { create } from 'zustand';
import type { MaskingSoundId } from '@noise-shield/shared';
import type { SessionState } from '@/features/session/sessionStateMachine';
import { transition } from '@/features/session/sessionStateMachine';
import type { NoiseEstimate } from '@noise-shield/audio-analysis';

interface SessionStore {
  state: SessionState;
  sessionId: string | null;
  soundId: MaskingSoundId;
  volume: number;
  timerEndAt: string | null;
  startedAt: string | null;
  manualOverride: boolean;
  lastNoiseEstimate: NoiseEstimate | null;
  micGranted: boolean;
  limitedMode: boolean;

  setMicGranted: (granted: boolean) => void;
  setSound: (soundId: MaskingSoundId, manual?: boolean) => void;
  setVolume: (volume: number) => void;
  setTimer: (endAt: string | null) => void;
  setNoiseEstimate: (estimate: NoiseEstimate | null) => void;
  dispatch: (event: Parameters<typeof transition>[1]) => void;
  reset: () => void;
}

const DEFAULT_SOUND: MaskingSoundId = 'white_noise';

export const useSessionStore = create<SessionStore>((set, get) => ({
  state: 'idle',
  sessionId: null,
  soundId: DEFAULT_SOUND,
  volume: 0.7,
  timerEndAt: null,
  startedAt: null,
  manualOverride: false,
  lastNoiseEstimate: null,
  micGranted: false,
  limitedMode: false,

  setMicGranted: (granted) =>
    set({ micGranted: granted, limitedMode: !granted }),

  setSound: (soundId, manual = false) =>
    set({ soundId, manualOverride: manual || get().manualOverride }),

  setVolume: (volume) => set({ volume: Math.max(0, Math.min(1, volume)) }),

  setTimer: (endAt) => set({ timerEndAt: endAt }),

  setNoiseEstimate: (estimate) => set({ lastNoiseEstimate: estimate }),

  dispatch: (event) => {
    const next = transition(get().state, event);
    if (event.type === 'START') {
      set({
        state: next,
        sessionId: crypto.randomUUID(),
        startedAt: new Date().toISOString(),
        manualOverride: !get().micGranted,
      });
    } else if (event.type === 'STOP' || event.type === 'TIMER_EXPIRED') {
      set({ state: next });
    } else {
      set({ state: next });
    }
  },

  reset: () =>
    set({
      state: 'idle',
      sessionId: null,
      soundId: DEFAULT_SOUND,
      timerEndAt: null,
      startedAt: null,
      manualOverride: false,
      lastNoiseEstimate: null,
    }),
}));
