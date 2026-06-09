import { Modal, View, Text, Pressable, StyleSheet } from 'react-native';
import { submitSessionFeedback } from './submitFeedback';

interface Props {
  visible: boolean;
  onClose: () => void;
}

export function SessionFeedbackModal({ visible, onClose }: Props) {
  const handleSubmit = async (helpful: boolean) => {
    await submitSessionFeedback(helpful);
    onClose();
  };

  return (
    <Modal visible={visible} transparent animationType="slide">
      <View style={styles.overlay}>
        <View style={styles.card}>
          <Text style={styles.title}>Was this session helpful?</Text>
          <View style={styles.row}>
            <Pressable style={styles.btn} onPress={() => handleSubmit(true)}>
              <Text style={styles.btnText}>Yes</Text>
            </Pressable>
            <Pressable style={styles.btn} onPress={() => handleSubmit(false)}>
              <Text style={styles.btnText}>No</Text>
            </Pressable>
          </View>
          <Pressable onPress={onClose}>
            <Text style={styles.skip}>Skip</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'flex-end',
  },
  card: {
    backgroundColor: '#1e293b',
    padding: 24,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    gap: 16,
  },
  title: { color: '#f8fafc', fontSize: 18, fontWeight: '600', textAlign: 'center' },
  row: { flexDirection: 'row', gap: 12 },
  btn: {
    flex: 1,
    backgroundColor: '#0ea5e9',
    padding: 14,
    borderRadius: 10,
  },
  btnText: { color: '#fff', textAlign: 'center', fontWeight: '600' },
  skip: { color: '#94a3b8', textAlign: 'center' },
});
