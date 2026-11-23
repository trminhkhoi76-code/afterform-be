# Simple Vercel deployment — Catalog API

The application is Java 25 / Spring Boot and uses **Neon PostgreSQL through
ordinary JDBC**. It does not need the Neon CLI, a Node.js wrapper, or a separate
GitHub deployment workflow.

## One source for runtime settings

All **Spring Boot** application settings live in
[`catalog-service/src/main/resources/application.yml`](../catalog-service/src/main/resources/application.yml).
They read environment variables; no passwords or database hostnames are
committed. Keep these variables in the existing Vercel project under Settings →
Environment Variables, selecting **Production**:

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://YOUR_NEON_HOST:5432/neondb?sslmode=require
SPRING_DATASOURCE_USERNAME=YOUR_USER
SPRING_DATASOURCE_PASSWORD=YOUR_PASSWORD
CATALOG_DB_MAX_POOL_SIZE=3
CATALOG_DB_MIN_IDLE=0
AFTERFORM_CORS_ORIGINS=https://trminhkhoi76-code.github.io
```

Flyway remains enabled by default and Hibernate remains in `validate` mode.
Existing database/schema naming is unchanged: on Neon the database is
`neondb`, the application schema is `catalog`, and `catalog-db` is only the
Docker Compose service hostname for local development.

Optional S3-compatible media storage stays **disabled** until configured.
The bucket defaults to `afterform-media`. To enable it, set
`AFTERFORM_MEDIA_ENABLED=true` and the Neon storage credentials
(`AWS_ENDPOINT_URL_S3`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`,
`AWS_SECRET_ACCESS_KEY`). No public media upload route is exposed.

## Deployment policy

- Vercel project Git integration: `trminhkhoi76-code/afterform-be`;
  **Production Branch = `main`**, **Root Directory = repository root (`./`)**,
  Framework = Container.
- Root [`vercel.json`](../vercel.json) is the small **Vercel-required JSON
  exception**: `git.deploymentEnabled` explicitly enables `main` and disables
  `**` (all other branches, including `dev`, `feat/*`, `fix/*`).
  When multiple patterns match, an explicit `true` takes precedence; therefore
  `main` still deploys.
- Vercel builds [`Dockerfile.vercel`](../Dockerfile.vercel) directly on
  `main` pushes/merges. No separate CLI deploy or webhook is configured.
- GitHub Actions has **one** workflow, `java-ci.yml`, which builds/tests
  Java on PRs and `main` with disposable PostgreSQL. **It does not deploy.**
- `PORT` comes from Vercel (often 80); Spring already uses
  `server.port=${PORT:8081}`. Do not hard-code another port.

After merging configuration to `main`, confirm a single Production deployment
is created. No preview deployment should be created for `dev` or `feat/*`.
Verify `GET /` and `GET /actuator/health` return HTTP 200. The public
store bootstrap may return 404 if there is no published store.

**Known limitation:** the previous Vercel cold start timed out after Flyway
validation, before Tomcat began accepting TCP connections. This branch policy
and CI simplification **do not claim to fix** that runtime timeout. Inspect
the latest logs after redeploy; avoid disabling Flyway before schema migration
is confirmed. Do not add additional deployment infrastructure for this scope.

Vercel branch configuration:
https://vercel.com/docs/project-configuration/git-configuration
