import { useState } from 'react';
import { View, Pressable, Text, StyleSheet } from 'react-native';
import { useRouter } from 'expo-router';
import { OnboardingSlides } from '@/features/onboarding/OnboardingSlides';
import { MicPermissionScreen } from '@/features/onboarding/MicPermissionScreen';
import { DataConsentModal } from '@/features/onboarding/DataConsentModal';
import { setOnboardingCompleted } from '@/features/onboarding/onboardingState';
import { requestMicrophonePermission } from '@/services/permissions/microphone';
import { useSessionStore } from '@/stores/sessionStore';

const SLIDE_COUNT = 3;

export default function OnboardingScreen() {
  const [slide, setSlide] = useState(0);
  const [showMic, setShowMic] = useState(false);
  const [showConsent, setShowConsent] = useState(false);
  const router = useRouter();
  const setMicGranted = useSessionStore((s) => s.setMicGranted);

  const finish = () => {
    setOnboardingCompleted(true);
    router.replace('/(main)/session');
  };

  const handleNext = () => {
    if (slide < SLIDE_COUNT - 1) {
      setSlide(slide + 1);
    } else {
      setShowMic(true);
    }
  };

  const handleMicResult = async (grant: boolean) => {
    if (grant) {
      const granted = await requestMicrophonePermission();
      setMicGranted(granted);
    } else {
      setMicGranted(false);
    }
    setShowMic(false);
    setShowConsent(true);
  };

  if (showMic) {
    return <MicPermissionScreen onContinue={handleMicResult} />;
  }

  return (
    <View style={styles.container}>
      <OnboardingSlides index={slide} />
      <Pressable style={styles.button} onPress={handleNext}>
        <Text style={styles.buttonText}>
          {slide < SLIDE_COUNT - 1 ? 'Next' : 'Continue'}
        </Text>
      </Pressable>
      <DataConsentModal visible={showConsent} onComplete={finish} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#0f172a',
    padding: 24,
  },
  button: {
    backgroundColor: '#0ea5e9',
    paddingHorizontal: 32,
    paddingVertical: 16,
    borderRadius: 12,
    marginTop: 32,
  },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '600' },
});
