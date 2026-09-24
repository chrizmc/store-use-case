import type { FastifyInstance } from 'fastify';
import { query } from '../db/pg.js';

// Rules are data (rows in `rules`), not code, so a Rules Admin UI can change
// behaviour without redeploying the backend.
export async function rulesRoutes(app: FastifyInstance) {
  app.get('/rules', async () => query('SELECT * FROM rules ORDER BY name'));

  app.post('/rules', async (req) => {
    const { name, triggerTable, cypherQuery, actionType, actionParams, enabled } = req.body as {
      name: string;
      triggerTable: string;
      cypherQuery: string;
      actionType: string;
      actionParams: Record<string, unknown>;
      enabled?: boolean;
    };
    const [rule] = await query(
      `INSERT INTO rules (name, trigger_table, cypher_query, action_type, action_params, enabled)
       VALUES ($1, $2, $3, $4, $5, COALESCE($6, true)) RETURNING *`,
      [name, triggerTable, cypherQuery, actionType, actionParams, enabled ?? null]
    );
    return rule;
  });

  app.patch('/rules/:id', async (req) => {
    const { id } = req.params as { id: string };
    const { enabled } = req.body as { enabled: boolean };
    const [rule] = await query('UPDATE rules SET enabled = $2 WHERE id = $1 RETURNING *', [
      id,
      enabled,
    ]);
    return rule;
  });
}
