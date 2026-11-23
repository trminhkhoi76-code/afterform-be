# Neon for AFTERFORM (Java 25 / Spring Boot / PostgreSQL)

This integration targets the **existing** Neon resources selected for this app:

- Org: `org-sweet-violet-18230803`
- Project: `small-night-14869410`
- Branch: `production`
- Services: PostgreSQL (always included) and **private** Object Storage bucket `afterform-media`
- Config: [neon.ts](../neon.ts) declares the bucket, while the linked `.neon` context selects the org, project and branch.

**Do not create a second Neon project.** The `neon.ts` file is code-only until `neon deploy` actually runs. No credentials should be committed.

## 1. Neon CLI setup in repository root

```bash
npm i -g neon@latest
# Fallback if global installation fails:
npx neon@latest --version

neon login
neon init -y --org-id org-sweet-violet-18230803 \
  --project-id small-night-14869410 \
  --branch production \
  --services postgres,object-storage

# Neon CLI may treat Postgres as implicit and reject it in --services.
# If it does, retry only the service flag using --services object-storage.
# Inspect git diff neon.ts after init: preserve the declared private bucket.

npm install
neon config plan
neon deploy
neon env pull
```

`neon init` links the specified branch and may write `.neon` (gitignored); `neon deploy` reconciles resources and pulls Neon env vars into `.env.local`, or `.env` when that file already exists. Review any existing `.env` before deploy: Neon may modify it.

**Auth:** if `neon login` opens a browser, complete OAuth there. If a CLI session expires or fails, repeat `neon login` and retry the original command. An existing account/project should not be replaced by a claimable database.

## 2. Credentials returned by Neon

PostgreSQL:
- `DATABASE_URL` and usually `DATABASE_URL_UNPOOLED` (PostgreSQL URIs, **not JDBC URLs**).

Private object storage, after deploy:
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_ENDPOINT_URL_S3`
- `AWS_REGION`

Add `AFTERFORM_MEDIA_BUCKET=afterform-media` **locally** to the ignored env file and to your real service runtime when you want to enable storage. The default bucket is private: do not grant public-read or add an anonymous upload route.

`npm run start` (or `npm run start:catalog`) uses [scripts/neon-runtime.mjs](../scripts/neon-runtime.mjs) to load `.env.local` / `.env`, convert the unpooled Neon PostgreSQL URL to `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` and start Spring Boot.

**Why JDBC?** AFTERFORM uses Spring Data JPA, Hibernate, Flyway, PostgreSQL JDBC and HikariCP. Do **not** replace the Java data layer with Neon JS HTTP/SQL drivers. The helper only converts the URL once. Spring Boot's native driver handles SQL connections and the existing Flyway migrations own the schema.

`COMMERCE_DB_*` is also populated by the helper for local development when both logical schemas share one PostgreSQL cluster. **Do not share a production superuser across microservices**; issue separate restricted database roles once Commerce is deployed.

## 3. Verify without leaking credentials

```bash
npm run test:neon
npm run neon:check   # Prints presence/absence only, never credential values.
npm run build        # Invokes mvn -B -ntp clean verify with Neon/JDBC env loaded.
npm run start
# In another terminal:
curl -i http://localhost:8081/actuator/health
curl -i http://localhost:8081/
curl -i http://localhost:8081/api/v1/public/stores/afterform/bootstrap
```

Expected: `GET /actuator/health` reports `UP` after Flyway/JPA successfully connect. `GET /` returns Catalog API JSON. The published bootstrap can return 404 until a store is published; a 404 for **unpublished store** is normal and not the previous Vercel runtime 500.

**Live Neon Object Storage round trip** (explicitly writes a temporary image and deletes it; requires real AWS_* vars from `neon deploy` and a bucket):

```bash
npm run neon:storage-smoke
```

This launches `NeonMediaStorageLiveTests`, which performs S3 **PUT → GET → DELETE** in bucket `afterform-media`. It is **opt-in**, is not run in ordinary PR CI, and must not be described as passed unless its actual command exits successfully. The normal `NeonMediaStorageTests` verifies SDK SigV4 signing offline.

**Safety:** GitHub Actions uses disposable PostgreSQL 16 rather than your `production` Neon branch. Do not run migration-producing `npm run build` against production customer data casually; create a Neon test branch to run integration tests, then promote reviewed Flyway migrations through a controlled deployment. Flyway may create/update application schema on first connection.

## 4. Vercel deployment (fixes the original missing DataSource URL)

Vercel Container Runtime starts Java directly using [Dockerfile.vercel](../Dockerfile.vercel) — it does **not** run the local Node wrapper or `docker-compose.yml`. Put **JDBC-format** variables in the existing Vercel project's Environment Variables, separately for Production and Preview:

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://YOUR_NEON_DIRECT_HOST:5432/YOUR_DB?sslmode=require
SPRING_DATASOURCE_USERNAME=YOUR_NEON_DB_USER
SPRING_DATASOURCE_PASSWORD=YOUR_NEON_DB_PASSWORD
CATALOG_DB_MAX_POOL_SIZE=3
CATALOG_DB_MIN_IDLE=0
AFTERFORM_CORS_ORIGINS=https://trminhkhoi76-code.github.io
```

Select the host/user/password from the `DATABASE_URL_UNPOOLED` output; do not paste the entire `postgresql://user:pass@host/db` URI into `SPRING_DATASOURCE_URL` because it is not a valid JDBC URL. Add the `AWS_*` vars and `AFTERFORM_MEDIA_BUCKET` to Vercel only once a protected media/admin workflow actually uses storage. Secrets belong in Vercel Settings, not GitHub source.

After setting Vercel env, **redeploy**; do not expect an old deployment to pick up new vars. Check Vercel runtime logs, `/actuator/health`, and the API root. If startup still fails, copy only the error trace and redact DB URLs/tokens.

## 5. What this does *not* implement

- Neon project authorization/provisioning is an **external action**; committing `neon.ts` alone does not mean `neon deploy` succeeded.
- This PR adds an **internal** storage adapter, not public/admin upload endpoints; identity/RBAC and content validation still block untrusted uploads.
- No online payment, media CDN, customer signup or automated migration promotion.
- Do not report a production integration as healthy based solely on Node unit tests or disposable GitHub PostgreSQL CI.

Official references: [Neon agent skill](https://github.com/neondatabase/agent-skills/blob/main/skills/neon/SKILL.md), [Neon Object Storage](https://github.com/neondatabase/website/blob/main/content/docs/storage/get-started.md), [Spring Data SQL](https://docs.spring.io/spring-boot/reference/data/sql.html).
