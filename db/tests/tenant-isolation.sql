-- Run only in an isolated test database after 0001_initial_design.sql.
-- Uses a rolled-back transaction to keep the test database clean.
BEGIN;

DO $test$
DECLARE
  fashion_id UUID;
  beauty_id UUID;
  fashion_product UUID;
  beauty_product UUID;
  beauty_variant UUID;
  fashion_order UUID;
  published_products JSONB;
BEGIN
  INSERT INTO app.stores(slug, name) VALUES ('fashion', 'Fashion Store')
    RETURNING id INTO fashion_id;
  INSERT INTO app.stores(slug, name) VALUES ('beauty', 'Beauty Store')
    RETURNING id INTO beauty_id;

  -- Identical product slugs must be legal for distinct tenants.
  INSERT INTO app.products(store_id, slug, name, base_price)
    VALUES (fashion_id, 'sample', 'Fashion Sample', 100)
    RETURNING id INTO fashion_product;
  INSERT INTO app.products(store_id, slug, name, base_price, attributes)
    VALUES (beauty_id, 'sample', 'Beauty Sample', 250, '{"skinType":"dry"}')
    RETURNING id INTO beauty_product;

  INSERT INTO app.product_variants(store_id, product_id, sku, option_values)
    VALUES (beauty_id, beauty_product, 'BEAUTY-30ML', '{"volume":"30ml"}')
    RETURNING id INTO beauty_variant;
  INSERT INTO app.inventory_balances(store_id, variant_id, on_hand, reserved)
    VALUES (beauty_id, beauty_variant, 20, 2);

  IF (SELECT on_hand - reserved FROM app.inventory_balances
      WHERE store_id = beauty_id AND variant_id = beauty_variant) <> 18 THEN
    RAISE EXCEPTION 'Stock invariant failed';
  END IF;

  -- A foreign key must reject a product from another tenant.
  BEGIN
    INSERT INTO app.product_variants(store_id, product_id, sku)
      VALUES (fashion_id, beauty_product, 'CROSS-TENANT-SKU');
    RAISE EXCEPTION 'Missing tenant constraint on product_variants';
  EXCEPTION WHEN foreign_key_violation THEN
    NULL;
  END;

  BEGIN
    INSERT INTO app.inventory_balances(store_id, variant_id, on_hand)
      VALUES (fashion_id, beauty_variant, 1);
    RAISE EXCEPTION 'Missing tenant constraint on inventory_balances';
  EXCEPTION WHEN foreign_key_violation THEN
    NULL;
  END;

  -- Zero-product published catalog must remain a valid stored public snapshot.
  INSERT INTO app.store_publications(store_id, version, payload)
    VALUES (beauty_id, 1, '{"store":{"slug":"beauty","name":"Beauty Store"},"products":[]}'::jsonb);
  UPDATE app.stores SET published_version = 1 WHERE id = beauty_id;

  SELECT publication.payload->'products'
    INTO published_products
  FROM app.store_publications publication
  JOIN app.stores store ON store.id = publication.store_id
                      AND store.published_version = publication.version
  WHERE store.id = beauty_id;

  IF published_products <> '[]'::jsonb THEN
    RAISE EXCEPTION 'Published empty catalog changed unexpectedly';
  END IF;

  -- Historical order line must not accept another store's variant.
  INSERT INTO app.orders(store_id, order_number, currency, customer_email)
    VALUES (fashion_id, 'AF-TEST-1', 'USD', 'test@example.invalid')
    RETURNING id INTO fashion_order;

  BEGIN
    INSERT INTO app.order_items(store_id, order_id, variant_id, product_snapshot, quantity, unit_price, line_total)
      VALUES (fashion_id, fashion_order, beauty_variant, '{"name":"Other store"}', 1, 1, 1);
    RAISE EXCEPTION 'Missing tenant constraint on order_items';
  EXCEPTION WHEN foreign_key_violation THEN
    NULL;
  END;
END;
$test$;

ROLLBACK;
