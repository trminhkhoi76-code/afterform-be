-- Commerce owns its data. Catalog product/variant IDs are opaque external UUIDs.
CREATE SCHEMA IF NOT EXISTS commerce;
SET LOCAL search_path = commerce, public;

CREATE TABLE inventory_balances (
  store_id uuid NOT NULL,
  variant_id uuid NOT NULL,
  on_hand integer NOT NULL DEFAULT 0 CHECK (on_hand >= 0),
  reserved integer NOT NULL DEFAULT 0 CHECK (reserved >= 0),
  version bigint NOT NULL DEFAULT 0,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(store_id, variant_id),
  CHECK (reserved <= on_hand)
);
CREATE TABLE inventory_reservations (
  id uuid PRIMARY KEY,
  store_id uuid NOT NULL,
  variant_id uuid NOT NULL,
  quantity integer NOT NULL CHECK (quantity > 0),
  state varchar(20) NOT NULL CHECK (state IN ('ACTIVE','RELEASED','COMMITTED')),
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (store_id, variant_id) REFERENCES inventory_balances(store_id, variant_id)
);
CREATE INDEX inventory_reservations_expiry_idx ON inventory_reservations(expires_at)
  WHERE state = 'ACTIVE';
CREATE INDEX inventory_reservations_variant_idx ON inventory_reservations(store_id, variant_id, state);

CREATE TABLE inventory_movements (
  id uuid PRIMARY KEY,
  store_id uuid NOT NULL,
  variant_id uuid NOT NULL,
  delta integer NOT NULL CHECK (delta <> 0),
  reason varchar(32) NOT NULL CHECK (reason IN ('RESTOCK','ORDER_COMMIT','ADJUSTMENT')),
  reference_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (store_id, variant_id) REFERENCES inventory_balances(store_id, variant_id)
);
CREATE INDEX inventory_movements_tenant_idx ON inventory_movements(store_id, variant_id, created_at DESC);
