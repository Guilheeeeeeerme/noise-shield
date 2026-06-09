import { Audio } from 'expo-av';

export async function requestMicrophonePermission(): Promise<boolean> {
  const { status } = await Audio.requestPermissionsAsync();
  return status === 'granted';
}

export async function checkMicrophonePermission(): Promise<boolean> {
  const { status } = await Audio.getPermissionsAsync();
  return status === 'granted';
}
