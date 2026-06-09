import { View, Text, StyleSheet } from 'react-native';
import { useSessionStore } from '@/stores/sessionStore';
import { shouldDisableAnalysis } from '@/features/session/sessionController';

export function NoiseIndicator() {
  const estimate = useSessionStore((s) => s.lastNoiseEstimate);
  const state = useSessionStore((s) => s.state);

  if (shouldDisableAnalysis() || !estimate || state === 'idle') {
    return null;
  }

  return (
    <View style={styles.container}>
      <Text style={styles.label}>Ambient</Text>
      <Text style={styles.level}>{estimate.levelBucket}</Text>
      <Text style={styles.profile}>{estimate.broadProfile.replace(/_/g, ' ')}</Text>
      <Text style={styles.confidence}>{Math.round(estimate.confidence * 100)}% confidence</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#1e293b',
    padding: 16,
    borderRadius: 12,
    marginBottom: 12,
  },
  label: { color: '#94a3b8', fontSize: 12 },
  level: { color: '#38bdf8', fontSize: 20, fontWeight: '700', textTransform: 'capitalize' },
  profile: { color: '#f8fafc', textTransform: 'capitalize' },
  confidence: { color: '#64748b', fontSize: 12, marginTop: 4 },
});
