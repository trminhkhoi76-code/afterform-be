-- AFTERFORM database schema proposal v1 (PostgreSQL 16+).
-- DESIGN ARTIFACT ONLY: not executed against a real database.
-- Private app schema is accessed through afterform-be; browsers must never use DB credentials.
BEGIN;

CREATE SCHEMA IF NOT EXISTS app;
SET LOCAL search_path = app, public;

-- TENANCY / BRANDING ---------------------------------------------------------
CREATE TABLE stores (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug TEXT NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9][a-z0-9-]{0,62}$'),
  name TEXT NOT NULL CHECK (btrim(name) <> ''),
  industry TEXT NOT NULL DEFAULT 'general',
  status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'active', 'suspended')),
  locale TEXT NOT NULL DEFAULT 'en-US',
  currency VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency ~ '^[A-Z]{3}$'),
  theme JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(theme) = 'object'),
  settings JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(settings) = 'object'),
  published_version BIGINT CHECK (published_version > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE store_domains (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
  hostname TEXT NOT NULL UNIQUE CHECK (hostname = lower(hostname)),
  is_primary BOOLEAN NOT NULL DEFAULT false,
  verified_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX store_domains_primary_uq ON store_domains(store_id) WHERE is_primary;

-- user_id is the UUID from the chosen identity provider, not an FK to another system yet.
CREATE TABLE store_memberships (
  store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
  user_id UUID NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('owner', 'admin', 'editor', 'viewer')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (store_id, user_id)
);
CREATE INDEX store_memberships_user_idx ON store_memberships(user_id);

-- CATALOG -------------------------------------------------------------------
CREATE TABLE categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
  parent_id UUID,
  slug TEXT NOT NULL,
  name TEXT NOT NULL CHECK (btrim(name) <> ''),
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (store_id, id),
  UNIQUE (store_id, slug),
  FOREIGN KEY (store_id, parent_id) REFERENCES categories(store_id, id)
);
CREATE INDEX categories_store_order_idx ON categories(store_id, sort_order);

CREATE TABLE products (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
  category_id UUID,
  slug TEXT NOT NULL,
  name TEXT NOT NULL CHECK (btrim(name) <> ''),
  description TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'active', 'archived')),
  base_price NUMERIC(18,2) NOT NULL CHECK (base_price >= 0),
  attributes JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(attributes) = 'object'),
  seo JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(seo) = 'object'),
  is_featured BOOLEAN NOT NULL DEFAULT false,
  is_new BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (store_id, id),
  UNIQUE (store_id, slug),
  FOREIGN KEY (store_id, category_id) REFERENCES categories(store_id, id)
);
CREATE INDEX products_store_status_idx ON products(store_id, status, created_at DESC);
CREATE INDEX products_store_category_idx ON products(store_id, category_id, status);
-- Supports queries using jsonb containment (@>); monitor before adding more JSON indexes.
CREATE INDEX products_attributes_gin_idx ON products USING GIN (attributes jsonb_path_ops);

CREATE TABLE product_variants (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id UUID NOT NULL,
  product_id UUID NOT NULL,
  sku TEXT NOT NULL,
  option_values JSONB NOT NULL DEFAULT '{}'::jsonb
    CHECK (jsonb_typeof(option_values) = 'object'),
  price_override NUMERIC(18,2) CHECK (price_override >= 0),
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (store_id, id),
  UNIQUE (store_id, sku),
  FOREIGN KEY (store_id, product_id) REFERENCES products(store_id, id)
);
CREATE INDEX product_variants_product_idx ON product_variants(store_id, product_id);

CREATE TABLE product_media (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id UUID NOT NULL,
  product_id UUID NOT NULL,
  media_url TEXT NOT NULL,
  alt_text TEXT NOT NULL DEFAULT '',
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (store_id, product_id) REFERENCES products(store_id, id) ON DELETE CASCADE
);
CREATE INDEX product_media_product_idx ON product_media(store_id, product_id, sort_order);

-- Stock truth is *not* stored in product JSON or publication snapshots.
CREATE TABLE inventory_balances (
  store_id UUID NOT NULL,
  variant_id UUID NOT NULL,
  on_hand INTEGER NOT NULL DEFAULT 0 CHECK (on_hand >= 0),
  reserved INTEGER NOT NULL DEFAULT 0 CHECK (reserved >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (store_id, variant_id),
  CHECK (reserved <= on_hand),
  FOREIGN KEY (store_id, variant_id) REFERENCES product_variants(store_id, id)
);

-- CONTENT / PUBLISHING -------------------------------------------------------
CREATE TABLE pages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
  slug TEXT NOT NULL,
  template_key TEXT NOT NULL DEFAULT 'default',
  draft_sections JSONB NOT NULL DEFAULT '[]'::jsonb
    CHECK (jsonb_typeof(draft_sections) = 'array'),
  status TEXT NOT NULL DEFAULT 'draft'
    CHECK (status IN ('draft', 'published', 'archived')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (store_id, id),
  UNIQUE (store_id, slug)
);
CREATE INDEX pages_store_status_idx ON pages(store_id, status);

-- Immutable published API payload. Publish transaction inserts a version and
-- moves stores.published_version to the same value atomically.
CREATE TABLE store_publications (
  store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
  version BIGINT NOT NULL CHECK (version > 0),
  payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
  published_by UUID,
  published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (store_id, version)
);
ALTER TABLE stores ADD CONSTRAINT stores_published_version_fk
  FOREIGN KEY (id, published_version)
  REFERENCES store_publications(store_id, version)
  DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX store_publications_time_idx ON store_publications(store_id, published_at DESC);

-- FUTURE COMMERCE TABLES (not integrated with frontend yet) -----------------
CREATE TABLE orders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id UUID NOT NULL REFERENCES stores(id),
  order_number TEXT NOT NULL,
  idempotency_key TEXT,
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'confirmed', 'fulfilled', 'cancelled')),
  payment_status TEXT NOT NULL DEFAULT 'unpaid'
    CHECK (payment_status IN ('unpaid', 'pending', 'paid', 'failed', 'refunded')),
  currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
  subtotal NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
  discount NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (discount >= 0),
  shipping NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (shipping >= 0),
  total NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
  customer_email TEXT NOT NULL,
  shipping_address JSONB NOT NULL DEFAULT '{}'::jsonb
    CHECK (jsonb_typeof(shipping_address) = 'object'),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (store_id, id),
  UNIQUE (store_id, order_number),
  UNIQUE (store_id, idempotency_key)
);
CREATE INDEX orders_store_created_idx ON orders(store_id, created_at DESC);
CREATE INDEX orders_store_status_idx ON orders(store_id, status, created_at DESC);

CREATE TABLE order_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id UUID NOT NULL,
  order_id UUID NOT NULL,
  variant_id UUID,
  product_snapshot JSONB NOT NULL CHECK (jsonb_typeof(product_snapshot) = 'object'),
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  unit_price NUMERIC(18,2) NOT NULL CHECK (unit_price >= 0),
  line_total NUMERIC(18,2) NOT NULL CHECK (line_total >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (store_id, order_id) REFERENCES orders(store_id, id),
  FOREIGN KEY (store_id, variant_id) REFERENCES product_variants(store_id, id)
);
CREATE INDEX order_items_order_idx ON order_items(store_id, order_id);

CREATE TABLE inventory_movements (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id UUID NOT NULL,
  variant_id UUID NOT NULL,
  order_id UUID,
  delta INTEGER NOT NULL CHECK (delta <> 0),
  reason TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (store_id, variant_id) REFERENCES product_variants(store_id, id),
  FOREIGN KEY (store_id, order_id) REFERENCES orders(store_id, id)
);
CREATE INDEX inventory_movements_variant_idx
  ON inventory_movements(store_id, variant_id, created_at DESC);

CREATE TABLE audit_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id UUID NOT NULL REFERENCES stores(id),
  actor_id UUID,
  action TEXT NOT NULL,
  entity_type TEXT NOT NULL,
  entity_id UUID,
  changes JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(changes) = 'object'),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_events_store_time_idx ON audit_events(store_id, created_at DESC);

COMMIT;
