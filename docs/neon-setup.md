# PostgreSQL and optional Neon S3 storage (Catalog Service)

The Vercel app runs **Java 25 / Spring Boot / Spring Data JPA / Flyway** directly.
Neon is a managed PostgreSQL provider, not a Java runtime integration. There is
**no Neon CLI, Node wrapper, `neon.ts`, or npm build requirement** for this repo.

## 1. PostgreSQL — already configured in Vercel

The existing application's `spring.datasource` is configured by ordinary
Spring Boot environment variables:

| Environment variable | Value |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://YOUR_NEON_HOST:5432/neondb?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | Your real Neon database username |
| `SPRING_DATASOURCE_PASSWORD` | Secret DB password |
| `CATALOG_DB_MAX_POOL_SIZE` | `3` (optional) |
| `CATALOG_DB_MIN_IDLE` | `0` (optional) |

**The database name `neondb` is valid.** In local Docker Compose,
`catalog-db` is only the hostname of the local PostgreSQL service,
`catalog_local` is its database name, and `catalog` is the PostgreSQL
**schema** where Flyway creates the application tables. You do not have to
rename Neon `neondb` to `catalog-db`.

Keep these values in Vercel Settings (Production/Preview as needed), not in
`.env.example` or Git history. After changing environment variables,
**redeploy** the Vercel container so the new runtime receives them.

The Catalog service uses the existing PostgreSQL JDBC driver, HikariCP and
Flyway migration `V1__catalog_schema.sql`. No migration/schema change is
included in this PR. A first startup may apply the migration; a production
rollout should use a controlled migration job when load and concurrency grow.

### Health verification

```bash
mvn -B -ntp clean verify
# Requires DB credentials locally, unlike CI which uses disposable PostgreSQL.
mvn -f catalog-service/pom.xml spring-boot:run
curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8081/
```

On Vercel, check `https://YOUR_DEPLOYMENT.vercel.app/actuator/health`.
Expect `200` / `{"status":"UP"}` when the app and health contributors start.
`GET /api/v1/public/stores/afterform/bootstrap` may return 404 until an
active store has a publication; that is normal and distinct from runtime 500.

## 2. Object Storage

The production `afterform-media` bucket serves public catalog assets. For the
publication and media import workflow, see [catalog operations](catalog-operations.md).

### Optional internal adapter for private files

The internal Catalog media service uses the **AWS SDK for Java v2** with Neon's
S3-compatible Object Storage. Use a separate private bucket such as
**`afterform-private-media`** for this adapter.

Creating/configuring the bucket is a Neon Console/cloud operation, **not**
part of the Catalog runtime. Verify the private bucket exists in the intended
Neon project and branch before enabling it.
Never use public-read for raw uploaded customer media.

Add these environment variables to Catalog's runtime *only when ready*:

```dotenv
AFTERFORM_MEDIA_ENABLED=true
AFTERFORM_MEDIA_BUCKET=afterform-private-media
AWS_ENDPOINT_URL_S3=https://YOUR_BRANCH.storage.YOUR_REGION.aws.neon.tech
AWS_REGION=YOUR_NEON_STORAGE_REGION
AWS_ACCESS_KEY_ID=YOUR_NEON_STORAGE_ACCESS_KEY
AWS_SECRET_ACCESS_KEY=YOUR_NEON_STORAGE_SECRET_KEY
```

Use the exact endpoint/region/credentials from Neon Object Storage; the
PostgreSQL hostname is **not** an S3 endpoint. Without
`AFTERFORM_MEDIA_ENABLED=true`, no S3 beans are created and **none** of these
storage variables are required. This keeps a DB-only Vercel deployment healthy.

Credentials are read by the AWS SDK default credentials provider from
`AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`. The service is for trusted
backend code, not an anonymous upload API. Object keys are tenant-scoped under
`stores/{storeId}/media/{mediaId}`; the adapter accepts PNG/JPEG/WebP up to
5 MiB and creates 5-minute signed PUT URLs. The future authenticated Admin API
must verify tenant ownership and inspect/validate uploaded bytes before using
untrusted files. Object Storage is **not** an authentication boundary.

### Tests

Normal CI: Java unit/integration tests + offline S3 SigV4 presigning tests.
They use disposable PostgreSQL 16 and **do not call real Neon**.

To verify actual Neon storage manually with temporary files after setting the
`AWS_*` variables, set `AFTERFORM_MEDIA_BUCKET=afterform-private-media` and run:

```bash
NEON_LIVE_STORAGE_TEST=true mvn -B -ntp -pl catalog-service -Dtest=NeonMediaStorageLiveTests test
```

This is an **opt-in live PUT → GET → DELETE** test. It requires a provisioned
bucket and valid credentials; it must not run on ordinary CI or be claimed
successful unless its output shows success. It is separate from the normal
`mvn clean verify` build. Do not log or commit S3 secrets.

## Deployment safety

- Catalog is the only Spring Boot module deployed by `Dockerfile.vercel`.
- The Commerce service remains independent and is not published through
  this Vercel project.
- The app continues to use Spring's `SPRING_DATASOURCE_*` and Flyway.
- PostgreSQL and storage URLs must not be hardcoded in application source.
- Do not enable public uploads without OIDC/tenant authorization.
- Do not merge before CI verifies all Java tests; do not claim Neon storage
  has been provisioned solely because this PR contains an S3 adapter.

Further reading: https://neon.com/docs/storage/get-started
