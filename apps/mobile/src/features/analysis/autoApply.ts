import type { NoiseEstimate } from '@noise-shield/audio-analysis';
import { PROFILE_TO_SOUND } from '@noise-shield/shared';
import { useSessionStore } from '@/stores/sessionStore';
import { isManualOverrideActive } from './manualOverride';
import { crossfadeToSound } from '@/services/playback/crossfade';
import { getAnalysisTuning } from '@/services/config/bundledDefaults';

let lastApplyAt = 0;
let lastProfile: string | null = null;

export function handleAutoApply(estimate: NoiseEstimate): void {
  const tuning = getAnalysisTuning();

  if (isManualOverrideActive()) return;
  if (estimate.confidence < tuning.classifier_threshold) return;
  if (estimate.broadProfile === lastProfile) return;

  const now = Date.now();
  if (now - lastApplyAt < tuning.auto_apply_debounce_ms) return;

  const soundId = PROFILE_TO_SOUND[estimate.broadProfile];
  const { soundId: current, setSound } = useSessionStore.getState();
  if (soundId === current) return;

  lastApplyAt = now;
  lastProfile = estimate.broadProfile;
  setSound(soundId, false);
  crossfadeToSound(soundId, tuning.crossfade_ms).catch(console.error);
}

export function resetAutoApply(): void {
  lastApplyAt = 0;
  lastProfile = null;
}
