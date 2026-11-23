# Public bootstrap API (implemented in catalog-service)

## Runtime

Start a PostgreSQL 16+ instance and provide `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` through environment variables (never commit secrets). Run:

```bash
cd catalog-service
mvn spring-boot:run
```

Flyway automatically applies only `catalog-service/src/main/resources/db/migration/V1__catalog_schema.sql` to its catalog-owned schema. **The historical combined-domain SQL in `db/migrations/` is not executed by the catalog service.**

```http
GET http://localhost:8081/api/v1/public/stores/beauty/bootstrap
Accept: application/json
```

- Returns `200` for active stores with a published version and public JSON payload; `404` for absent, draft, suspended or unpublished stores; `400` for invalid slug format.
- No demo catalog is returned by this endpoint. There are **no pre-seeded stores**, so a new database returns `404` until a valid publication is created. This is intentional.
- Public response is an immutable publication snapshot; it contains `store`, `products`, optional `home` and `looks`, exactly as specified in `docs/bootstrap-contract.md`.
- ETag and a small public cache max-age are sent. Data validation and administrative publish workflow remain issue #9.
- CORS defaults to `http://localhost:8137` and the existing GitHub Pages origin; override via `AFTERFORM_CORS_ORIGINS` (comma-separated exact origins).
- The frontend will fall back to its local demo when API returns `404`, times out or cannot connect. For a **valid empty publication**, frontend shows the empty API catalog.

**Security:** This is read-only. No customer cookies, admin writes or payment/auth operations. Store identity is checked by querying both slug and published JSON slug. Do not permit raw SQL inserts from public clients; authenticated admin publish workflow is a separate PR.

## CI

Run `mvn -B -ntp clean verify` with a disposable PostgreSQL 16 instance and `SPRING_DATASOURCE_*` variables. Integration tests verify draft invisibility, active publication, invalid/mismatched store identity and no default fake rows.
