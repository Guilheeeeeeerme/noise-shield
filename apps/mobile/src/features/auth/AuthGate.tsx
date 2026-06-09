import { useEffect, type ReactNode } from 'react';
import { usePathname, useRouter } from 'expo-router';
import { useAuthStore } from '@/stores/authStore';

const PUBLIC_ROUTES = ['/(auth)/sign-in'];

export function AuthGate({ children }: { children: ReactNode }) {
  const hydrate = useAuthStore((s) => s.hydrate);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  useEffect(() => {
    const isPublic = PUBLIC_ROUTES.some((r) => pathname.startsWith(r.replace('/(auth)', '/auth')));
    if (!isAuthenticated && !pathname.includes('sign-in')) {
      router.replace('/(auth)/sign-in');
    } else if (isAuthenticated && pathname.includes('sign-in')) {
      router.replace('/');
    }
    void isPublic;
  }, [isAuthenticated, pathname, router]);

  return <>{children}</>;
}
