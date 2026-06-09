import { Modal, View, Text, Pressable, StyleSheet } from 'react-native';
import { CONSENT_POLICY_VERSION, PRIVACY_COPY } from '@noise-shield/shared';
import { api } from '@/services/api/client';
import { setBoolean, STORAGE_KEYS } from '@/services/storage/mmkv';
import { useAuthStore } from '@/stores/authStore';

interface Props {
  visible: boolean;
  onComplete: () => void;
}

/** FR-027 — consent prompt for signed-in users only. */
export function DataConsentModal({ visible, onComplete }: Props) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const handleChoice = async (optIn: boolean) => {
    setBoolean(STORAGE_KEYS.CONSENT_SHOWN, true);
    if (isAuthenticated) {
      try {
        await api.putConsent(optIn, CONSENT_POLICY_VERSION);
      } catch {
        // Offline — consent syncs later via queue
      }
    }
    onComplete();
  };

  if (!isAuthenticated) return null;

  return (
    <Modal visible={visible} transparent animationType="fade">
      <View style={styles.overlay}>
        <View style={styles.card}>
          <Text style={styles.title}>{PRIVACY_COPY.en.dataConsentTitle}</Text>
          <Text style={styles.body}>{PRIVACY_COPY.en.dataConsentBody}</Text>
          <Pressable style={styles.primary} onPress={() => handleChoice(true)}>
            <Text style={styles.primaryText}>{PRIVACY_COPY.en.dataConsentOptIn}</Text>
          </Pressable>
          <Pressable style={styles.secondary} onPress={() => handleChoice(false)}>
            <Text style={styles.secondaryText}>{PRIVACY_COPY.en.dataConsentDecline}</Text>
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
