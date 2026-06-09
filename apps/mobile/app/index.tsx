import { Redirect } from 'expo-router';
import { useAuthStore } from '@/stores/authStore';
import { getOnboardingCompleted } from '@/features/onboarding/onboardingState';

export default function Index() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  if (!isAuthenticated) {
    return <Redirect href="/(auth)/sign-in" />;
  }

  if (!getOnboardingCompleted()) {
    return <Redirect href="/(onboarding)" />;
  }

  return <Redirect href="/(main)/session" />;
}
