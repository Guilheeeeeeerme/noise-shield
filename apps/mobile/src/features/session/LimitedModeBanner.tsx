import { Text, StyleSheet } from 'react-native';
import { PRIVACY_COPY } from '@noise-shield/shared';
import { useSessionStore } from '@/stores/sessionStore';

export function LimitedModeBanner() {
  const limitedMode = useSessionStore((s) => s.limitedMode);
  if (!limitedMode) return null;
  return <Text style={styles.banner}>{PRIVACY_COPY.en.limitedModeBanner}</Text>;
}

const styles = StyleSheet.create({
  banner: {
    backgroundColor: '#422006',
    color: '#fcd34d',
    padding: 12,
    borderRadius: 8,
    marginBottom: 12,
  },
});
