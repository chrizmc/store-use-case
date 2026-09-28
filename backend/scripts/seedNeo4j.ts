import { query } from '../src/db/pg.js';
import { runCypher } from '../src/db/neo4j.js';
import { syncEntityToNeo4j } from '../src/rules/graphSync.js';

// Full graph rebuild: static entities (products, substitutes, shelves, stores) plus a
// one-time backfill of dynamic entities (inventory, customers, order_items) via the same
// syncEntityToNeo4j() used by the live rule listener. Needed because SQL seed files insert
// rows directly in Postgres -- if that happens before the backend (and its LISTEN/NOTIFY
// listener) is running, those pg_notify events are lost and Neo4j never mirrors them.
// Safe to re-run any time (idempotent MERGE-only), e.g. after wiping Neo4j's data directory.
async function main() {
  const stores = await query('SELECT id, name, address FROM stores');
  for (const s of stores) {
    await runCypher('MERGE (st:Store {id: $id}) SET st.name = $name, st.address = $address', s);
  }

  const products = await query('SELECT id, sku, name FROM products');
  for (const p of products) {
    await runCypher('MERGE (p:Product {id: $id}) SET p.sku = $sku, p.name = $name', p);
  }

  const substitutes = await query(
    'SELECT product_id, substitute_product_id, score FROM substitutes'
  );
  for (const s of substitutes) {
    await runCypher(
      `MATCH (p:Product {id: $product_id}), (sub:Product {id: $substitute_product_id})
       MERGE (p)-[r:SUBSTITUTE_FOR]->(sub) SET r.score = $score`,
      s
    );
  }

  const shelves = await query('SELECT id, store_id, product_id, qr_code, aisle FROM shelves');
  for (const sh of shelves) {
    await runCypher(
      `MATCH (p:Product {id: $product_id}), (st:Store {id: $store_id})
       MERGE (s:Shelf {id: $id}) SET s.qrCode = $qr_code, s.aisle = $aisle
       MERGE (p)-[:STOCKED_AT]->(s)
       MERGE (s)-[:LOCATED_IN]->(st)`,
      sh
    );
  }

  const customerIds = await query<{ id: string }>('SELECT id FROM customers');
  for (const c of customerIds) {
    await syncEntityToNeo4j('customers', c.id);
  }

  const shelfIds = await query<{ shelf_id: string }>('SELECT shelf_id FROM inventory');
  for (const i of shelfIds) {
    await syncEntityToNeo4j('inventory', i.shelf_id);
  }

  const orderItemIds = await query<{ id: string }>('SELECT id FROM order_items');
  for (const oi of orderItemIds) {
    await syncEntityToNeo4j('order_items', oi.id);
  }

  console.log('Neo4j graph rebuild complete.');
  process.exit(0);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
