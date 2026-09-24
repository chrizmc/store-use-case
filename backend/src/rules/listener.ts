import { Client } from 'pg';
import 'dotenv/config';
import { syncEntityToNeo4j } from './graphSync.js';
import { evaluateRulesFor } from './engine.js';

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
    const change = JSON.parse(msg.payload) as { table: string; id: string; version: number };
    try {
      await syncEntityToNeo4j(change.table, change.id);
      await evaluateRulesFor(change.table, change.id, change.version);
    } catch (err) {
      console.error('[rule-listener] failed to process change', change, err);
    }
  });

  console.log('[rule-listener] listening on data_change');
}
