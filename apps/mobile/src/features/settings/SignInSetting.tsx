import { useState } from 'react';
import { View, Text, Pressable, StyleSheet, ActivityIndicator } from 'react-native';
import { useAuthStore } from '@/stores/authStore';
import { SignInDiscardWarning } from '@/features/auth/SignInDiscardWarning';
import { executeSignIn, isFirstSignIn } from '@/features/auth/signInFlow';

export function SignInSetting() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const user = useAuthStore((s) => s.user);
  const signOut = useAuthStore((s) => s.signOut);
  const isLoading = useAuthStore((s) => s.isLoading);
  const [error, setError] = useState<string | null>(null);
  const [pendingProvider, setPendingProvider] = useState<
    'google' | 'apple' | 'facebook' | null
  >(null);
  const [showDiscardWarning, setShowDiscardWarning] = useState(false);

  const beginSignIn = (provider: 'google' | 'apple' | 'facebook') => {
    setError(null);
    if (isFirstSignIn()) {
      setPendingProvider(provider);
      setShowDiscardWarning(true);
      return;
    }
    void completeSignIn(provider);
  };

  const completeSignIn = async (provider: 'google' | 'apple' | 'facebook') => {
    setError(null);
    try {
      await executeSignIn(provider, `dev-token-${provider}-${Date.now()}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Sign-in failed');
    }
  };

  if (isAuthenticated) {
    return (
      <View style={styles.section}>
        <Text style={styles.label}>Account</Text>
        <Text style={styles.value}>{user?.email ?? user?.display_name ?? user?.id}</Text>
        <Pressable style={styles.signOut} onPress={signOut}>
          <Text style={styles.signOutText}>Sign Out</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <View style={styles.section}>
      <Text style={styles.label}>Sign In</Text>
      <Text style={styles.hint}>Optional — sync preferences across devices</Text>

      {isLoading ? (
        <ActivityIndicator color="#38bdf8" style={styles.loader} />
      ) : (
        <>
          <Pressable style={styles.button} onPress={() => beginSignIn('google')}>
            <Text style={styles.buttonText}>Continue with Google</Text>
          </Pressable>
          <Pressable style={styles.button} onPress={() => beginSignIn('apple')}>
            <Text style={styles.buttonText}>Continue with Apple</Text>
          </Pressable>
          <Pressable style={styles.button} onPress={() => beginSignIn('facebook')}>
            <Text style={styles.buttonText}>Continue with Facebook</Text>
          </Pressable>
        </>
      )}

      {error && <Text style={styles.error}>{error}</Text>}

      <SignInDiscardWarning
        visible={showDiscardWarning}
        onConfirm={() => {
          setShowDiscardWarning(false);
          if (pendingProvider) void completeSignIn(pendingProvider);
          setPendingProvider(null);
        }}
        onCancel={() => {
          setShowDiscardWarning(false);
          setPendingProvider(null);
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  section: { marginBottom: 24, gap: 8 },
  label: { fontSize: 16, fontWeight: '600', color: '#f8fafc' },
  hint: { fontSize: 14, color: '#94a3b8', marginBottom: 8 },
  value: { fontSize: 14, color: '#cbd5e1' },
  button: {
    backgroundColor: '#1e293b',
    padding: 14,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#334155',
  },
  buttonText: { color: '#f8fafc', textAlign: 'center' },
  signOut: {
    marginTop: 8,
    padding: 12,
    backgroundColor: '#1e293b',
    borderRadius: 10,
  },
  signOutText: { color: '#f87171', textAlign: 'center' },
  error: { color: '#f87171', fontSize: 14 },
  loader: { marginVertical: 12 },
});
