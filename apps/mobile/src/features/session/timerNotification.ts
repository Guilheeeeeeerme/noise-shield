import * as Notifications from 'expo-notifications';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

export async function scheduleTimerNotification(endAt: Date): Promise<string> {
  const { status } = await Notifications.requestPermissionsAsync();
  if (status !== 'granted') return '';

  return Notifications.scheduleNotificationAsync({
    content: {
      title: 'Noise Shield',
      body: 'Your masking session timer has ended.',
    },
    trigger: {
      type: Notifications.SchedulableTriggerInputTypes.DATE,
      date: endAt,
    },
  });
}

export async function cancelTimerNotification(id: string): Promise<void> {
  if (id) await Notifications.cancelScheduledNotificationAsync(id);
}
