# BOPIS Store Associate App — SAP Interview Demo

Buy-Online-Pick-Up-In-Store demo for store associates. Postgres is the single
source of truth; Neo4j is a live-synced reasoning layer that fires
data-driven rules (business logic as data, not code). Everything runs
locally via the command line — no Docker, no external accounts/API keys.

**Not for production** — built to demonstrate an engineering approach for a
technical interview.

## Mock SAP mapping (transparency)

| Mock in this repo | Stands in for (potential real system) |
|---|---|
| `stores` / `shelves` seed data | SAP S/4HANA Site / Store Master |
| `products` seed data | SAP Material Master |
| `inventory` | SAP Available-To-Promise (ATP) / Inventory Management |
| `customers` seed data | SAP Customer Data Cloud (CDC) / Customer Profile |
| `orders` / `order_items` | SAP S/4HANA Sales Order Management |

## Architecture at a glance

- **Postgres 16 + pgvector** — system of record + embeddings for similarity search.
- **Neo4j Community** — live-mirrored knowledge graph; rules stored as data (`rules` table), evaluated via Cypher.
- **Node.js + TypeScript (Fastify)** — middleware API, sync engine, rule engine.
- **Ollama** — local LLM + embeddings (no API key, no cloud account).
- **whisper.cpp** — local speech-to-text.
- **Piper** — local text-to-speech.
- **Android (Kotlin/Compose)** — offline-first store associate app (QR scan → pick/report shelf,
  Orders/OrderDetail, Notifications, voice Assistant), runs in the emulator or a Pixel 6a.
- **Two small web UIs** — Customer Order Simulator (`/simulator.html`) and Rules Admin
  (`/rules.html`), served directly by Fastify as static HTML/vanilla JS (no build step, no
  separate customer app — the shared basis is the database).

See [docs/architecture.md](docs/architecture.md) for diagrams and the
production-replacement mapping (for interview slides), and
[docs/data-model.md](docs/data-model.md) / [docs/api.md](docs/api.md) for
the data model and API contract.

## Prerequisites (macOS, Homebrew, no Docker)

```bash
brew install postgresql@17 pgvector neo4j ollama whisper-cpp node
brew services start postgresql@17
ollama serve &
ollama pull llama3.2
ollama pull nomic-embed-text
neo4j start
```

For the Android app you additionally need a JDK 17+, the Android command-line
tools, and Gradle — see [Android setup](#android-setup-emulator--build) below.

pgvector's Homebrew bottle is built against PostgreSQL 17/18 — use `postgresql@17`,
not `@16` (the extension `.control` file won't be found otherwise).

Piper TTS: the Homebrew formula is unreliable; install via pip instead and
download a voice model once:

```bash
python3 -m pip install piper-tts
python3 -m piper.download_voices --download-dir backend/models en_US-lessac-medium
```

## Setup

```bash
createdb bopis
cd backend
cp .env.example .env   # then set PGUSER to $(whoami) and clear PGPASSWORD (Homebrew Postgres uses trust auth)
npm install
npm run migrate      # applies db/schema.sql
npm run seed         # applies db/seed_mock_sap.sql + db/seed_rules.sql
npm run neo4j:init   # applies neo4j/constraints.cypher
npm run seed:neo4j   # one-time static graph population (products, substitutes, shelves, stores)
npm run dev          # starts the API on :3000 and the rule listener
```

Neo4j requires a one-time password change on first login before `neo4j:init` will work:

```bash
neo4j stop
neo4j-admin dbms set-initial-password <your-password>   # must run before the very first start
neo4j start
```

Health check: `curl localhost:3000/health`

Backend must always be started with stdin redirected from `/dev/null`, otherwise
`tsx watch`'s stdin-keypress listener can get the process suspended (`SIGTTIN`)
the moment it's backgrounded — it looks like a hang (TCP connects, never
responds) but is actually a stopped process:

```bash
nohup npm run dev < /dev/null > /tmp/backend.log 2>&1 & disown
```

Once running, open in a browser:

- `http://localhost:3000/` — landing page linking to both admin UIs
- `http://localhost:3000/rules.html` — **Rules Admin**: view/enable-disable/create rules
  (business logic as data — no redeploy needed to change behaviour)
- `http://localhost:3000/simulator.html` — **Customer Order Simulator**: create a customer
  order (there is no separate customer-facing app; the database is the shared basis)
- `http://localhost:7474` — Neo4j Browser (the live reasoning/knowledge-graph layer)

## Android setup (emulator + build)

```bash
brew install --cask android-commandlinetools temurin17
brew install gradle
export JAVA_HOME=/opt/homebrew/opt/openjdk@25   # or your JDK 17+ install
SDK_ROOT=/opt/homebrew/share/android-commandlinetools

# install a stable platform + system image (avoid the bleeding-edge preview
# platform Android Studio's first-run wizard may pull, which newer AGP can't parse)
sdkmanager --sdk_root="$SDK_ROOT" "platforms;android-35" \
  "system-images;android-35;google_apis;arm64-v8a"

# create the AVD (answer "no" to the custom hardware-profile prompt; --device
# throws a bogus devices.xml error on this tool version)
echo no | avdmanager create avd -n bopis_demo \
  -k "system-images;android-35;google_apis;arm64-v8a" --sdk_root="$SDK_ROOT"

# boot the emulator, detached so it survives the terminal
nohup ~/Library/Android/sdk/emulator/emulator -avd bopis_demo \
  -no-boot-anim -no-snapshot > /tmp/emulator.log 2>&1 & disown

# build + install + launch the app (from android/)
cd android
gradle assembleDebug
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
~/Library/Android/sdk/platform-tools/adb shell am start -n com.bopis.associate/.MainActivity
```

The emulator reaches the host's backend at `10.0.2.2:3000` automatically —
no extra networking config needed. A real Pixel 6a works the same way over
USB debugging, except the app's base URL must point at the Mac's LAN IP
instead of `10.0.2.2`.

## Demo script (what to have open, and in what order)

For a live Teams call, open everything **before** the call starts so nothing
needs to load on camera, and keep it all visible/switchable via
Cmd+Tab / browser tabs throughout — the point of the demo is to show the data
flowing between screens, not to narrate slides:

1. **Android emulator window** — app open on the Orders screen.
2. **Browser tab** — `/simulator.html` (Customer Order Simulator).
3. **Browser tab** — `/rules.html` (Rules Admin).
4. **Browser tab** — Neo4j Browser (`localhost:7474`), with a Cypher query like
   `MATCH (r) RETURN r LIMIT 50` ready to re-run to show the live graph.
5. **Terminal** — `tail -f /tmp/backend.log` visible in a corner, so rule firings
   and sync requests scroll live as you interact with the app.

Suggested walkthrough:

1. In the **Customer Order Simulator**, create a new order for a customer/store/product.
2. Switch to the **Android app** → pull-to-refresh (or restart) the Orders list → the new
   order appears (proves the DB, not a bespoke customer app, is the shared source of truth).
3. Open the order → scan a shelf QR code (or tap through if the emulator has no camera feed)
   → tap **Picked Up** / **Is Empty** / **Is Nearly Empty**.
4. If **Is Empty**: the app shows a suggested substitute (rule-fired, only offered because
   the customer's `alternativeOkIfEmpty` flag is set) — flip to the **backend log tab** to show
   the rule firing in real time, then to **Neo4j Browser** to show the underlying graph query.
5. If stock is low: flip to the **Notifications** screen to show the store-manager notification
   that fired from the same rule engine.
6. In **Rules Admin**, disable the substitute-suggestion rule live, repeat step 3 on a different
   item, and show the suggestion no longer appears — demonstrating behaviour change with zero
   redeploy (rules are data, not code).
7. Open the **Assistant** tab, tap record, ask "is there a similar product to X?" — shows local
   speech-to-text → RAG-over-the-graph → LLM answer, fully offline/local.

## Status

- [x] Phase 1 — Data model, mock-SAP seeds, Neo4j constraints
- [x] Phase 2 — Middleware core API (orders, shelves, sync, rules, customer-orders)
- [x] Phase 3 — Android core loop app (Orders, OrderDetail, shelf QR-scan actions, Notifications)
- [x] Phase 4 — Live Postgres → Neo4j sync + data-driven rule engine
- [x] Phase 5 — Offline-first bidirectional sync (Room + WorkManager outbox/pull)
- [x] Phase 6a — Local AI building blocks (embeddings, LLM, STT, TTS) wired end-to-end
      (voice Assistant screen: record → whisper.cpp → RAG over Neo4j/pgvector → Ollama → reply)
- [x] Phase 6b — Rules Admin UI + Customer Order Simulator UI
- [x] Phase 7 — Demo polish & docs (this README)

## Known simplifications (call these out explicitly in the interview)

- One shelf = exactly one product slot (keeps the QR-scan flow trivial).
- `/sync/push` currently logs entries to `sync_outbox` for audit/idempotency;
  applying arbitrary entity payloads generically is a Phase 5 follow-up —
  today, writes go through the dedicated REST routes (`pick`, `substitute`, `report`).
- Conflict resolution is last-write-wins via a global logical clock
  (`global_version_seq`); production would evaluate CRDT-based engines
  (see [docs/architecture.md](docs/architecture.md)).

## Troubleshooting

- **Backend seems to "hang" (connects, never responds) right after backgrounding it**:
  it's almost certainly suspended (`SIGTTIN`), not crashed — check with
  `ps -o pid,stat,command -p <pid>` (`STAT=T` means stopped). Always start it with
  `< /dev/null` redirected (see [Setup](#setup)).
- **`@fastify/static` crashes on boot with `FST_ERR_PLUGIN_VERSION_MISMATCH`**: this repo
  pins Fastify 4.x — install `@fastify/static@6`, not the latest major (v8, which needs
  Fastify 5).
- **`inventory.status` check constraint violation**: only `'ok' | 'low' | 'empty'` are valid;
  the app's "Is Nearly Empty" button sends `"low"`.
- **`avdmanager create avd --device ...` errors on `devices.xml`**: omit `--device` entirely.
