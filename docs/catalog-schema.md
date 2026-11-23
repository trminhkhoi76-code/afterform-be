# Catalog database boundary

This Flyway migration is **catalog-service only**. It deliberately excludes `orders`, `order_items`, `inventory_balances`, `inventory_movements`, and `store_memberships`, which belong to Commerce / Identity.

- Migration: `catalog-service/src/main/resources/db/migration/V1__catalog_schema.sql`
- Smoke/integrity tests: `db/tests/catalog-isolation.sql`
- Existing `db/migrations/0001_initial_design.sql` remains a **historical logical ADR artifact** and must NOT be run as an application startup migration.
- Catalog uses an independent connection with access only to catalog schema. Prefer distinct DB name and DB user; a single shared PostgreSQL cluster is acceptable for local development.
- The snapshot `catalog.publications.payload` is *already public contract JSON*; publishing/write API and full JSON validation are future #9. For first read-only bootstrap API, no fake seed/dummy rows will be created on startup.
- Before production, DB privileges should prevent updates/deletes to published snapshots except privileged migration/retention operations.

Local smoke test (disposable PostgreSQL database):

```bash
psql -1 -v ON_ERROR_STOP=1 "$CATALOG_DB_URL" -f catalog-service/src/main/resources/db/migration/V1__catalog_schema.sql
psql -v ON_ERROR_STOP=1 "$CATALOG_DB_URL" -f db/tests/catalog-isolation.sql
```

Runtime Flyway runner and service credentials are attached in #5, once JDBC is wired.
