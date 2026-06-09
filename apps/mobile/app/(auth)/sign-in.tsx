import { useState } from 'react';
import { View, Text, Pressable, StyleSheet, ActivityIndicator } from 'react-native';
import { useAuthStore } from '@/stores/authStore';
import { OfflineSignInBlock } from '@/features/auth/OfflineSignInBlock';

export default function SignInScreen() {
  const signIn = useAuthStore((s) => s.signIn);
  const isLoading = useAuthStore((s) => s.isLoading);
  const [error, setError] = useState<string | null>(null);

  const handleSignIn = async (provider: 'google' | 'apple' | 'facebook') => {
    setError(null);
    try {
      // MVP: dev token placeholder — wire provider SDKs in production build
      await signIn(provider, `dev-token-${provider}-${Date.now()}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Sign-in failed');
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Noise Shield</Text>
      <Text style={styles.subtitle}>Reduce perceived noise with adaptive masking</Text>

      <OfflineSignInBlock />

      {isLoading ? (
        <ActivityIndicator size="large" color="#38bdf8" />
      ) : (
        <>
          <Pressable style={styles.button} onPress={() => handleSignIn('google')}>
            <Text style={styles.buttonText}>Continue with Google</Text>
          </Pressable>
          <Pressable style={styles.button} onPress={() => handleSignIn('apple')}>
            <Text style={styles.buttonText}>Continue with Apple</Text>
          </Pressable>
          <Pressable style={styles.button} onPress={() => handleSignIn('facebook')}>
            <Text style={styles.buttonText}>Continue with Facebook</Text>
          </Pressable>
        </>
      )}

      {error && <Text style={styles.error}>{error}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    padding: 24,
    backgroundColor: '#0f172a',
    gap: 12,
  },
  title: { fontSize: 32, fontWeight: '700', color: '#f8fafc', textAlign: 'center' },
  subtitle: { fontSize: 16, color: '#94a3b8', textAlign: 'center', marginBottom: 24 },
  button: {
    backgroundColor: '#1e293b',
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#334155',
  },
  buttonText: { color: '#f8fafc', textAlign: 'center', fontSize: 16 },
  error: { color: '#f87171', textAlign: 'center', marginTop: 8 },
});
