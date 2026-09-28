-- BOPIS demo schema. Postgres is the single source of truth; Neo4j is a
-- derived, live-synced mirror for reasoning (see docs/architecture.md).

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE SEQUENCE IF NOT EXISTS global_version_seq;

-- Gives every syncable row a monotonic logical clock, shared across tables,
-- so /sync/pull?since=<version> can be compared consistently across entities.
CREATE OR REPLACE FUNCTION stamp_version() RETURNS trigger AS $$
BEGIN
  NEW.version := nextval('global_version_seq');
  NEW.updated_at := now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- `inventory`'s primary key is shelf_id (not id), so resolve the entity id
-- generically instead of assuming every syncable table has an `id` column.
CREATE OR REPLACE FUNCTION notify_data_change() RETURNS trigger AS $$
DECLARE
  entity_id uuid;
BEGIN
  entity_id := COALESCE((to_jsonb(NEW)->>'id')::uuid, (to_jsonb(NEW)->>'shelf_id')::uuid);
  PERFORM pg_notify('data_change', json_build_object(
    'table', TG_TABLE_NAME,
    'op', TG_OP,
    'id', entity_id,
    'version', NEW.version
  )::text);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Stores (Mock: SAP Site/Store Master) --------------------------------------
CREATE TABLE stores (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  code text UNIQUE NOT NULL,
  address text
);

-- Products (Mock: SAP Material Master) ---------------------------------------
CREATE TABLE products (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  sku text UNIQUE NOT NULL,
  name text NOT NULL,
  description text,
  embedding vector(768)
);

CREATE TABLE substitutes (
  product_id uuid REFERENCES products(id),
  substitute_product_id uuid REFERENCES products(id),
  score float NOT NULL,
  PRIMARY KEY (product_id, substitute_product_id)
);

-- Shelves: one shelf = exactly one product slot, driven entirely by QR scan --
CREATE TABLE shelves (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id uuid REFERENCES stores(id) NOT NULL,
  product_id uuid REFERENCES products(id) NOT NULL,
  qr_code text UNIQUE NOT NULL,
  aisle text
);

-- Inventory (Mock: SAP Available-To-Promise / Inventory Management) ---------
CREATE TABLE inventory (
  shelf_id uuid PRIMARY KEY REFERENCES shelves(id),
  qty int NOT NULL DEFAULT 0,
  status text NOT NULL DEFAULT 'ok' CHECK (status IN ('ok', 'low', 'empty')),
  version bigint,
  updated_at timestamptz DEFAULT now()
);
CREATE TRIGGER trg_inventory_version BEFORE INSERT OR UPDATE ON inventory
  FOR EACH ROW EXECUTE FUNCTION stamp_version();
CREATE TRIGGER trg_inventory_notify AFTER INSERT OR UPDATE ON inventory
  FOR EACH ROW EXECUTE FUNCTION notify_data_change();

-- Customers (Mock: SAP Customer Data Cloud / Customer Profile) --------------
CREATE TABLE customers (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  alternative_ok_if_empty boolean NOT NULL DEFAULT false,
  last_order_at timestamptz,
  version bigint,
  updated_at timestamptz DEFAULT now()
);
CREATE TRIGGER trg_customers_version BEFORE INSERT OR UPDATE ON customers
  FOR EACH ROW EXECUTE FUNCTION stamp_version();
CREATE TRIGGER trg_customers_notify AFTER INSERT OR UPDATE ON customers
  FOR EACH ROW EXECUTE FUNCTION notify_data_change();

-- Orders (Mock: SAP S/4HANA Sales Order) ------------------------------------
CREATE TABLE orders (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id uuid REFERENCES customers(id) NOT NULL,
  store_id uuid REFERENCES stores(id) NOT NULL,
  status text NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'ready_for_pickup', 'completed')),
  version bigint,
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now()
);
CREATE TRIGGER trg_orders_version BEFORE INSERT OR UPDATE ON orders
  FOR EACH ROW EXECUTE FUNCTION stamp_version();
CREATE TRIGGER trg_orders_notify AFTER INSERT OR UPDATE ON orders
  FOR EACH ROW EXECUTE FUNCTION notify_data_change();

CREATE TABLE order_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid REFERENCES orders(id) NOT NULL,
  product_id uuid REFERENCES products(id) NOT NULL,
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'picked', 'substituted', 'unavailable')),
  substituted_with_product_id uuid REFERENCES products(id),
  version bigint,
  updated_at timestamptz DEFAULT now()
);
CREATE TRIGGER trg_order_items_version BEFORE INSERT OR UPDATE ON order_items
  FOR EACH ROW EXECUTE FUNCTION stamp_version();
CREATE TRIGGER trg_order_items_notify AFTER INSERT OR UPDATE ON order_items
  FOR EACH ROW EXECUTE FUNCTION notify_data_change();

-- Notifications ---------------------------------------------------------------
CREATE TABLE notifications (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  role text NOT NULL CHECK (role IN ('associate', 'manager', 'customer')),
  type text NOT NULL,
  payload jsonb NOT NULL DEFAULT '{}',
  read_at timestamptz,
  version bigint,
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now()
);
CREATE TRIGGER trg_notifications_version BEFORE INSERT OR UPDATE ON notifications
  FOR EACH ROW EXECUTE FUNCTION stamp_version();
CREATE TRIGGER trg_notifications_notify AFTER INSERT OR UPDATE ON notifications
  FOR EACH ROW EXECUTE FUNCTION notify_data_change();

-- Rules are data, not code: editable later via the Rules Admin UI without redeploying.
CREATE TABLE rules (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text UNIQUE NOT NULL,
  -- Plain-English explanation shown in the Rules Admin UI, kept separate from
  -- the Cypher query so non-technical readers can understand what a rule does.
  description text,
  trigger_table text NOT NULL,
  cypher_query text NOT NULL,
  action_type text NOT NULL,
  action_params jsonb NOT NULL DEFAULT '{}',
  enabled boolean NOT NULL DEFAULT true,
  updated_at timestamptz DEFAULT now()
);

-- Makes rule evaluation idempotent: the same (rule, entity, version) never fires twice.
CREATE TABLE rule_firings (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  rule_id uuid REFERENCES rules(id) NOT NULL,
  entity_id uuid NOT NULL,
  entity_version bigint NOT NULL,
  fired_at timestamptz DEFAULT now(),
  UNIQUE (rule_id, entity_id, entity_version)
);

-- Offline-first sync ------------------------------------------------------------
CREATE TABLE sync_outbox (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id text NOT NULL,
  entity text NOT NULL,
  entity_id uuid,
  op text NOT NULL CHECK (op IN ('insert', 'update')),
  payload jsonb NOT NULL,
  idempotency_key text UNIQUE NOT NULL,
  created_at timestamptz DEFAULT now(),
  applied_at timestamptz
);

-- Idempotency for direct online API calls (pick/substitute/report) -------------
CREATE TABLE processed_requests (
  idempotency_key text PRIMARY KEY,
  response jsonb,
  created_at timestamptz DEFAULT now()
);
