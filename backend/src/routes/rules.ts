import type { FastifyInstance } from 'fastify';
import { query } from '../db/pg.js';

// Rules are data (rows in `rules`), not code, so a Rules Admin UI can change
// behaviour without redeploying the backend.
export async function rulesRoutes(app: FastifyInstance) {
  app.get('/rules', async () => query('SELECT * FROM rules ORDER BY name'));

  app.post('/rules', async (req) => {
    const { name, description, triggerTable, cypherQuery, actionType, actionParams, enabled } = req.body as {
      name: string;
      description?: string;
      triggerTable: string;
      cypherQuery: string;
      actionType: string;
      actionParams: Record<string, unknown>;
      enabled?: boolean;
    };
    const [rule] = await query(
      `INSERT INTO rules (name, description, trigger_table, cypher_query, action_type, action_params, enabled)
       VALUES ($1, $2, $3, $4, $5, $6, COALESCE($7, true)) RETURNING *`,
      [name, description ?? null, triggerTable, cypherQuery, actionType, actionParams, enabled ?? null]
    );
    return rule;
  });

  app.patch('/rules/:id', async (req) => {
    const { id } = req.params as { id: string };
    const { name, description, triggerTable, cypherQuery, actionType, actionParams, enabled } =
      req.body as {
        name?: string;
        description?: string;
        triggerTable?: string;
        cypherQuery?: string;
        actionType?: string;
        actionParams?: Record<string, unknown>;
        enabled?: boolean;
      };
    // Every field is optional so the same endpoint serves both the enabled-toggle
    // checkbox and the full "edit rule" form — COALESCE leaves unspecified fields as-is.
    const [rule] = await query(
      `UPDATE rules SET
         name = COALESCE($2, name),
         description = COALESCE($3, description),
         trigger_table = COALESCE($4, trigger_table),
         cypher_query = COALESCE($5, cypher_query),
         action_type = COALESCE($6, action_type),
         action_params = COALESCE($7, action_params),
         enabled = COALESCE($8, enabled),
         updated_at = now()
       WHERE id = $1 RETURNING *`,
      [
        id,
        name ?? null,
        description ?? null,
        triggerTable ?? null,
        cypherQuery ?? null,
        actionType ?? null,
        actionParams ?? null,
        enabled ?? null,
      ]
    );
    return rule;
  });
}
