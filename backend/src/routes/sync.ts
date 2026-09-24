import type { FastifyInstance } from 'fastify';
import { query } from '../db/pg.js';

const SYNCABLE_TABLES = ['inventory', 'order_items', 'orders', 'notifications'] as const;

type SyncEntry = {
  table: string;
  op: 'insert' | 'update';
  payload: Record<string, unknown> & { id?: string; deviceId?: string };
  idempotencyKey: string;
};

export async function syncRoutes(app: FastifyInstance) {
  // Offline-first push: each entry carries a client-generated idempotency key so retries are no-ops.
  // NOTE (known simplification, see README "Known simplifications"): entries are recorded in
  // sync_outbox for audit/idempotency; applying arbitrary payloads generically to their target
  // table is a Phase 5 follow-up. Today, writes go through the dedicated REST routes.
  app.post('/sync/push', async (req) => {
    const { entries } = req.body as { entries: SyncEntry[] };

    const results = [];
    for (const entry of entries) {
      if (!SYNCABLE_TABLES.includes(entry.table as (typeof SYNCABLE_TABLES)[number])) continue;

      const existing = await query('SELECT 1 FROM sync_outbox WHERE idempotency_key = $1', [
        entry.idempotencyKey,
      ]);
      if (existing.length > 0) {
        results.push({ idempotencyKey: entry.idempotencyKey, status: 'duplicate-ignored' });
        continue;
      }

      await query(
        `INSERT INTO sync_outbox (device_id, entity, entity_id, op, payload, idempotency_key)
         VALUES ($1, $2, $3, $4, $5, $6)`,
        [
          entry.payload.deviceId ?? 'unknown',
          entry.table,
          entry.payload.id ?? null,
          entry.op,
          entry.payload,
          entry.idempotencyKey,
        ]
      );
      results.push({ idempotencyKey: entry.idempotencyKey, status: 'applied' });
    }
    return { results };
  });

  app.get('/sync/pull', async (req) => {
    const { since, store_id: storeId } = req.query as { since?: string; store_id?: string };
    const sinceVersion = Number(since ?? 0);

    const changes = {
      inventory: await query(
        `SELECT i.* FROM inventory i JOIN shelves s ON s.id = i.shelf_id
         WHERE i.version > $1 AND ($2::uuid IS NULL OR s.store_id = $2)`,
        [sinceVersion, storeId ?? null]
      ),
      order_items: await query(
        `SELECT oi.* FROM order_items oi JOIN orders o ON o.id = oi.order_id
         WHERE oi.version > $1 AND ($2::uuid IS NULL OR o.store_id = $2)`,
        [sinceVersion, storeId ?? null]
      ),
      notifications: await query('SELECT * FROM notifications WHERE version > $1', [sinceVersion]),
    };

    const [{ max: maxVersion }] = await query<{ max: number }>(
      `SELECT GREATEST(
         (SELECT COALESCE(MAX(version), 0) FROM inventory),
         (SELECT COALESCE(MAX(version), 0) FROM order_items),
         (SELECT COALESCE(MAX(version), 0) FROM notifications)
       ) AS max`
    );

    return { changes, version: maxVersion ?? sinceVersion };
  });
}
