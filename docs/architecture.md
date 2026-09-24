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
    AIService --> PG
    AIService --> Neo
    API --> AIService
```

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
