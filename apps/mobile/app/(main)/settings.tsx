import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { Link } from 'expo-router';
import { AppearanceSetting } from '@/features/settings/AppearanceSetting';
import { LanguageSetting } from '@/features/settings/LanguageSetting';
import { SignInSetting } from '@/features/settings/SignInSetting';
import { OnboardingSetting } from '@/features/settings/OnboardingSetting';

export default function SettingsScreen() {
  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Link href="/(main)/session" style={styles.back}>
        ← Session
      </Link>
      <Text style={styles.title}>Settings</Text>
      <SignInSetting />
      <AppearanceSetting />
      <LanguageSetting />
      <OnboardingSetting />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f172a' },
  content: { padding: 24, paddingTop: 60 },
  back: { color: '#38bdf8', marginBottom: 16 },
  title: { fontSize: 28, fontWeight: '700', color: '#f8fafc', marginBottom: 24 },
});
