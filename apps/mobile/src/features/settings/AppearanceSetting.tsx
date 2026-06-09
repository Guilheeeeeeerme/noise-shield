import { View, Text, Pressable, StyleSheet } from 'react-native';
import { useTheme, type ThemeMode } from '@/theme/ThemeProvider';
import { saveLocalPreference } from './preferencesLocal';

const OPTIONS: ThemeMode[] = ['system', 'light', 'dark'];

export function AppearanceSetting() {
  const { mode, setMode } = useTheme();

  return (
    <View style={styles.container}>
      <Text style={styles.label}>Appearance</Text>
      <View style={styles.row}>
        {OPTIONS.map((opt) => (
          <Pressable
            key={opt}
            style={[styles.chip, mode === opt && styles.active]}
            onPress={() => {
              setMode(opt);
              saveLocalPreference('theme', opt);
            }}
          >
            <Text style={styles.chipText}>{opt}</Text>
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
  chipText: { color: '#f8fafc', textTransform: 'capitalize' },
});
