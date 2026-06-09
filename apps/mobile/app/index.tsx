import { Redirect } from 'expo-router';
import { getOnboardingCompleted } from '@/features/onboarding/onboardingState';

export default function Index() {
  if (!getOnboardingCompleted()) {
    return <Redirect href="/(onboarding)" />;
  }

  return <Redirect href="/(main)/session" />;
}
