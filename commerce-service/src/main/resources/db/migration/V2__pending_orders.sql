-- Commerce-owned order model. No Catalog foreign keys and no payment provider integration.
ALTER TABLE commerce.inventory_reservations
  ADD CONSTRAINT inventory_reservations_tenant_id_uq UNIQUE(store_id, id);

CREATE TABLE commerce.orders (
  id uuid PRIMARY KEY,
  store_id uuid NOT NULL,
  idempotency_key varchar(128) NOT NULL,
  request_fingerprint varchar(64) NOT NULL CHECK(length(request_fingerprint)=64),
  status varchar(20) NOT NULL CHECK(status IN ('PENDING','CANCELLED')),
  currency varchar(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
  total numeric(18,2) NOT NULL CHECK(total >= 0),
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(store_id, id),
  UNIQUE(store_id, idempotency_key)
);
CREATE INDEX orders_tenant_time_idx ON commerce.orders(store_id, created_at DESC);

CREATE TABLE commerce.order_items (
  id uuid PRIMARY KEY,
  store_id uuid NOT NULL,
  order_id uuid NOT NULL,
  variant_id uuid NOT NULL,
  reservation_id uuid NOT NULL,
  product_snapshot jsonb NOT NULL CHECK(jsonb_typeof(product_snapshot)='object'),
  quantity integer NOT NULL CHECK(quantity > 0),
  unit_price numeric(18,2) NOT NULL CHECK(unit_price >= 0),
  line_total numeric(18,2) NOT NULL CHECK(line_total >= 0),
  FOREIGN KEY(store_id, order_id) REFERENCES commerce.orders(store_id, id),
  FOREIGN KEY(store_id, variant_id) REFERENCES commerce.inventory_balances(store_id, variant_id),
  FOREIGN KEY(store_id, reservation_id) REFERENCES commerce.inventory_reservations(store_id, id),
  UNIQUE(store_id, reservation_id)
);
CREATE INDEX order_items_tenant_order_idx ON commerce.order_items(store_id, order_id);
