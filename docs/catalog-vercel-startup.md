# Fix: Vercel Catalog startup timed out after Flyway validation

## Incident / evidence

The 2026-11-23 Vercel logs show **Tomcat initialized with port 80**, Hikari
connected successfully to Neon, PostgreSQL 18.7, and Flyway validated V1 after
reporting that `public.flyway_schema_history` did not yet exist. Vercel
terminated startup after 28.7 seconds because no TCP listener was ready.

The Java app **already honors Vercel's `PORT=80`** through
`server.port=${PORT:8081}`. Changing the port alone will not fix this.

The long-running step after validation is not proven by these logs, but initial
Flyway schema creation and subsequent Hibernate/JPA initialization are on the
synchronous cold-start path. This project now supports **separate manual
migration** so Vercel's HTTP container does not need to migrate each time.

## 1. Verify the current schema first (read-only)

In the Neon SQL Editor for database `neondb` and the branch used by Vercel:

```sql
SELECT to_regclass('public.flyway_schema_history') AS history,
       to_regclass('catalog.stores') AS stores,
       to_regclass('catalog.publications') AS publications;
```

If the tables exist, inspect whether a version 1 migration succeeded:

```sql
SELECT installed_rank, version, description, success
FROM public.flyway_schema_history
ORDER BY installed_rank DESC LIMIT 5;
```

Do **not** blindly rerun `V1__catalog_schema.sql` in the Neon SQL Editor:
Flyway must own schema history and transactional migration execution. Do not
manually remove tables or history to "fix" this startup timeout.

## 2. One-time/manual migration OUTSIDE Vercel

Merge the reviewed PR. In GitHub → repository **Settings → Environments →
catalog-production-db**, configure required reviewers and restrict deployment branches to
`main`. Add three environment secrets (targeting the selected Neon **production** branch). Create a restore point / database branch before first migration if data is valuable:

- `CATALOG_MIGRATION_JDBC_URL`: direct **non-`-pooler`** Neon JDBC
  connection (e.g. `jdbc:postgresql://YOUR_DIRECT_HOST:5432/neondb?sslmode=require`).
- `CATALOG_MIGRATION_USERNAME`: Neon database username.
- `CATALOG_MIGRATION_PASSWORD`: Neon database password.

This is **separate from Vercel Settings**; GitHub cannot read Vercel secrets.
Never paste actual credentials into Git files, CI logs or a chat.

Run GitHub Actions → **Apply Catalog Flyway migrations (manual)** → **Run
workflow** on `main`. Approve the `catalog-production-db` environment if configured.
This runs the same Flyway migration on a GitHub-hosted runner with no Vercel HTTP
startup deadline. Repeated executions validate existing migrations and apply
only new versions. The runner refuses Neon `-pooler` connections and
unprotected remote TCP connections.

Verify the Neon SQL checks above again: `catalog.stores` and
`catalog.publications` must exist and Flyway history should report `success
= true`. The initial failure may already have applied some migrations, so
**inspect current state before executing**.

## 3. Disable runtime Flyway only AFTER successful migration

In Vercel → existing `afterform-be` project → **Settings → Environment
Variables**, preserve the existing:

- `SPRING_DATASOURCE_URL` (pooled Neon PostgreSQL JDBC URL, database `neondb`).
- `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`.
- `CATALOG_DB_MAX_POOL_SIZE=3`, `CATALOG_DB_MIN_IDLE=0` (recommended for
  ephemeral runtime instances).

Add this **only after the schema and Flyway history are verified**:

```dotenv
SPRING_FLYWAY_ENABLED=false
```

Then redeploy the latest `main` commit. **Do not disable Flyway before
migration**. Hibernate is still configured with `ddl-auto=validate` and will
refuse to start if the Catalog schema is missing or incompatible.

`PORT` is set by Vercel to `80`; do not hard-code another port. Your app
uses `${PORT:8081}` already.

## 4. Check startup and diagnose remaining delay

Request each endpoint on the current Vercel URL:

```bash
curl -i https://YOUR-VERCEL-DOMAIN.vercel.app/actuator/health
curl -i https://YOUR-VERCEL-DOMAIN.vercel.app/
```

Expected: `200 {"status":"UP"}` and JSON API index. Look for
**`Tomcat started on port 80`** and **`Started CatalogApplication`** in the
runtime logs. `Tomcat initialized` is **not** enough.

If the timeout remains after disabling runtime migration, inspect newer
Hibernate/JPA startup logs and isolate Neon cold compute start, JPA DDL
validation and image/memory limits. Do not report the deployment as fixed
without a real 200 health response.

## 5. Verification in GitHub Actions (disposable database)

Normal CI still runs `mvn clean verify` against PostgreSQL 16; the Catalog
migration runner is tested **twice** after CI's main Maven tests to prove
repeat runs are idempotent and use the same compiled migration resources.
No PR CI workflow touches Neon. The separate manual workflow is the only
workflow intended to mutate the target Neon Catalog database.

This fix is distinct from optional S3 storage `afterform-media`, which stays
disabled until its own environment variables are configured.
