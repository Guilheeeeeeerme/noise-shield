import { useEffect, useState } from 'react';
import { Text, StyleSheet } from 'react-native';
import * as Network from 'expo-network';
import { PRIVACY_COPY } from '@noise-shield/shared';

export function OfflineSignInBlock() {
  const [offline, setOffline] = useState(false);

  useEffect(() => {
    Network.getNetworkStateAsync().then((state) => {
      setOffline(!state.isConnected);
    });
  }, []);

  if (!offline) return null;

  return <Text style={styles.banner}>{PRIVACY_COPY.en.offlineSignInBlock}</Text>;
}

const styles = StyleSheet.create({
  banner: {
    backgroundColor: '#422006',
    color: '#fcd34d',
    padding: 12,
    borderRadius: 8,
    textAlign: 'center',
    marginBottom: 8,
  },
});
