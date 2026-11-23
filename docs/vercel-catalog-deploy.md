# AFTERFORM Catalog API on Vercel

**Scope:** Vercel Container Runtime (Beta) deploys **only the Catalog API** from the Java 25/Spring Boot Maven monorepo. The Commerce service, the PostgreSQL server and the storefront frontend are **not** hosted by this Vercel project.

## Why the previous Vercel deployment returned a 404

The project was imported from `afterform-be` with Application Preset **Other** and no root Vercel entrypoint. A "Deployment completed" message does not imply that a Java server or route was available.

This repo now contains:
- `Dockerfile.vercel` at the repo root: builds Catalog and starts it as a Java 25 container.
- `vercel.json`: selects the `container` framework; the container runtime routes incoming HTTP traffic to the image.
- `GET /`: API discovery JSON (not a website). The storefront UI lives in the separate `afterform` repository.

## Existing Vercel project configuration (do not create another project)

1. Open the existing `afterform-be` project in Vercel.
2. **Settings → General / Build and Deployment**: Git Repository `trminhkhoi76-code/afterform-be`, Production Branch `main`, Root Directory **`./`** (repository root). Do not select `catalog-service/`: Docker build needs parent Maven reactor and both module POMs.
3. Select **Container** framework if the dashboard offers it. `vercel.json` already pins the framework; do not override it back to `Other`. Clear previously customized Build Command / Install Command / Output Directory, if present, so Vercel uses `Dockerfile.vercel`.
4. Connect an **external, reachable PostgreSQL 16+ database** (managed or VPS). Vercel containers are stateless functions: they do **not** run `docker-compose.yml` or host persistent PostgreSQL volumes. Keep database credentials private.
5. In **Settings → Environment Variables**, set:
   - `SPRING_DATASOURCE_URL`: `jdbc:postgresql://YOUR_DB_HOST:5432/YOUR_DB?sslmode=require` (use provider's actual TLS settings)
   - `SPRING_DATASOURCE_USERNAME`: your catalog DB user
   - `SPRING_DATASOURCE_PASSWORD`: DB password (mark sensitive)
   - `AFTERFORM_CORS_ORIGINS`: `https://trminhkhoi76-code.github.io` (add exact preview origins only if necessary)
   - Optional: `CATALOG_DB_MAX_POOL_SIZE=3`, `CATALOG_DB_MIN_IDLE=0` to reduce connection usage under autoscaling. Adjust against database max connections.
6. **Redeploy** the latest `main` commit after merging the PR and saving environment variables. Verify Build Logs show Docker/Container image creation and Runtime Logs show Spring Boot + Flyway connecting to PostgreSQL.
7. Test the actual API routes:
   ```bash
   curl -i https://YOUR-VERCEL-DEPLOYMENT.vercel.app/
   curl -i https://YOUR-VERCEL-DEPLOYMENT.vercel.app/actuator/health
   curl -i https://YOUR-VERCEL-DEPLOYMENT.vercel.app/api/v1/public/stores/afterform/bootstrap
   ```
   Expected: `GET /` returns **200 JSON** with `service: afterform-catalog`; `/actuator/health` returns **200** when the service/database are healthy. The bootstrap may return **404** until `afterform` has an active publication in the database; this is different from a Vercel deployment routing 404. No backend fake store is seeded.
8. Once healthy, set the frontend repository `runtime-config.js` `apiBaseUrl` to the Vercel HTTPS origin and validate preflight/CORS from GitHub Pages. Do not turn on real checkout or public Commerce routes.

### Resource/security considerations

- Vercel Container Runtime is Beta and bills Active CPU/resources; check plan limits before enabling traffic.
- Each container instance has its own Hikari pool. Multiple function instances can exhaust database connections. Start small and monitor connections, p95 latency and failures.
- The root container inherits `PORT` from Vercel when provided and otherwise listens on port 8081.
- Hibernate runs with `ddl-auto=validate`; Flyway runs migrations on startup. Concurrent cold-start migrations and short-lived function instances have operational implications. For a higher-traffic environment, run migrations in a separate deployment job and disable runtime migration once verified.
- Catalog public bootstrap is read-only; secure/admin/Commerce features remain future issues. There are no DB passwords or provider tokens in Git.
- If the container cannot start, check PostgreSQL reachability, TLS settings, database credentials, memory limits and Boot/Flyway logs before attempting to change URLs or build commands.

### Official sources

- [Agent setup playbook](https://vercel.com/get-started.md)
- [Vercel Container Runtime](https://vercel.com/kb/guide/does-vercel-support-docker-deployments)
- [Root Dockerfile support](https://vercel.com/changelog/deploy-a-container-from-a-plain-dockerfile)
- [Vercel Services](https://vercel.com/kb/guide/vercel-services)
