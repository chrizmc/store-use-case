import { Client } from 'pg';
import 'dotenv/config';
import { syncEntityToNeo4j } from './graphSync.js';
import { evaluateRulesFor } from './engine.js';

export interface DataChange {
  table: string;
  op: string;
  id: string;
  version: number;
}

const subscribers = new Set<(change: DataChange) => void>();

// Lets other parts of the server (e.g. the /events/orders SSE route) react to the same
// Postgres NOTIFY stream the rule engine already listens to, without a second LISTEN connection.
export function subscribeToDataChanges(fn: (change: DataChange) => void): () => void {
  subscribers.add(fn);
  return () => subscribers.delete(fn);
}

// Bridges Postgres changes into the live Neo4j mirror and re-evaluates affected rules.
// See docs/architecture.md for the full sequence diagram of this loop.
export async function startRuleListener() {
  const client = new Client({
    host: process.env.PGHOST ?? 'localhost',
    port: Number(process.env.PGPORT ?? 5432),
    user: process.env.PGUSER ?? 'postgres',
    password: process.env.PGPASSWORD ?? '',
    database: process.env.PGDATABASE ?? 'bopis',
  });
  await client.connect();
  await client.query('LISTEN data_change');

  client.on('notification', async (msg) => {
    if (!msg.payload) return;
    const change = JSON.parse(msg.payload) as DataChange;
    for (const fn of subscribers) fn(change);
    try {
      await syncEntityToNeo4j(change.table, change.id);
      await evaluateRulesFor(change.table, change.id, change.version);
    } catch (err) {
      console.error('[rule-listener] failed to process change', change, err);
    }
  });

  console.log('[rule-listener] listening on data_change');
}

