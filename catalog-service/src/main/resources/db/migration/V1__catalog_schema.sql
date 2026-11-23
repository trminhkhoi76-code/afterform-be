-- Catalog-service-owned PostgreSQL schema v1 (Flyway).
-- Execute only through the catalog-service Flyway runner or disposable CI database.
-- This service must never access commerce inventory/orders tables by SQL.
CREATE SCHEMA IF NOT EXISTS catalog;
SET LOCAL search_path = catalog, public;

CREATE TABLE stores (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  slug text NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9][a-z0-9-]{0,62}$'),
  name text NOT NULL CHECK (length(trim(name)) > 0),
  industry text NOT NULL DEFAULT 'general',
  status text NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','active','suspended')),
  locale text NOT NULL DEFAULT 'en-US',
  currency varchar(3) NOT NULL DEFAULT 'USD' CHECK (currency ~ '^[A-Z]{3}$'),
  market text NOT NULL DEFAULT '',
  description text NOT NULL DEFAULT '',
  theme jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(theme) = 'object'),
  settings jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(settings) = 'object'),
  published_version bigint CHECK (published_version > 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE store_domains (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id uuid NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
  hostname text NOT NULL UNIQUE CHECK (hostname = lower(hostname)),
  verified_at timestamptz,
  is_primary boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX store_primary_domain_uq ON store_domains(store_id) WHERE is_primary;

CREATE TABLE categories (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id uuid NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
  parent_id uuid,
  slug text NOT NULL,
  name text NOT NULL CHECK (length(trim(name)) > 0),
  sort_order integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (store_id, id),
  UNIQUE (store_id, slug),
  FOREIGN KEY (store_id, parent_id) REFERENCES categories(store_id, id)
);
CREATE INDEX categories_listing_idx ON categories(store_id, sort_order);

CREATE TABLE products (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id uuid NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
  category_id uuid,
  slug text NOT NULL,
  name text NOT NULL CHECK (length(trim(name)) > 0),
  description text NOT NULL DEFAULT '',
  status text NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'active', 'archived')),
  base_price numeric(18,2) NOT NULL CHECK (base_price >= 0),
  attributes jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(attributes) = 'object'),
  seo jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(seo) = 'object'),
  is_featured boolean NOT NULL DEFAULT false,
  is_new boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (store_id, id),
  UNIQUE (store_id, slug),
  FOREIGN KEY (store_id, category_id) REFERENCES categories(store_id, id)
);
CREATE INDEX products_listing_idx ON products(store_id, status, created_at DESC);
CREATE INDEX products_category_idx ON products(store_id, category_id, status);
CREATE INDEX products_attributes_gin_idx ON products USING gin (attributes jsonb_path_ops);

CREATE TABLE product_variants (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id uuid NOT NULL,
  product_id uuid NOT NULL,
  sku text NOT NULL,
  option_values jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(option_values) = 'object'),
  price_override numeric(18,2) CHECK (price_override >= 0),
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (store_id, id),
  UNIQUE (store_id, sku),
  FOREIGN KEY (store_id, product_id) REFERENCES products(store_id, id)
);
CREATE INDEX variants_product_idx ON product_variants(store_id, product_id);

CREATE TABLE product_media (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id uuid NOT NULL,
  product_id uuid NOT NULL,
  media_url text NOT NULL,
  alt_text text NOT NULL DEFAULT '',
  sort_order integer NOT NULL DEFAULT 0,
  FOREIGN KEY (store_id, product_id) REFERENCES products(store_id, id) ON DELETE CASCADE
);
CREATE INDEX product_media_listing_idx ON product_media(store_id, product_id, sort_order);

CREATE TABLE pages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id uuid NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
  slug text NOT NULL,
  template_key text NOT NULL DEFAULT 'default',
  draft_sections jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(draft_sections) = 'array'),
  status text NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','published','archived')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (store_id, id),
  UNIQUE (store_id, slug)
);
CREATE INDEX pages_store_status_idx ON pages(store_id, status);

-- An immutable *public* snapshot; the publish service will validate, insert and
-- advance stores.published_version in a single transaction (later issue #9).
CREATE TABLE publications (
  store_id uuid NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
  version bigint NOT NULL CHECK (version > 0),
  payload jsonb NOT NULL CHECK (
    jsonb_typeof(payload) = 'object'
    AND jsonb_typeof(payload -> 'store') = 'object'
    AND jsonb_typeof(payload -> 'products') = 'array'
  ),
  published_by uuid,
  published_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (store_id, version)
);
ALTER TABLE stores ADD CONSTRAINT stores_current_publication_fk
  FOREIGN KEY (id, published_version) REFERENCES publications(store_id, version)
  DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX publications_recent_idx ON publications(store_id, published_at DESC);
