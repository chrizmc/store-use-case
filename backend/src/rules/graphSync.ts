import { query } from '../db/pg.js';
import { runCypher } from '../db/neo4j.js';

// Keeps Neo4j as a live, idempotent mirror of the Postgres rows relevant for reasoning.
// Only ever MERGEs — Neo4j never originates data, Postgres remains the source of truth.
export async function syncEntityToNeo4j(table: string, id: string) {
  if (table === 'inventory') {
    const [row] = await query(
      `SELECT i.shelf_id, i.qty, i.status, s.qr_code, s.product_id, s.store_id, st.name AS store_name
       FROM inventory i
       JOIN shelves s ON s.id = i.shelf_id
       JOIN stores st ON st.id = s.store_id
       WHERE i.shelf_id = $1`,
      [id]
    );
    if (!row) return;
    // A Store node only ever appears in the graph once one of its shelves syncs here —
    // stores with no seeded shelves/inventory (e.g. a brand-new store) won't show up yet.
    await runCypher(
      `MERGE (s:Shelf {id: $shelfId})
       SET s.qrCode = $qrCode, s.status = $status, s.qty = $qty
       MERGE (p:Product {id: $productId})
       MERGE (st:Store {id: $storeId})
       SET st.name = $storeName
       MERGE (p)-[:STOCKED_AT]->(s)
       MERGE (s)-[:LOCATED_IN]->(st)`,
      {
        shelfId: row.shelf_id,
        qrCode: row.qr_code,
        status: row.status,
        qty: row.qty,
        productId: row.product_id,
        storeId: row.store_id,
        storeName: row.store_name,
      }
    );
    return;
  }

  if (table === 'order_items') {
    const [row] = await query(
      `SELECT oi.id, oi.status, oi.product_id, oi.order_id, o.customer_id
       FROM order_items oi JOIN orders o ON o.id = oi.order_id WHERE oi.id = $1`,
      [id]
    );
    if (!row) return;
    await runCypher(
      `MERGE (oi:OrderItem {id: $id})
       SET oi.status = $status
       MERGE (p:Product {id: $productId})
       MERGE (c:Customer {id: $customerId})
       MERGE (oi)-[:FOR_PRODUCT]->(p)
       MERGE (oi)-[:ORDERED_BY]->(c)`,
      { id: row.id, status: row.status, productId: row.product_id, customerId: row.customer_id }
    );
    return;
  }

  if (table === 'customers') {
    const [row] = await query(
      'SELECT id, alternative_ok_if_empty, last_order_at FROM customers WHERE id = $1',
      [id]
    );
    if (!row) return;
    await runCypher(
      'MERGE (c:Customer {id: $id}) SET c.alternativeOkIfEmpty = $ok, c.lastOrderAt = $lastOrderAt',
      {
        id: row.id,
        ok: row.alternative_ok_if_empty,
        lastOrderAt: row.last_order_at?.toISOString() ?? null,
      }
    );
  }
}
