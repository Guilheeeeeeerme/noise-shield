import * as SQLite from 'expo-sqlite';

const db = SQLite.openDatabaseSync('noise_shield_sync.db');

export type SyncEntityType = 'preference' | 'favorite' | 'feedback' | 'consent' | 'feature';

export interface SyncQueueEntry {
  id: string;
  entity_type: SyncEntityType;
  payload: string;
  created_at: string;
  retry_count: number;
}

export function initSyncQueue(): void {
  db.execSync(`
    CREATE TABLE IF NOT EXISTS sync_queue (
      id TEXT PRIMARY KEY,
      entity_type TEXT NOT NULL,
      payload TEXT NOT NULL,
      created_at TEXT NOT NULL,
      retry_count INTEGER NOT NULL DEFAULT 0
    );
    CREATE INDEX IF NOT EXISTS idx_sync_queue_created ON sync_queue(created_at);
  `);
}

export function enqueue(entityType: SyncEntityType, payload: Record<string, unknown>): void {
  initSyncQueue();
  db.runSync(
    'INSERT INTO sync_queue (id, entity_type, payload, created_at, retry_count) VALUES (?, ?, ?, ?, 0)',
    [crypto.randomUUID(), entityType, JSON.stringify(payload), new Date().toISOString()],
  );
}

export function dequeue(): SyncQueueEntry | null {
  initSyncQueue();
  const rows = db.getAllSync<SyncQueueEntry>(
    'SELECT * FROM sync_queue ORDER BY created_at ASC LIMIT 1',
  );
  return rows[0] ?? null;
}

export function remove(id: string): void {
  db.runSync('DELETE FROM sync_queue WHERE id = ?', [id]);
}

export function incrementRetry(id: string): void {
  db.runSync('UPDATE sync_queue SET retry_count = retry_count + 1 WHERE id = ?', [id]);
}
