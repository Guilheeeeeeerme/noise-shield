import { View, Text, Pressable, StyleSheet } from 'react-native';
import { useTranslation } from 'react-i18next';
import { setString, STORAGE_KEYS } from '@/services/storage/mmkv';
import { saveLocalPreference } from './preferencesLocal';

const LANGUAGES = ['en', 'pt'] as const;

export function LanguageSetting() {
  const { i18n } = useTranslation();

  return (
    <View style={styles.container}>
      <Text style={styles.label}>Language</Text>
      <View style={styles.row}>
        {LANGUAGES.map((lang) => (
          <Pressable
            key={lang}
            style={[styles.chip, i18n.language === lang && styles.active]}
            onPress={() => {
              i18n.changeLanguage(lang);
              setString(STORAGE_KEYS.LANGUAGE, lang);
              saveLocalPreference('language', lang);
            }}
          >
            <Text style={styles.chipText}>{lang === 'en' ? 'English' : 'Português'}</Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { marginBottom: 24 },
  label: { color: '#94a3b8', marginBottom: 8 },
  row: { flexDirection: 'row', gap: 8 },
  chip: {
    backgroundColor: '#1e293b',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 20,
  },
  active: { backgroundColor: '#0ea5e9' },
  chipText: { color: '#f8fafc' },
});
