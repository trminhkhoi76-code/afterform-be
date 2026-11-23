# AFTERFORM Backend (TODO)

This repository is reserved for the **API, persistence and authenticated admin services** of the AFTERFORM multi-store storefront engine. The frontend lives in [afterform](https://github.com/trminhkhoi76-code/afterform).

> Status: **contract / roadmap only**. No backend API, authentication, database or production checkout is implemented yet.

## First endpoint to implement

`GET /api/v1/public/stores/{storeSlug}/bootstrap`

- Public, read-only, no customer credentials required
- Respond `200` with the documented store, products and home payload; an empty product list is valid
- Respond `404` for unknown stores, `5xx` for server failures
- Ensure the returned `store.slug` exactly matches `{storeSlug}`
- Enable CORS for the deployed AFTERFORM frontend origin
- Use HTTPS when frontend is hosted over HTTPS
- Provide stable, cache-friendly responses and URL-addressable product images

See [the API contract](docs/bootstrap-contract.md).

## Implementation backlog

- [ ] Choose backend framework and deployment strategy
- [ ] Define persistence for stores, themes, categories, products, variants, media, pages and published versions
- [ ] Implement the read-only public bootstrap endpoint; validate by schema and tenant
- [ ] Add integration tests for 200, 404, empty catalog, invalid store and CORS
- [ ] Add secure admin auth + per-store permissions; protect every write by tenant ID
- [ ] Implement draft/publish APIs for store settings, catalog and page content
- [ ] Add image/media storage and upload authorization
- [ ] Add server-validated checkout, atomic stock changes, orders, payment webhooks and audit logs **before** live commerce
- [ ] Coordinate migration from the frontend's browser-local demo data
- [ ] Add CI and environment configuration; never commit API secrets

The frontend loads live data only if `runtime-config.js` has a valid `apiBaseUrl`. When missing, unreachable, timed out, or invalid, it uses the existing fashion demo data. API-mode admin is intentionally **read-only** until backend write endpoints are available.


## Database design proposal (review before deployment)

**Recommendation:** Managed PostgreSQL 16+ with tenant-scoped relational tables and `JSONB` for industry-specific product fields and storefront pages. The SQL below is **not deployed**; there are no production credentials or databases in this repository.

- [ADR-0001: PostgreSQL vs MongoDB / DynamoDB, ERD, multitenancy, scaling](docs/adr-0001-database-architecture.md)
- [Proposed PostgreSQL migration](db/migrations/0001_initial_design.sql)
- [Database observability and backup runbook](docs/observability-runbook.md)
- [Read-only monitoring SQL](db/queries/monitoring.sql)
- [Bootstrap API contract](docs/bootstrap-contract.md)

**Local schema validation once PostgreSQL is available:**

```sh
psql -v ON_ERROR_STOP=1 "$DATABASE_URL" -f db/migrations/0001_initial_design.sql
psql -v ON_ERROR_STOP=1 "$DATABASE_URL" -f db/tests/tenant-isolation.sql
```

Use a **disposable test database**, never run the design/test scripts blindly on an existing production instance. Real backend APIs, admin auth, publishing, live checkout, payments and real inventory transactions remain TODO.


## Java 25 + Spring Boot microservices (implementation branches)

This repository is being built incrementally through linked PRs:

- [Architecture ADR](docs/adr-0002-java-microservices.md)
- [Implementation roadmap](docs/implementation-roadmap.md)
- [Java / Maven development](docs/java-development.md)
- [Catalog-owned Flyway schema](docs/catalog-schema.md)
- [PostgreSQL-backed public API](docs/public-bootstrap-service.md)
- [Local Docker Compose](docs/local-docker.md)

The two independent apps are **Catalog Service** (8081) and **Commerce Service** (8082). Commerce still exposes health only; customer auth, products admin, payments and real order flows are tracked as separate issues. No fake live payments are enabled.

Start local infrastructure using:

```bash
cp .env.example .env
# Set a strong local-only CATALOG_DB_PASSWORD in .env
docker compose up --build -d
```

See the local Docker runbook before using a real database. PRs are stacked and must be merged in dependency order; files linked above are available after their respective PRs are merged.

## Database & optional Object Storage

Catalog uses managed PostgreSQL directly through
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and
`SPRING_DATASOURCE_PASSWORD` (Spring Data JPA + Flyway). No Neon SDK,
Node.js adapter or CLI is required. Internal image storage can be enabled
later via Neon S3-compatible Object Storage, bucket `afterform-media`.
See [database and storage configuration](docs/neon-setup.md).

