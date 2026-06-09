import { useSessionStore } from '@/stores/sessionStore';

const OVERRIDE_DURATION_MS = 60_000;
let overrideUntil = 0;

export function setManualOverride(): void {
  overrideUntil = Date.now() + OVERRIDE_DURATION_MS;
  useSessionStore.getState().setSound(
    useSessionStore.getState().soundId,
    true,
  );
}

export function isManualOverrideActive(): boolean {
  const storeOverride = useSessionStore.getState().manualOverride;
  if (storeOverride && Date.now() < overrideUntil) return true;
  if (Date.now() >= overrideUntil) {
    overrideUntil = 0;
  }
  return false;
}
