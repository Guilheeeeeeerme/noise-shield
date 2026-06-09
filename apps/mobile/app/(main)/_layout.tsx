import { Stack } from 'expo-router';

export default function MainLayout() {
  return (
    <Stack screenOptions={{ headerShown: false }}>
      <Stack.Screen name="session/index" />
      <Stack.Screen name="settings" />
    </Stack>
  );
}
