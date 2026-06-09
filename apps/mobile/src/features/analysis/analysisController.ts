import { HeuristicAnalysisPort } from '@noise-shield/audio-analysis';
import { useSessionStore } from '@/stores/sessionStore';
import { shouldDisableAnalysis } from '@/features/session/sessionController';
import { handleAutoApply } from './autoApply';

let analysisPort: HeuristicAnalysisPort | null = null;
let unsubscribe: (() => void) | null = null;

export async function startAnalysis(): Promise<void> {
  if (shouldDisableAnalysis()) return;

  if (!analysisPort) {
    analysisPort = new HeuristicAnalysisPort();
  }

  await analysisPort.start();
  unsubscribe = analysisPort.onEstimate((estimate) => {
    useSessionStore.getState().setNoiseEstimate(estimate);
    handleAutoApply(estimate);
  });
}

export async function stopAnalysis(): Promise<void> {
  unsubscribe?.();
  unsubscribe = null;
  if (analysisPort) {
    await analysisPort.stop();
  }
}
