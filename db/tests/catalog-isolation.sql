-- Integration check for Catalog-owned schema; rollback so CI remains isolated.
BEGIN;
DO $$
DECLARE
  a uuid;
  b uuid;
  b_product uuid;
  a_product uuid;
  b_variant uuid;
  p jsonb;
BEGIN
  INSERT INTO catalog.stores (slug, name, status)
    VALUES ('fashion', 'Fashion', 'active') RETURNING id INTO a;
  INSERT INTO catalog.stores (slug, name, status)
    VALUES ('beauty', 'Beauty', 'active') RETURNING id INTO b;

  INSERT INTO catalog.products(store_id, slug, name, base_price)
    VALUES(a, 'sample', 'Fashion Sample', 99) RETURNING id INTO a_product;
  INSERT INTO catalog.products(store_id, slug, name, base_price, attributes)
    VALUES(b, 'sample', 'Beauty Sample', 199, '{"skinTypes":["dry"]}') RETURNING id INTO b_product;

  -- Cross-tenant product/variant foreign keys must be rejected.
  BEGIN
    INSERT INTO catalog.product_variants(store_id, product_id, sku)
    VALUES(a, b_product, 'CROSS-STORE');
    RAISE EXCEPTION 'Missing cross-tenant protection';
  EXCEPTION WHEN foreign_key_violation THEN NULL;
  END;

  INSERT INTO catalog.product_variants(store_id, product_id, sku, option_values)
    VALUES (b, b_product, 'SERUM-30', '{"volume":"30ml"}') RETURNING id INTO b_variant;

  INSERT INTO catalog.publications(store_id, version, payload) VALUES
    (b, 1, '{"store":{"slug":"beauty","name":"Beauty"},"products":[],"home":{},"looks":[]}'::jsonb);
  UPDATE catalog.stores SET published_version=1 WHERE id=b;

  SELECT pub.payload INTO p FROM catalog.publications pub
    JOIN catalog.stores s ON s.id=pub.store_id AND s.published_version=pub.version
    WHERE s.slug='beauty' AND s.status='active';
  IF p->'products' <> '[]'::jsonb THEN RAISE EXCEPTION 'Empty catalog was not preserved'; END IF;

  -- Failure to publish another tenant's snapshot must be prevented.
  BEGIN
    UPDATE catalog.stores SET published_version=2 WHERE id=a;
    SET CONSTRAINTS stores_current_publication_fk IMMEDIATE;
    RAISE EXCEPTION 'Should have rejected invalid publication reference';
  EXCEPTION WHEN foreign_key_violation THEN
    SET CONSTRAINTS stores_current_publication_fk DEFERRED;
  END;
END $$;
ROLLBACK;
