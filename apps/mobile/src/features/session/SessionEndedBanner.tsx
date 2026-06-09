import { View, Text, Pressable, StyleSheet } from 'react-native';
import { useSessionStore } from '@/stores/sessionStore';

interface Props {
  onDismiss: () => void;
  onFeedback?: () => void;
}

export function SessionEndedBanner({ onDismiss, onFeedback }: Props) {
  const state = useSessionStore((s) => s.state);

  if (state !== 'ended') return null;

  return (
    <View style={styles.banner}>
      <Text style={styles.text}>Session ended</Text>
      {onFeedback && (
        <Pressable onPress={onFeedback}>
          <Text style={styles.link}>Was this helpful?</Text>
        </Pressable>
      )}
      <Pressable onPress={onDismiss}>
        <Text style={styles.dismiss}>Dismiss</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    backgroundColor: '#1e3a5f',
    padding: 16,
    borderRadius: 12,
    marginTop: 16,
    gap: 8,
  },
  text: { color: '#f8fafc', fontWeight: '600' },
  link: { color: '#38bdf8' },
  dismiss: { color: '#94a3b8', textAlign: 'right' },
});
