import { View, Text, Pressable, StyleSheet } from 'react-native';
import { PRIVACY_COPY } from '@noise-shield/shared';

interface Props {
  onContinue: (grant: boolean) => void;
}

export function MicPermissionScreen({ onContinue }: Props) {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>Microphone Access</Text>
      <Text style={styles.body}>{PRIVACY_COPY.en.micRationale}</Text>
      <Pressable style={styles.primary} onPress={() => onContinue(true)}>
        <Text style={styles.primaryText}>Allow Microphone</Text>
      </Pressable>
      <Pressable style={styles.secondary} onPress={() => onContinue(false)}>
        <Text style={styles.secondaryText}>Continue Without Mic</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    padding: 24,
    backgroundColor: '#0f172a',
    gap: 16,
  },
  title: { fontSize: 24, fontWeight: '700', color: '#f8fafc' },
  body: { fontSize: 16, color: '#94a3b8', lineHeight: 24 },
  primary: { backgroundColor: '#0ea5e9', padding: 16, borderRadius: 12 },
  primaryText: { color: '#fff', textAlign: 'center', fontWeight: '600' },
  secondary: { padding: 16 },
  secondaryText: { color: '#94a3b8', textAlign: 'center' },
});
