import type { FastifyInstance } from 'fastify';
import { query } from '../db/pg.js';

export async function customerOrdersRoutes(app: FastifyInstance) {
  // Backs the small Customer Order Simulator web UI; there is no separate customer app.
  app.post('/customer-orders', async (req) => {
    const { customerId, storeId, productIds } = req.body as {
      customerId: string;
      storeId: string;
      productIds: string[];
    };
    const [order] = await query(
      "INSERT INTO orders (customer_id, store_id, status) VALUES ($1, $2, 'open') RETURNING *",
      [customerId, storeId]
    );
    for (const productId of productIds) {
      await query(
        "INSERT INTO order_items (order_id, product_id, status) VALUES ($1, $2, 'pending')",
        [order.id, productId]
      );
    }
    return order;
  });
}
