import { useSessionStore } from '@/stores/sessionStore';

export function isAnalysisEnabled(): boolean {
  const { micGranted, limitedMode, state } = useSessionStore.getState();
  if (limitedMode || !micGranted) return false;
  return state === 'analyzing' || state === 'playing';
}

export function shouldDisableAnalysis(): boolean {
  return !isAnalysisEnabled();
}
