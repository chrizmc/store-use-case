import type { FastifyInstance } from 'fastify';
import { query } from '../db/pg.js';

// Generic insert used by client-driven notifications that aren't produced by the rule
// engine (e.g. the associate accepting a proactive prompt) - everything rule-driven still
// goes through rules/engine.ts.
export async function notificationsRoutes(app: FastifyInstance) {
  app.post('/notifications', async (req) => {
    const { role, type, payload } = req.body as {
      role: string;
      type: string;
      payload?: Record<string, unknown>;
    };
    const [notification] = await query(
      `INSERT INTO notifications (role, type, payload) VALUES ($1, $2, $3::jsonb) RETURNING *`,
      [role, type, JSON.stringify(payload ?? {})]
    );
    return notification;
  });
}
