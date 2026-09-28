import { query } from '../db/pg.js';
import { runCypher } from '../db/neo4j.js';

type Rule = {
  id: string;
  name: string;
  cypher_query: string;
  action_type: string;
  action_params: Record<string, unknown>;
};

type GraphRecord = { toObject: () => Record<string, unknown> };

// Rules are data (rows in `rules`), not code, so behaviour changes without a redeploy.
export async function evaluateRulesFor(table: string, entityId: string, entityVersion: number) {
  const rules = await query<Rule>('SELECT * FROM rules WHERE trigger_table = $1 AND enabled = true', [
    table,
  ]);

  for (const rule of rules) {
    const alreadyFired = await query(
      'SELECT 1 FROM rule_firings WHERE rule_id = $1 AND entity_id = $2 AND entity_version = $3',
      [rule.id, entityId, entityVersion]
    );
    if (alreadyFired.length > 0) continue;

    const matches = await runCypher(rule.cypher_query, { entityId });
    if (matches.length === 0) continue;

    await runAction(rule.action_type, rule.action_params, entityId, matches);
    await query(
      'INSERT INTO rule_firings (rule_id, entity_id, entity_version) VALUES ($1, $2, $3) ON CONFLICT DO NOTHING',
      [rule.id, entityId, entityVersion]
    );
  }
}

async function runAction(
  actionType: string,
  params: Record<string, unknown>,
  entityId: string,
  matches: GraphRecord[]
) {
  const first = matches[0]?.toObject();

  if (actionType === 'notify_manager') {
    await query("INSERT INTO notifications (role, type, payload) VALUES ('manager', $1, $2)", [
      params.notificationType ?? 'shelf_alert',
      JSON.stringify({ entityId, ...first }),
    ]);
  }

  if (actionType === 'suggest_substitute') {
    const substituteId = first?.substituteId;
    if (!substituteId) return;
    // Guarded by IS DISTINCT FROM: this action writes to order_items, its own trigger
    // table. Without the guard, every write would re-fire notify_data_change with a new
    // version, re-matching this same rule forever (a real infinite loop hit in testing).
    const [updated] = await query(
      `UPDATE order_items SET substituted_with_product_id = $2
       WHERE id = $1 AND substituted_with_product_id IS DISTINCT FROM $2
       RETURNING id`,
      [entityId, substituteId]
    );
    if (!updated) return;
    await query(
      "INSERT INTO notifications (role, type, payload) VALUES ('associate', 'substitute_suggested', $1)",
      [JSON.stringify({
        orderItemId: entityId,
        substituteId,
        substituteName: first?.substituteName,
        aisle: first?.aisle,
      })]
    );
  }

  if (actionType === 'loyalty_addon_suggestion') {
    await query(
      "INSERT INTO notifications (role, type, payload) VALUES ('associate', 'loyalty_addon', $1)",
      [JSON.stringify({ entityId, ...params, ...first })]
    );
  }

  if (actionType === 'suggest_other_store') {
    const storeName = first?.storeName;
    if (!storeName) return;
    await query(
      "INSERT INTO notifications (role, type, payload) VALUES ('associate', 'available_at_other_store', $1)",
      [JSON.stringify({
        shelfId: entityId,
        storeName,
        storeAddress: first?.storeAddress,
        qty: first?.qty,
      })]
    );
  }
}
