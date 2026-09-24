-- Mock SAP seed data. Each block states which real SAP system it stands in for.

-- Mock: SAP Site/Store Master (replaces S/4HANA Site/Store Master)
INSERT INTO stores (id, name, code) VALUES
  ('11111111-1111-1111-1111-111111111111', 'Downtown Supermarket', 'ST01'),
  ('22222222-2222-2222-2222-222222222222', 'Uptown Supermarket', 'ST02');

-- Mock: SAP Material Master (replaces S/4HANA Material Master)
INSERT INTO products (id, sku, name, description) VALUES
  ('a1000000-0000-0000-0000-000000000001', 'SKU-MILK-1L', 'Whole Milk 1L', 'Fresh whole milk, 1 liter carton'),
  ('a1000000-0000-0000-0000-000000000002', 'SKU-MILK-OAT-1L', 'Oat Milk 1L', 'Oat-based milk alternative, 1 liter'),
  ('a1000000-0000-0000-0000-000000000003', 'SKU-BREAD-WHT', 'White Bread Loaf', 'Sliced white bread loaf'),
  ('a1000000-0000-0000-0000-000000000004', 'SKU-BREAD-WHL', 'Whole Grain Bread Loaf', 'Sliced whole grain bread loaf'),
  ('a1000000-0000-0000-0000-000000000005', 'SKU-COFFEE-BEANS', 'Coffee Beans 500g', 'Medium roast coffee beans'),
  ('a1000000-0000-0000-0000-000000000006', 'SKU-CHOCOLATE-BAR', 'Dark Chocolate Bar', 'Customer-favorite dark chocolate bar');

INSERT INTO substitutes (product_id, substitute_product_id, score) VALUES
  ('a1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000002', 0.82),
  ('a1000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000004', 0.75);

-- Mock: SAP Available-To-Promise / Inventory Management --------------------
INSERT INTO shelves (id, store_id, product_id, qr_code, aisle) VALUES
  ('b1000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'a1000000-0000-0000-0000-000000000001', 'QR-ST01-MILK', 'Aisle 1'),
  ('b1000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'a1000000-0000-0000-0000-000000000002', 'QR-ST01-OATMILK', 'Aisle 1'),
  ('b1000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'a1000000-0000-0000-0000-000000000003', 'QR-ST01-BREAD', 'Aisle 2'),
  ('b1000000-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'a1000000-0000-0000-0000-000000000005', 'QR-ST01-COFFEE', 'Aisle 3'),
  ('b1000000-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'a1000000-0000-0000-0000-000000000006', 'QR-ST01-CHOCOLATE', 'Aisle 4');

INSERT INTO inventory (shelf_id, qty, status) VALUES
  ('b1000000-0000-0000-0000-000000000001', 12, 'ok'),
  ('b1000000-0000-0000-0000-000000000002', 20, 'ok'),
  ('b1000000-0000-0000-0000-000000000003', 8, 'ok'),
  ('b1000000-0000-0000-0000-000000000004', 3, 'low'),
  ('b1000000-0000-0000-0000-000000000005', 15, 'ok');

-- Second store's shelves/inventory (Mock: SAP ATP for Uptown Supermarket, ST02).
-- Seeded so the "check other stores" rule has real cross-store data to reason
-- over during the demo (e.g. Coffee Beans is stocked here even if ST01 runs out).
INSERT INTO shelves (id, store_id, product_id, qr_code, aisle) VALUES
  ('b2000000-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'a1000000-0000-0000-0000-000000000001', 'QR-ST02-MILK', 'Aisle 1'),
  ('b2000000-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'a1000000-0000-0000-0000-000000000003', 'QR-ST02-BREAD', 'Aisle 2'),
  ('b2000000-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', 'a1000000-0000-0000-0000-000000000005', 'QR-ST02-COFFEE', 'Aisle 3'),
  ('b2000000-0000-0000-0000-000000000004', '22222222-2222-2222-2222-222222222222', 'a1000000-0000-0000-0000-000000000006', 'QR-ST02-CHOCOLATE', 'Aisle 4');

INSERT INTO inventory (shelf_id, qty, status) VALUES
  ('b2000000-0000-0000-0000-000000000001', 10, 'ok'),
  ('b2000000-0000-0000-0000-000000000002', 6, 'ok'),
  ('b2000000-0000-0000-0000-000000000003', 18, 'ok'),
  ('b2000000-0000-0000-0000-000000000004', 9, 'ok');

-- Mock: SAP Customer Data Cloud / Customer Profile --------------------------
INSERT INTO customers (id, name, alternative_ok_if_empty, last_order_at) VALUES
  ('c1000000-0000-0000-0000-000000000001', 'Alice Johnson', true, now() - interval '90 days'),
  ('c1000000-0000-0000-0000-000000000002', 'Bob Smith', false, now() - interval '2 days');

-- No orders are seeded here on purpose: the Orders list should start empty so the
-- Customer Order Simulator → Android app flow can be demonstrated live end-to-end.
-- Run `npm run seed:demo-order` (applies seed_demo_order.sql) if you want one
-- pre-existing order for quick manual testing without using the simulator.

