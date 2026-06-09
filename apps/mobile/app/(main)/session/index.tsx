import { useEffect, useState } from 'react';
import { View, Text, Pressable, StyleSheet, ScrollView } from 'react-native';
import { useSessionStore } from '@/stores/sessionStore';
import { startSession, stopSession } from '@/features/session/startSession';
import { SoundPicker } from '@/features/session/SoundPicker';
import { VolumeControl } from '@/features/session/VolumeControl';
import { TimerControl } from '@/features/session/TimerControl';
import { SessionEndedBanner } from '@/features/session/SessionEndedBanner';
import { LimitedModeBanner } from '@/features/session/LimitedModeBanner';
import { NoiseIndicator } from '@/features/analysis/NoiseIndicator';
import { setupAudioFocusHandlers } from '@/services/playback/audioFocus';
import { registerPlaybackService } from '@/services/playback/playbackService';
import { playbackAdapter } from '@/services/playback/TrackPlayerAdapter';
import { SessionFeedbackModal } from '@/features/feedback/SessionFeedbackModal';
import { canStart } from '@/features/session/sessionStateMachine';
import { Link } from 'expo-router';

export default function SessionScreen() {
  const state = useSessionStore((s) => s.state);
  const limitedMode = useSessionStore((s) => s.limitedMode);
  const setSound = useSessionStore((s) => s.setSound);
  const reset = useSessionStore((s) => s.reset);
  const [showFeedback, setShowFeedback] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    registerPlaybackService();
    return setupAudioFocusHandlers();
  }, []);

  const handleStart = async () => {
    setBusy(true);
    try {
      await startSession();
    } finally {
      setBusy(false);
    }
  };

  const handleStop = async () => {
    setBusy(true);
    try {
      await stopSession();
    } finally {
      setBusy(false);
    }
  };

  const handleSoundChange = async (soundId: string) => {
    setSound(soundId as Parameters<typeof setSound>[0], true);
    if (state === 'playing' || state === 'paused') {
      await playbackAdapter.load(soundId);
      await playbackAdapter.play();
    }
  };

  const isActive = state === 'playing' || state === 'analyzing' || state === 'paused';

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.header}>
        <Text style={styles.title}>Session</Text>
        <Link href="/(main)/settings" style={styles.settingsLink}>
          Settings
        </Link>
      </View>

      <LimitedModeBanner />
      <NoiseIndicator />

      <SoundPicker onSelect={handleSoundChange} />
      <VolumeControl />
      <TimerControl />

      <Pressable
        style={[styles.mainBtn, isActive && styles.stopBtn]}
        onPress={isActive ? handleStop : handleStart}
        disabled={busy || (!isActive && !canStart(state))}
      >
        <Text style={styles.mainBtnText}>
          {busy ? '...' : isActive ? 'Stop Session' : 'Start Session'}
        </Text>
      </Pressable>

      {limitedMode && (
        <Text style={styles.hint}>Manual mode — pick a sound above</Text>
      )}

      <SessionEndedBanner
        onDismiss={() => reset()}
        onFeedback={() => setShowFeedback(true)}
      />

      <SessionFeedbackModal
        visible={showFeedback}
        onClose={() => setShowFeedback(false)}
      />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f172a' },
  content: { padding: 24, paddingTop: 60 },
  header: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 24 },
  title: { fontSize: 28, fontWeight: '700', color: '#f8fafc' },
  settingsLink: { color: '#38bdf8', fontSize: 16 },
  mainBtn: {
    backgroundColor: '#0ea5e9',
    padding: 18,
    borderRadius: 16,
    marginTop: 24,
  },
  stopBtn: { backgroundColor: '#dc2626' },
  mainBtnText: { color: '#fff', textAlign: 'center', fontSize: 18, fontWeight: '600' },
  hint: { color: '#94a3b8', textAlign: 'center', marginTop: 12 },
});
