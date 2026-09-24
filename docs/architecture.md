# Architecture

## System overview

```mermaid
flowchart TB
    subgraph Mocks["Mock SAP Layer (seed data only, clearly labeled)"]
        M1["Mock: SAP Sales Order"]
        M2["Mock: SAP ATP / Inventory"]
        M3["Mock: SAP Customer Profile"]
    end

    subgraph Client["Clients"]
        Android["Android App (Kotlin, Compose)\nRoom DB + Outbox - CameraX/ZXing QR - WorkManager Sync"]
        WebSim["Web UI: Customer Order Simulator"]
        WebRules["Web UI: Rules Admin"]
    end

    subgraph Middleware["Middleware (Node.js + TypeScript, Fastify)"]
        API["REST API (orders, shelves, sync, rules, assistant)"]
        SyncEngine["Sync Engine (outbox / changefeed, per-store)"]
        RuleEngine["Rule Engine (LISTEN/NOTIFY driven)"]
        AIService["AI modules (embeddings, LLM, STT, TTS)"]
    end

    subgraph Data["Data Layer"]
        PG["PostgreSQL 16 + pgvector\n(system of record + embeddings)"]
        Neo["Neo4j Community\n(live-mirrored knowledge graph, rules as data)"]
    end

    subgraph AI["Local AI Runtime (no API keys, no accounts)"]
        Ollama["Ollama (LLM + embeddings)"]
        Whisper["whisper.cpp (speech-to-text)"]
        Piper["Piper (text-to-speech)"]
    end

    Mocks --> PG
    Android <--> SyncEngine
    WebSim --> API
    WebRules --> API
    API --> PG
    API --> Neo
    SyncEngine --> PG
    RuleEngine --> PG
    RuleEngine --> Neo
    AIService --> Ollama
    AIService --> Whisper
    AIService --> Piper
    AIService -->|"pgvector: embed product catalog (seed time)\n+ nearest-product search (query time)"| PG
    AIService -->|"Cypher: what's stocked at this store"| Neo
    API --> AIService
```

## Where pgvector is actually used (query-time mapping)

The vector index only ever answers one question: **which single `products` row is
this free-text question about?** It never decides rule outcomes or substitute
validity — those stay in normal SQL/Cypher lookups against curated data.

```mermaid
sequenceDiagram
    participant Assoc as Associate (voice or text)
    participant Whisper as whisper.cpp
    participant Embed as Ollama (embeddings)
    participant PGV as Postgres + pgvector
    participant Graph as Neo4j
    participant LLM as Ollama (LLM)
    participant Piper as Piper (TTS)

    Assoc->>Whisper: recorded audio (voice) or raw text
    Whisper-->>Assoc: transcript, e.g. "is there a similar product to whole milk?"
    Assoc->>Embed: embed(transcript)
    Embed-->>PGV: question embedding (768-dim vector)
    PGV->>PGV: ORDER BY embedding <=> $question LIMIT 1
    PGV-->>Assoc: likelyProduct = the nearest products row\n(e.g. "Whole Milk 1L")
    Assoc->>PGV: SELECT * FROM substitutes WHERE product_id = likelyProduct.id
    Assoc->>Graph: Cypher: products stocked at this store
    PGV-->>LLM: likelyProduct + substitutes (structured facts)
    Graph-->>LLM: graphFacts (structured facts)
    LLM-->>Assoc: one natural-language sentence, composed only from\nthe facts above (never invents a substitute)
    Assoc->>Piper: synthesize(answer)
    Piper-->>Assoc: spoken reply
```

So a text/voice utterance is never mapped to an "intent" or a "rule" — it is
mapped to **exactly one row in the `products` table**, chosen by nearest-neighbor
distance between the question's embedding and that product's stored embedding.
Everything downstream (substitutes, stock-at-this-store) is a plain lookup keyed
off that one product id; the LLM only turns already-decided facts into a sentence.

## Live graph sync & rule-firing loop

Postgres stays the single source of truth. Neo4j is only ever written to via
idempotent `MERGE`, driven by Postgres change events — it never originates data.

```mermaid
sequenceDiagram
    participant PG as Postgres
    participant RE as Rule Engine (Listener)
    participant NEO as Neo4j
    participant SYNC as Sync Outbox
    participant APP as Android App

    PG->>PG: BEFORE trigger stamps version (global_version_seq)
    PG-->>RE: pg_notify('data_change', {table, id, version})
    RE->>NEO: MERGE node/relationship (idempotent)
    RE->>NEO: Cypher pattern check scoped to the changed entity
    NEO-->>RE: Rule matches -> action (deterministic or vector-based)
    RE->>PG: Writes result back (Notification, order_items.status)
    RE->>PG: INSERT INTO rule_firings (rule_id, entity_id, entity_version)
    PG-->>RE: pg_notify (again, for the result write)
    RE->>SYNC: Result flows through the same outbox mechanism
    SYNC-->>APP: /sync/pull returns the new notification/status
```

`rule_firings` (unique on `rule_id, entity_id, entity_version`) makes rule
evaluation idempotent — replaying the same change event never fires twice.

## Why rules run against Neo4j and not directly against Postgres

Honest answer: the 4 rules seeded in this demo are simple enough that they
*could* be plain SQL against Postgres directly — nothing here strictly requires
a graph database. Neo4j is used anyway, deliberately, for three reasons:

1. **The pattern needs to survive rule growth, not just today's 4 rules.**
   `suggest_substitute_on_empty` already chains 3 relationship hops
   (`OrderItem → Product → Shelf`, `OrderItem → Customer`,
   `Product -[:SUBSTITUTE_FOR]-> Product`) in one readable Cypher pattern.
   Real substitute/recommendation logic tends to grow into *variable-length*
   traversals ("find a substitute of a substitute", "what do similar customers
   buy instead") — trivial to add a hop to in Cypher, increasingly painful as
   nested joins/recursive CTEs in SQL.
2. **Separation of write path from reasoning path.** Postgres stays the only
   place anything is ever written first (see diagram above) — Neo4j is a
   disposable, rebuildable *read-side* projection. That means rule evaluation
   (arbitrary Cypher, potentially expensive graph traversal) never competes
   with or risks the transactional write path; you can wipe and rebuild the
   whole graph from Postgres at any time with zero data loss.
3. **It's the direct analogue of a real SAP capability.** This maps 1:1 to
   HANA's Graph Engine sitting alongside its relational tables in the same
   database — the interview point isn't "Neo4j is required," it's "business
   rules expressed as graph patterns over live-synced data, stored as data
   (the `rules` table) instead of hardcoded `if` statements, so a rule change
   is a Rules Admin edit, not a redeploy."

If asked directly: *"for exactly these 4 rules, SQL would work fine — Neo4j
earns its place once relationship depth/variability grows, and as a clean
separation between transactional writes and read-side reasoning."*

## Production-replacement mapping (for interview slides)

| Demo component (now) | OSS production alternative | SAP / enterprise alternative |
|---|---|---|
| pgvector | Qdrant / Milvus / Weaviate | SAP HANA Cloud Vector Engine |
| Neo4j Community | Neo4j Cluster / Aura | SAP HANA Graph Engine |
| Custom outbox sync | ElectricSQL, PowerSync | SAP Mobile Services (Offline OData Sync) |
| `pg_notify`/`LISTEN` as event bus | Kafka, NATS, Redpanda | SAP Event Mesh |
| Ollama (local LLM) | vLLM / TGI behind a load balancer | SAP AI Core / Generative AI Hub |
| Node.js single-process backend | Horizontally replicated stateless API behind a load balancer | SAP BTP (Cloud Foundry / Kyma) |

## Scalability notes (not implemented, talking points only)

- API is stateless → horizontally replicable.
- Sync is partitioned per `store_id` → no single-writer bottleneck.
- Rule evaluation is scoped to the changed entity's local Cypher pattern, not a full-graph scan.
- AI modules are decoupled from the transactional API path, so heavy inference doesn't block writes.
