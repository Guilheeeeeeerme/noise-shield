import { View, Text, StyleSheet, ScrollView, Pressable } from 'react-native';
import { Link } from 'expo-router';
import { AppearanceSetting } from '@/features/settings/AppearanceSetting';
import { LanguageSetting } from '@/features/settings/LanguageSetting';
import { useAuthStore } from '@/stores/authStore';

export default function SettingsScreen() {
  const signOut = useAuthStore((s) => s.signOut);

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Link href="/(main)/session" style={styles.back}>
        ← Session
      </Link>
      <Text style={styles.title}>Settings</Text>
      <AppearanceSetting />
      <LanguageSetting />
      <Pressable style={styles.signOut} onPress={signOut}>
        <Text style={styles.signOutText}>Sign Out</Text>
      </Pressable>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f172a' },
  content: { padding: 24, paddingTop: 60 },
  back: { color: '#38bdf8', marginBottom: 16 },
  title: { fontSize: 28, fontWeight: '700', color: '#f8fafc', marginBottom: 24 },
  signOut: {
    marginTop: 32,
    padding: 16,
    backgroundColor: '#1e293b',
    borderRadius: 12,
  },
  signOutText: { color: '#f87171', textAlign: 'center' },
});
