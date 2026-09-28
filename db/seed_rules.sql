-- Rules as data: editable later via the Rules Admin UI without redeploying the backend.

-- 1) Deterministic: notify the store manager when a shelf becomes empty or nearly empty.
INSERT INTO rules (name, description, trigger_table, cypher_query, action_type, action_params) VALUES (
  'notify_manager_on_low_stock',
  'When a shelf is reported low or empty, tell the store manager which product and shelf need restocking.',
  'inventory',
  'MATCH (p:Product)-[:STOCKED_AT]->(s:Shelf {id: $entityId}) WHERE s.status IN ["low", "empty"] RETURN p.name AS productName, s.status AS status',
  'notify_manager',
  '{"notificationType": "shelf_low_stock"}'
);

-- 2) Probabilistic: suggest a substitute product, but only if the customer opted in
--    (order_items.entityId here is the OrderItem id; gated by Customer.alternativeOkIfEmpty).
INSERT INTO rules (name, description, trigger_table, cypher_query, action_type, action_params) VALUES (
  'suggest_substitute_on_empty',
  'If an ordered item''s shelf is empty and the customer said substitutions are OK, suggest the best-scoring known substitute product to the associate.',
  'order_items',
  'MATCH (oi:OrderItem {id: $entityId})-[:FOR_PRODUCT]->(p:Product)-[:STOCKED_AT]->(s:Shelf)
   MATCH (oi)-[:ORDERED_BY]->(c:Customer {alternativeOkIfEmpty: true})
   WHERE s.status = "empty"
   MATCH (s)-[:LOCATED_IN]->(st:Store)
   MATCH (p)-[r:SUBSTITUTE_FOR]->(sub:Product)
   MATCH (sub)-[:STOCKED_AT]->(subShelf:Shelf)-[:LOCATED_IN]->(st)
   RETURN sub.id AS substituteId, sub.name AS substituteName, subShelf.aisle AS aisle ORDER BY r.score DESC LIMIT 1',
  'suggest_substitute',
  '{}'
);

-- 3) Loyalty nudge: customer has not ordered in a while -> suggest a free add-on
--    (the user's own knowledge-graph idea from the initial brainstorm).
INSERT INTO rules (name, description, trigger_table, cypher_query, action_type, action_params) VALUES (
  'loyalty_addon_suggestion',
  'If a customer hasn''t ordered in over 60 days, suggest the associate add a free item to their next pickup as a small loyalty gesture.',
  'customers',
  'MATCH (c:Customer {id: $entityId}) WHERE c.lastOrderAt < datetime() - duration("P60D") RETURN c.id AS entityId',
  'loyalty_addon_suggestion',
  '{"suggestedShelfQr": "QR-ST01-CHOCOLATE"}'
);

-- 4) Deterministic: when a shelf runs out, proactively check whether the same
--    product is in stock at another store nearby, so the associate can tell the
--    customer where to find it instead of just reporting "unavailable".
INSERT INTO rules (name, description, trigger_table, cypher_query, action_type, action_params) VALUES (
  'suggest_other_store_on_empty',
  'When a shelf becomes empty, check if any other store still has the same product in stock, and if so, tell the associate which store and how many are available.',
  'inventory',
  'MATCH (p:Product)-[:STOCKED_AT]->(s:Shelf {id: $entityId}) WHERE s.status = "empty"
   MATCH (p)-[:STOCKED_AT]->(s2:Shelf)-[:LOCATED_IN]->(st2:Store)
   WHERE s2.id <> s.id AND s2.status <> "empty"
   RETURN st2.name AS storeName, st2.address AS storeAddress, s2.qty AS qty ORDER BY s2.qty DESC LIMIT 1',
  'suggest_other_store',
  '{}'
);
