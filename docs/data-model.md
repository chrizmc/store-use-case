# Data model

Postgres is the only source of truth. Neo4j nodes/relationships below are a
derived, live-synced mirror of the same entities — never written to directly
by clients.

## Entities (Postgres)

```
stores(id, name, code)
products(id, sku, name, description, embedding vector(768))
substitutes(product_id, substitute_product_id, score)
shelves(id, store_id, product_id, qr_code, aisle)        -- one shelf = one product slot
inventory(shelf_id PK, qty, status[ok|low|empty], version, updated_at)
customers(id, name, alternative_ok_if_empty, last_order_at, version, updated_at)
orders(id, customer_id, store_id, status[open|ready_for_pickup|completed], version, ...)
order_items(id, order_id, product_id, status[pending|picked|substituted|unavailable],
            substituted_with_product_id, version, updated_at)
notifications(id, role[associate|manager|customer], type, payload jsonb, read_at, version, created_at)
rules(id, name, trigger_table, cypher_query, action_type, action_params jsonb, enabled)
rule_firings(id, rule_id, entity_id, entity_version)      -- idempotency for rule evaluation
sync_outbox(id, device_id, entity, entity_id, op, payload, idempotency_key)
processed_requests(idempotency_key PK, response, created_at) -- idempotency for direct API calls
```

`version` columns are stamped from a single Postgres sequence
(`global_version_seq`), giving a global logical clock so `/sync/pull?since=`
works consistently across tables.

## Neo4j graph shape (derived)

```
(:Store)<-[:LOCATED_IN]-(:Shelf)<-[:STOCKED_AT]-(:Product)
(:Product)-[:SUBSTITUTE_FOR {score}]->(:Product)
(:OrderItem)-[:FOR_PRODUCT]->(:Product)
(:OrderItem)-[:ORDERED_BY]->(:Customer)
```

## Why shelf = 1 product

Keeps the associate flow trivial: scan shelf QR → shelf already implies the
product and its inventory row → action buttons (`Picked Up`, `Is Empty`,
`Is Nearly Empty`) act on that single inventory row. No product picker UI needed.
