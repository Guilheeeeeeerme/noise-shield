import { View, Text, Pressable, StyleSheet } from 'react-native';
import { useSessionStore } from '@/stores/sessionStore';
import { playbackAdapter } from '@/services/playback/TrackPlayerAdapter';

export function VolumeControl() {
  const volume = useSessionStore((s) => s.volume);
  const setVolume = useSessionStore((s) => s.setVolume);

  const adjust = (delta: number) => {
    const next = Math.max(0, Math.min(1, volume + delta));
    setVolume(next);
    playbackAdapter.setVolume(next);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.label}>Volume</Text>
      <View style={styles.row}>
        <Pressable style={styles.btn} onPress={() => adjust(-0.1)}>
          <Text style={styles.btnText}>−</Text>
        </Pressable>
        <View style={styles.track}>
          <View style={[styles.fill, { width: `${volume * 100}%` }]} />
        </View>
        <Pressable style={styles.btn} onPress={() => adjust(0.1)}>
          <Text style={styles.btnText}>+</Text>
        </Pressable>
      </View>
      <Text style={styles.value}>{Math.round(volume * 100)}%</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { paddingVertical: 8 },
  label: { color: '#94a3b8', marginBottom: 4 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  btn: {
    backgroundColor: '#1e293b',
    width: 36,
    height: 36,
    borderRadius: 18,
    justifyContent: 'center',
    alignItems: 'center',
  },
  btnText: { color: '#f8fafc', fontSize: 20 },
  track: {
    flex: 1,
    height: 8,
    backgroundColor: '#334155',
    borderRadius: 4,
    overflow: 'hidden',
  },
  fill: { height: '100%', backgroundColor: '#38bdf8' },
  value: { color: '#f8fafc', textAlign: 'right', marginTop: 4 },
});
