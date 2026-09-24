import type { FastifyInstance } from 'fastify';
import { query } from '../db/pg.js';

// Read-only lookups backing the Customer Order Simulator UI's dropdowns.
export async function catalogRoutes(app: FastifyInstance) {
  app.get('/customers', async () => query('SELECT id, name FROM customers ORDER BY name'));
  app.get('/products', async () => query('SELECT id, name, sku FROM products ORDER BY name'));
  app.get('/stores', async () => query('SELECT id, name FROM stores ORDER BY name'));
}
