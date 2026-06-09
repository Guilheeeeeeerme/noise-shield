import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { AuthGate } from '@/features/auth/AuthGate';
import { ThemeProvider } from '@/theme/ThemeProvider';
import { ErrorBoundary } from '@/components/ErrorBoundary';
import '@/i18n';

export default function RootLayout() {
  return (
    <ErrorBoundary>
      <ThemeProvider>
        <AuthGate>
          <Stack screenOptions={{ headerShown: false }}>
            <Stack.Screen name="index" />
            <Stack.Screen name="(auth)" />
            <Stack.Screen name="(onboarding)" />
            <Stack.Screen name="(main)" />
          </Stack>
          <StatusBar style="auto" />
        </AuthGate>
      </ThemeProvider>
    </ErrorBoundary>
  );
}
