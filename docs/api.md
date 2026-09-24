# API

All mutating endpoints require an `Idempotency-Key` header (client-generated
UUID, e.g. the Android outbox entry ID). Repeating the same key returns the
cached first response instead of re-applying the write.

## Orders

- `GET /orders?store_id=` — list orders for a store.
- `GET /orders/:id` — order with its items.
- `POST /orders/:id/items/:itemId/pick` — mark an item picked. *(Idempotency-Key required)*
- `POST /orders/:id/items/:itemId/substitute` — `{ substituteProductId }`. *(Idempotency-Key required)*

## Shelves (QR-scan flow)

- `GET /shelves/:qr` — shelf + product + current inventory, driven by the scanned QR code.
- `POST /shelves/:qr/report` — `{ status: "ok" | "low" | "empty" }`. *(Idempotency-Key required)*

## Sync (offline-first)

- `POST /sync/push` — `{ entries: [{ table, op, payload, idempotencyKey }] }`.
- `GET /sync/pull?since=<version>&store_id=` — `{ changes: { inventory, order_items, notifications }, version }`.

## Rules (data-driven business logic)

- `GET /rules` — list rules.
- `POST /rules` — `{ name, triggerTable, cypherQuery, actionType, actionParams, enabled }`.
- `PATCH /rules/:id` — `{ enabled }`.

## Assistant (voice/RAG, text endpoint first)

- `POST /assistant/query` — `{ question, storeId }` → `{ answer, similarProducts, graphFacts }`.
  Combines pgvector similarity search with Neo4j Cypher facts, then asks a local Ollama LLM.

## Customer order simulator

- `POST /customer-orders` — `{ customerId, storeId, productIds }` — creates an order + pending items
  (there is no separate customer app; this backs the small simulator web UI only).
