import { Modal, View, Text, Pressable, StyleSheet } from 'react-native';
import { PRIVACY_COPY } from '@noise-shield/shared';

interface Props {
  visible: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/** FR-035 — warn before first sign-in that local data will be discarded. */
export function SignInDiscardWarning({ visible, onConfirm, onCancel }: Props) {
  return (
    <Modal visible={visible} transparent animationType="fade">
      <View style={styles.overlay}>
        <View style={styles.card}>
          <Text style={styles.title}>{PRIVACY_COPY.en.signInDiscardTitle}</Text>
          <Text style={styles.body}>{PRIVACY_COPY.en.signInDiscardBody}</Text>
          <Pressable style={styles.primary} onPress={onConfirm}>
            <Text style={styles.primaryText}>{PRIVACY_COPY.en.signInDiscardConfirm}</Text>
          </Pressable>
          <Pressable style={styles.secondary} onPress={onCancel}>
            <Text style={styles.secondaryText}>{PRIVACY_COPY.en.signInDiscardCancel}</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'center',
    padding: 24,
  },
  card: {
    backgroundColor: '#1e293b',
    borderRadius: 16,
    padding: 24,
    gap: 16,
  },
  title: { fontSize: 20, fontWeight: '700', color: '#f8fafc' },
  body: { fontSize: 15, color: '#94a3b8', lineHeight: 22 },
  primary: { backgroundColor: '#0ea5e9', padding: 14, borderRadius: 10 },
  primaryText: { color: '#fff', textAlign: 'center', fontWeight: '600' },
  secondary: { padding: 14 },
  secondaryText: { color: '#94a3b8', textAlign: 'center' },
});
