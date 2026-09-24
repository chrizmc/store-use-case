import type { FastifyInstance } from 'fastify';
import { query } from '../db/pg.js';
import { withIdempotency } from '../idempotency.js';

export async function shelvesRoutes(app: FastifyInstance) {
  // Drives the QR-scan flow: one shelf holds exactly one product for demo simplicity.
  app.get('/shelves/:qr', async (req) => {
    const { qr } = req.params as { qr: string };
    const [shelf] = await query(
      `SELECT s.*, p.name AS product_name, p.sku, i.qty, i.status AS inventory_status
       FROM shelves s
       JOIN products p ON p.id = s.product_id
       JOIN inventory i ON i.shelf_id = s.id
       WHERE s.qr_code = $1`,
      [qr]
    );
    return shelf ?? { error: 'Shelf not found' };
  });

  app.post('/shelves/:qr/report', async (req, reply) => {
    const { qr } = req.params as { qr: string };
    const { status } = req.body as { status: 'ok' | 'low' | 'empty' };
    return withIdempotency(req, reply, async () => {
      const [row] = await query(
        `UPDATE inventory SET status = $2
         FROM shelves s
         WHERE inventory.shelf_id = s.id AND s.qr_code = $1
         RETURNING inventory.*`,
        [qr, status]
      );
      return row;
    });
  });
}
