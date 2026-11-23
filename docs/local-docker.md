# Local Docker stack (Issue #16)

Requires Docker Engine and Compose v2. No cloud accounts or secrets are required.

```bash
cp .env.example .env
# Edit CATALOG_DB_PASSWORD with a strong local-only value
docker compose up --build -d
curl -fsS http://localhost:8081/actuator/health/readiness
curl -fsS http://localhost:8082/actuator/health/readiness
curl -i http://localhost:8081/api/v1/public/stores/afterform/bootstrap
```

The bootstrap URL intentionally returns **404** until a real `catalog.publications` snapshot is published. The existing frontend dummy fallback is responsible for showing the fashion demo. This stack never seeds fake live payment or catalog data.

```bash
docker compose logs -f catalog-service
docker compose down
# destructive local reset only:
docker compose down -v
```

Security scope: `docker-compose.yml` is for development and binds application ports to **127.0.0.1 only**; PostgreSQL is internal to the Compose network and has a persistent named volume. Passwords are provided via ignored local `.env`. The image runtime uses non-root users. Before production, pin image digests, enable image scanning/SBOM, set resource limits, managed TLS/ingress and secret manager, backup/PITR, staged migrations and controlled rollouts. CI builds immutable app artifacts and checks both Dockerfiles, but does **not** deploy.

A production deployment pipeline, gateway/auth and live orders are separate issues. Do not expose the catalog DB directly to the internet.
