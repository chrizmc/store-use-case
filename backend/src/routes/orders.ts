import type { FastifyInstance } from 'fastify';
import { query } from '../db/pg.js';
import { withIdempotency } from '../idempotency.js';

export async function ordersRoutes(app: FastifyInstance) {
  app.get('/orders', async (req) => {
    const { store_id: storeId } = req.query as { store_id?: string };
    return query(
      `SELECT o.*, c.name AS customer_name,
              (SELECT count(*) FROM order_items oi WHERE oi.order_id = o.id AND oi.status = 'pending') AS pending_count
       FROM orders o
       JOIN customers c ON c.id = o.customer_id
       WHERE ($1::uuid IS NULL OR o.store_id = $1)
       ORDER BY o.created_at DESC`,
      [storeId ?? null]
    );
  });

  app.get('/orders/:id', async (req) => {
    const { id } = req.params as { id: string };
    const [order] = await query('SELECT * FROM orders WHERE id = $1', [id]);
    const items = await query(
      `SELECT oi.*, p.name AS product_name, p.sku,
              sub.name AS substituted_with_product_name
       FROM order_items oi
       JOIN products p ON p.id = oi.product_id
       LEFT JOIN products sub ON sub.id = oi.substituted_with_product_id
       WHERE oi.order_id = $1`,
      [id]
    );
    return { ...order, items };
  });

  // Candidate substitutes for an item, filtered to what the store actually has in stock.
  app.get('/orders/:id/items/:itemId/substitutes', async (req) => {
    const { itemId } = req.params as { id: string; itemId: string };
    return query(
      `SELECT p.id AS product_id, p.name, p.sku, sub.score
       FROM order_items oi
       JOIN substitutes sub ON sub.product_id = oi.product_id
       JOIN products p ON p.id = sub.substitute_product_id
       JOIN shelves sh ON sh.product_id = p.id
       JOIN inventory i ON i.shelf_id = sh.id AND i.status != 'empty'
       WHERE oi.id = $1
       ORDER BY sub.score DESC`,
      [itemId]
    );
  });

  app.post('/orders/:id/items/:itemId/pick', async (req, reply) => {
    const { itemId } = req.params as { id: string; itemId: string };
    return withIdempotency(req, reply, async () => {
      const [item] = await query(
        "UPDATE order_items SET status = 'picked' WHERE id = $1 RETURNING *",
        [itemId]
      );
      return item;
    });
  });

  // Called after reporting the item's shelf as empty (see shelves.ts POST /shelves/:qr/report).
  // Updating order_items here is what fires the `suggest_substitute_on_empty` rule, since
  // rules only evaluate when a row on their trigger_table changes (see rules/engine.ts).
  app.post('/orders/:id/items/:itemId/mark-unavailable', async (req, reply) => {
    const { itemId } = req.params as { id: string; itemId: string };
    return withIdempotency(req, reply, async () => {
      const [item] = await query(
        "UPDATE order_items SET status = 'unavailable' WHERE id = $1 RETURNING *",
        [itemId]
      );
      return item;
    });
  });

  app.post('/orders/:id/items/:itemId/substitute', async (req, reply) => {
    const { itemId } = req.params as { id: string; itemId: string };
    const { substituteProductId } = req.body as { substituteProductId: string };
    return withIdempotency(req, reply, async () => {
      const [item] = await query(
        "UPDATE order_items SET status = 'substituted', substituted_with_product_id = $2 WHERE id = $1 RETURNING *",
        [itemId, substituteProductId]
      );
      return item;
    });
  });
}
