-- AFTERFORM read-only operational queries (PostgreSQL 16+).
-- Inspect and run with a dedicated read-only monitoring role.
-- Query 5 requires pg_stat_statements to be installed and configured by the DB provider.
-- Do not record SQL parameters containing customer PII in logs.

-- 1. Current database health counters
SELECT datname, numbackends, xact_commit, xact_rollback, deadlocks,
       pg_size_pretty(pg_database_size(datname)) AS database_size
FROM pg_stat_database
WHERE datname = current_database();

-- 2. Connection pool/app usage
SELECT application_name, state, count(*) AS connections
FROM pg_stat_activity
WHERE datname = current_database()
GROUP BY application_name, state
ORDER BY connections DESC;

-- 3. Slow/blocked running transactions (no query text to avoid accidental PII)
SELECT pid, application_name, state, wait_event_type, wait_event,
       now() - xact_start AS transaction_age
FROM pg_stat_activity
WHERE datname = current_database()
  AND xact_start IS NOT NULL
  AND now() - xact_start > interval '60 seconds'
ORDER BY xact_start;

-- 4. Largest app tables, including indexes and TOAST
SELECT relname AS table_name,
       pg_size_pretty(pg_total_relation_size(relid)) AS total_size
FROM pg_stat_user_tables
WHERE schemaname = 'app'
ORDER BY pg_total_relation_size(relid) DESC
LIMIT 20;

-- 5. Aggregate SQL statistics: run separately only after enabling pg_stat_statements.
-- SELECT queryid, calls,
--        round(total_exec_time::numeric, 2) AS total_exec_ms,
--        round(mean_exec_time::numeric, 2) AS avg_exec_ms,
--        rows
-- FROM pg_stat_statements
-- ORDER BY total_exec_time DESC
-- LIMIT 20;

-- 6. Publication health: stores awaiting first published snapshot
SELECT slug, status, updated_at
FROM app.stores
WHERE status = 'active' AND published_version IS NULL
ORDER BY updated_at DESC;

-- 7. Public stock pressure, store-scoped
SELECT s.slug AS store_slug, v.sku,
       (b.on_hand - b.reserved) AS available_to_sell
FROM app.inventory_balances AS b
JOIN app.product_variants AS v
  ON v.store_id = b.store_id AND v.id = b.variant_id
JOIN app.stores AS s ON s.id = b.store_id
WHERE (b.on_hand - b.reserved) <= 5
ORDER BY available_to_sell ASC, s.slug, v.sku
LIMIT 100;
