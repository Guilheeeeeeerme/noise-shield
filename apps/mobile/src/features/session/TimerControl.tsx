import { View, Text, Pressable, StyleSheet } from 'react-native';
import { useSessionStore } from '@/stores/sessionStore';
import { stopSession } from './startSession';

const TIMER_OPTIONS = [15, 30, 45, 60, 90, 120];

export function TimerControl() {
  const timerEndAt = useSessionStore((s) => s.timerEndAt);
  const setTimer = useSessionStore((s) => s.setTimer);
  const dispatch = useSessionStore((s) => s.dispatch);

  const setMinutes = (minutes: number) => {
    const end = new Date(Date.now() + minutes * 60 * 1000).toISOString();
    setTimer(end);
    setTimeout(async () => {
      const current = useSessionStore.getState().timerEndAt;
      if (current === end) {
        dispatch({ type: 'TIMER_EXPIRED' });
        await stopSession();
      }
    }, minutes * 60 * 1000);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.label}>Timer</Text>
      <View style={styles.row}>
        {TIMER_OPTIONS.map((min) => (
          <Pressable key={min} style={styles.chip} onPress={() => setMinutes(min)}>
            <Text style={styles.chipText}>{min}m</Text>
          </Pressable>
        ))}
        <Pressable style={styles.chip} onPress={() => setTimer(null)}>
          <Text style={styles.chipText}>Off</Text>
        </Pressable>
      </View>
      {timerEndAt && (
        <Text style={styles.active}>Ends {new Date(timerEndAt).toLocaleTimeString()}</Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { paddingVertical: 8 },
  label: { color: '#94a3b8', marginBottom: 4 },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: {
    backgroundColor: '#1e293b',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 16,
  },
  chipText: { color: '#f8fafc' },
  active: { color: '#38bdf8', marginTop: 8 },
});
