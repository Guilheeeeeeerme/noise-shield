import { useEffect, type ReactNode } from 'react';
import { useAuthStore } from '@/stores/authStore';

/** Hydrates auth state; does not block unsigned access to core routes. */
export function AuthGate({ children }: { children: ReactNode }) {
  const hydrate = useAuthStore((s) => s.hydrate);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  return <>{children}</>;
}
