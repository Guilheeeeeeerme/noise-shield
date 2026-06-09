export interface LwwEntry {
  key: string;
  value: unknown;
  server_received_at: string;
}

export function mergeLww(
  local: LwwEntry[],
  remote: LwwEntry[],
): LwwEntry[] {
  const map = new Map<string, LwwEntry>();

  for (const entry of local) {
    map.set(entry.key, entry);
  }

  for (const entry of remote) {
    const existing = map.get(entry.key);
    if (!existing) {
      map.set(entry.key, entry);
      continue;
    }
    const localTime = new Date(existing.server_received_at).getTime();
    const remoteTime = new Date(entry.server_received_at).getTime();
    if (remoteTime >= localTime) {
      map.set(entry.key, entry);
    }
  }

  return Array.from(map.values());
}
