-- Optional: one pre-existing order, for manual testing without the Customer Order
-- Simulator UI. Not applied by the default `npm run seed` / `npm run demo:reset` flow
-- on purpose (the Orders list should start empty for the live demo) — apply explicitly
-- via `npm run seed:demo-order` if you want it.

-- Mock: SAP S/4HANA Sales Order ----------------------------------------------
INSERT INTO orders (id, customer_id, store_id, status) VALUES
  ('d1000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'open');

INSERT INTO order_items (order_id, product_id, status) VALUES
  ('d1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 'pending'),
  ('d1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000005', 'pending');
