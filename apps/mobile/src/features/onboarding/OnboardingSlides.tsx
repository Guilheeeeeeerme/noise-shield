import { View, Text, StyleSheet, Dimensions } from 'react-native';

const SLIDES = [
  {
    title: 'Welcome to Noise Shield',
    body: 'Reduce perceived environmental noise with adaptive masking sounds.',
  },
  {
    title: 'Privacy First',
    body: 'Audio is analyzed on your device. Nothing is uploaded without your consent.',
  },
  {
    title: 'Works Offline',
    body: 'After sign-in, masking sessions work without an internet connection.',
  },
];

interface Props {
  index: number;
}

export function OnboardingSlides({ index }: Props) {
  const slide = SLIDES[index] ?? SLIDES[0];
  return (
    <View style={styles.container}>
      <Text style={styles.title}>{slide.title}</Text>
      <Text style={styles.body}>{slide.body}</Text>
    </View>
  );
}

const { width } = Dimensions.get('window');

const styles = StyleSheet.create({
  container: { width: width - 48, padding: 24, alignItems: 'center' },
  title: { fontSize: 24, fontWeight: '700', color: '#f8fafc', marginBottom: 16, textAlign: 'center' },
  body: { fontSize: 16, color: '#94a3b8', textAlign: 'center', lineHeight: 24 },
});
