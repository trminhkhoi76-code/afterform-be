# AFTERFORM: staging deployment without AWS

**Status: prepared, not deployed.** This PR adds an optional route from GitHub Actions to GHCR and self-hosted Coolify/VPS. It does not provision a server, create a paid account, or configure secret credentials.

## Topology

- Local development: root `docker-compose.yml` with private PostgreSQL volumes.
- CI: GitHub Actions running Java 25, Maven, and disposable PostgreSQL integration tests.
- Staging image registry: two GHCR packages, `afterform-be-catalog` and `afterform-be-commerce`.
- Staging host: self-hosted Coolify with [deploy/staging/compose.yml](../deploy/staging/compose.yml), referencing *image* tags rather than rebuilding source.
- Edge: HTTPS domain routed via Coolify to `catalog-service:8081`. Do not publish PostgreSQL ports or expose Commerce until its authentication and checkout API are secure.

## One-time staging setup

1. Provision and harden your VPS, install Coolify, enable API access and configure backups. Budget for two JVMs, two PostgreSQL instances and Coolify's own processes.
2. Create a **Docker Compose** resource in Coolify from `deploy/staging/compose.yml`. Configure HTTPS for Catalog on port 8081. Keep Commerce and DB endpoints internal.
3. Set Coolify **runtime environment variables** `CATALOG_DB_PASSWORD`, `COMMERCE_DB_PASSWORD` (strong, distinct) and `AFTERFORM_CORS_ORIGINS=https://trminhkhoi76-code.github.io`. Never commit passwords. The Compose file uses persistent named DB volumes.
4. Ensure GHCR packages can be read from the VPS. Newly created packages may be private: configure registry authentication, or deliberately make packages public if that is acceptable.
5. In GitHub repository `Settings > Environments`, create `staging` with **required reviewers** and allowed deployment branch `main`.
6. Create a Coolify API token with **Deploy permission only**, and copy the Compose resource's authenticated **Deploy Webhook**. In GitHub *staging environment secrets*, add `COOLIFY_TOKEN` and `COOLIFY_DEPLOY_WEBHOOK`.
7. In GitHub Actions run **Publish staging images and optional Coolify deploy** from branch `main`. Default `deploy_to_staging=false` performs verification and image publication **without calling Coolify**. Choose true only to initiate the approved deployment.

## Release contract

- Manual trigger only: no deployment on PR, main push or commit automatically.
- Maven verification and Compose configuration validation must pass before GHCR publishing.
- GHCR packages: `ghcr.io/trminhkhoi76-code/afterform-be-catalog` and `ghcr.io/trminhkhoi76-code/afterform-be-commerce`, each tagged `sha-<commit>` plus mutable `staging`.
- Deploy step is gated on a manual boolean and the GitHub `staging` environment; it uses HTTPS `POST` and a Bearer deploy token. HTTP webhook success means the deployment was *accepted*, not confirmed healthy.
- Read-only bootstrap is available at `GET /api/v1/public/stores/{storeSlug}/bootstrap`. `404` is expected until an active store has a publication; **no demo data is seeded to the API**.
- The frontend's `runtime-config.js` should point to the HTTPS Catalog URL only after real staging is verified; preserve its existing dummy fallback.

## Verifying the remote environment

After Coolify reports a successful deployment, run these commands, substituting your actual staging API hostname:

```bash
curl -fsS https://YOUR-STAGING-DOMAIN/actuator/health/readiness
curl -i https://YOUR-STAGING-DOMAIN/api/v1/public/stores/afterform/bootstrap
```

Also check both services' health in Coolify, active DB sessions, schema migration output and application logs. The Commerce service remains internal. Set alerts for service restart loops, disk usage, backup age and HTTP 5xx.

## Rollback and safety

- The `staging` tag is mutable; for deterministic rollback, replace Compose's image references with the previous immutable `sha-<commit>` tags (or digests), redeploy and verify.
- Flyway schema migrations are forward-only: rolling back an image does not roll back a database migration. Back up and test restore before destructive DB changes.
- Coolify deployment webhook should be an authenticated resource webhook for the **whole Compose resource**; never print the token or webhook secrets in CI logs.
- For real production payment data, use a proper auth system, webhook signature verification, restore testing, TLS and reviewed service-to-service security before enabling Commerce APIs.
- Deploying two JVMs/DBs does not mean production-grade operations are complete. This is an economical, controlled dev/test staging path.

## Primary references

- [Coolify GitHub Actions](https://coolify.io/docs/applications/sources/github/actions)
- [Coolify Deploy Webhooks](https://coolify.io/docs/core/automation/deploy-webhooks)
- [GitHub GHCR publishing](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images)
