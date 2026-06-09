import { View, Text, Pressable, StyleSheet } from 'react-native';
import { useRouter } from 'expo-router';
import { setOnboardingCompleted } from '@/features/onboarding/onboardingState';

export function OnboardingSetting() {
  const router = useRouter();

  return (
    <View style={styles.section}>
      <Text style={styles.label}>Onboarding</Text>
      <Pressable
        style={styles.button}
        onPress={() => {
          setOnboardingCompleted(false);
          router.push('/(onboarding)');
        }}
      >
        <Text style={styles.buttonText}>Review onboarding</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  section: { marginBottom: 24 },
  label: { fontSize: 16, fontWeight: '600', color: '#f8fafc', marginBottom: 8 },
  button: {
    backgroundColor: '#1e293b',
    padding: 14,
    borderRadius: 12,
  },
  buttonText: { color: '#38bdf8', textAlign: 'center' },
});
