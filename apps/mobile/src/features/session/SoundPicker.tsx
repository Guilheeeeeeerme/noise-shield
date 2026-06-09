import { View, Text, Pressable, StyleSheet, ScrollView } from 'react-native';
import { MASKING_SOUNDS, type MaskingSoundId } from '@noise-shield/shared';
import { useSessionStore } from '@/stores/sessionStore';
import { toggleFavorite, isFavorite } from './favoritesLocal';

interface Props {
  onSelect?: (soundId: MaskingSoundId) => void;
}

export function SoundPicker({ onSelect }: Props) {
  const soundId = useSessionStore((s) => s.soundId);
  const setSound = useSessionStore((s) => s.setSound);

  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.container}>
      {MASKING_SOUNDS.map((sound) => (
        <Pressable
          key={sound.id}
          style={[styles.chip, soundId === sound.id && styles.chipActive]}
          onPress={() => {
            setSound(sound.id, true);
            onSelect?.(sound.id);
          }}
        >
          <Text style={styles.chipText}>{sound.id.replace(/_/g, ' ')}</Text>
          {isFavorite(sound.id) && <Text style={styles.star}>★</Text>}
          <Pressable onPress={() => toggleFavorite(sound.id)}>
            <Text style={styles.favBtn}>{isFavorite(sound.id) ? '♥' : '♡'}</Text>
          </Pressable>
        </Pressable>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flexGrow: 0, marginVertical: 8 },
  chip: {
    backgroundColor: '#1e293b',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 20,
    marginRight: 8,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  chipActive: { backgroundColor: '#0ea5e9' },
  chipText: { color: '#f8fafc', textTransform: 'capitalize' },
  star: { color: '#fcd34d' },
  favBtn: { color: '#f87171', marginLeft: 4 },
});
