import type { FastifyInstance } from 'fastify';
import { subscribeToDataChanges } from '../rules/listener.js';

const RELEVANT_TABLES = new Set(['orders', 'order_items']);

// Lets the Android app react the instant an order is created/updated (e.g. via the
// Customer Order Simulator) instead of relying on manual refresh or slow polling.
export async function eventsRoutes(app: FastifyInstance) {
  app.get('/events/orders', (req, reply) => {
    reply.raw.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    });
    reply.raw.write('retry: 2000\n\n');

    const unsubscribe = subscribeToDataChanges((change) => {
      if (!RELEVANT_TABLES.has(change.table)) return;
      reply.raw.write(`data: ${JSON.stringify(change)}\n\n`);
    });

    // Keeps the connection alive through idle proxies/timeouts between real events.
    const heartbeat = setInterval(() => reply.raw.write(': ping\n\n'), 15000);

    req.raw.on('close', () => {
      clearInterval(heartbeat);
      unsubscribe();
    });
  });
}
