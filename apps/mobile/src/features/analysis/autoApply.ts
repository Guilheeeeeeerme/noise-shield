import type { NoiseEstimate } from '@noise-shield/audio-analysis';
import { PROFILE_TO_SOUND } from '@noise-shield/shared';
import { useSessionStore } from '@/stores/sessionStore';
import { isManualOverrideActive } from './manualOverride';
import { crossfadeToSound } from '@/services/playback/crossfade';

let lastApplyAt = 0;
let lastProfile: string | null = null;
const DEBOUNCE_MS = 5000;
const MIN_CONFIDENCE = 0.55;

export function handleAutoApply(estimate: NoiseEstimate): void {
  if (isManualOverrideActive()) return;
  if (estimate.confidence < MIN_CONFIDENCE) return;
  if (estimate.broadProfile === lastProfile) return;

  const now = Date.now();
  if (now - lastApplyAt < DEBOUNCE_MS) return;

  const soundId = PROFILE_TO_SOUND[estimate.broadProfile];
  const { soundId: current, setSound } = useSessionStore.getState();
  if (soundId === current) return;

  lastApplyAt = now;
  lastProfile = estimate.broadProfile;
  setSound(soundId, false);
  crossfadeToSound(soundId).catch(console.error);
}

export function resetAutoApply(): void {
  lastApplyAt = 0;
  lastProfile = null;
}
