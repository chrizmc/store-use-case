-- Rules as data: editable later via the Rules Admin UI without redeploying the backend.

-- 1) Deterministic: notify the store manager when a shelf becomes empty or nearly empty.
INSERT INTO rules (name, trigger_table, cypher_query, action_type, action_params) VALUES (
  'notify_manager_on_low_stock',
  'inventory',
  'MATCH (p:Product)-[:STOCKED_AT]->(s:Shelf {id: $entityId}) WHERE s.status IN ["low", "empty"] RETURN p.name AS productName, s.status AS status',
  'notify_manager',
  '{"notificationType": "shelf_low_stock"}'
);

-- 2) Probabilistic: suggest a substitute product, but only if the customer opted in
--    (order_items.entityId here is the OrderItem id; gated by Customer.alternativeOkIfEmpty).
INSERT INTO rules (name, trigger_table, cypher_query, action_type, action_params) VALUES (
  'suggest_substitute_on_empty',
  'order_items',
  'MATCH (oi:OrderItem {id: $entityId})-[:FOR_PRODUCT]->(p:Product)-[:STOCKED_AT]->(s:Shelf)
   MATCH (oi)-[:ORDERED_BY]->(c:Customer {alternativeOkIfEmpty: true})
   WHERE s.status = "empty"
   MATCH (p)-[r:SUBSTITUTE_FOR]->(sub:Product)
   RETURN sub.id AS substituteId ORDER BY r.score DESC LIMIT 1',
  'suggest_substitute',
  '{}'
);

-- 3) Loyalty nudge: customer has not ordered in a while -> suggest a free add-on
--    (the user's own knowledge-graph idea from the initial brainstorm).
INSERT INTO rules (name, trigger_table, cypher_query, action_type, action_params) VALUES (
  'loyalty_addon_suggestion',
  'customers',
  'MATCH (c:Customer {id: $entityId}) WHERE c.lastOrderAt < datetime() - duration("P60D") RETURN c.id AS entityId',
  'loyalty_addon_suggestion',
  '{"suggestedShelfQr": "QR-ST01-CHOCOLATE"}'
);
