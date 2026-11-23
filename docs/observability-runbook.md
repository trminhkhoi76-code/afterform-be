# AFTERFORM — Database observability & operations

**Status:** Proposed; no live database has been provisioned.  
**Monitoring SQL:** [../db/queries/monitoring.sql](../db/queries/monitoring.sql)

## Recommended deployment

**Phase 1/2 MVP:** managed PostgreSQL on Supabase if ease of setup, low maintenance and built-in observability matter most. Use the server-side connection pooler as documented by the provider; never make one physical database connection per browser request.

**AWS-first alternative:** Amazon RDS for PostgreSQL with CloudWatch **Database Insights** (not the retired standalone Performance Insights branding), CloudWatch alarms/logs, private subnet/security groups and a managed secret. It is better when AFTERFORM shares an established AWS/VPC and centralized AWS security/monitoring.

Keep migrations framework-independent (`db/migrations`) and use the backend's chosen migration runner later (Flyway, Liquibase, Prisma, Knex, etc.). This avoids a premature lock-in to a single hosting platform.

## Operational dashboard (start small)

| Signal | Source | What it detects |
| --- | --- | --- |
| DB availability / connection errors | Backend metrics, provider health | Outage / DNS / credentials |
| CPU, IOPS, memory, free storage | Supabase metrics or RDS CloudWatch | Sustained saturation, capacity |
| Active/idle sessions, pool wait | PostgreSQL, pooler | Connection leakage, overload |
| Query execution time and slow queries | pg_stat_statements, slow-query logs | Missing indexes / regressions |
| Lock wait and deadlocks | pg_stat_activity, pg_stat_database | Order / inventory contention |
| API p50/p95/p99, error rate by route | OpenTelemetry / API service | User-facing latency |
| Frontend bootstrap fallback ratio | AFTERFORM client telemetry (TODO) | Backend failures hidden by dummy fallback |
| Publish failure, unpublished stores | Backend events, SQL checks | Misconfigured or broken store content |
| Insufficient-stock conflict rate | Backend counters (TODO) | Overselling pressure / race conflicts |
| DB backup age, restore drill success | Provider console, runbook | Data recovery readiness |
| Replica lag (if replicas exist) | Provider metrics / PostgreSQL | Stale public reads |

**Alarms:** configure availability and backup freshness from day one; set resource, latency and lock thresholds after observing a baseline. Avoid arbitrary fixed limits that create alert fatigue. Alert on prolonged dummy fallback when API is configured—the demo fallback is a user-experience safety net, not proof of service health.

## Query monitoring

- Enable `pg_stat_statements` where allowed (provider configuration or preload required). Rank by total execution time and average duration; analyze representative queries with `EXPLAIN (ANALYZE, BUFFERS)` in safe environments.
- Log slow or failing queries with appropriate redaction / retention. Do not log `shipping_address`, emails, API secrets or full user query parameters.
- Use the SQL in `db/queries/monitoring.sql` for manual spot checks. Some views and statistics require extra DB privileges; do not run monitoring with an application superuser.
- PostgreSQL query statistics and API spans are complementary: a fast SQL query does not guarantee a fast page render.

## Backups and restore gates

1. Before production checkout, confirm automated backup schedule and retention with the chosen provider.
2. Enable Point-in-Time Recovery where the risk and SLA require it.
3. Restore to a separate disposable environment on a routine schedule, and verify `stores`, `orders`, `order_items`, and inventory reconciliation.
4. Define RPO (acceptable data loss) and RTO (restoration time target) before accepting real purchases.
5. Do not assume free-tier backups cover all recovery cases.

## Tenant isolation testing

- Create `fashion` and `beauty` stores with identical product slugs.
- Verify same slug can exist in both and cannot collide within one.
- Verify inserting an order item, category FK, or inventory balance with wrong `store_id` is rejected by composite FK.
- Test admin auth scopes: editor of `beauty` cannot publish/update `fashion`.
- Test no PII or internal-only data appears in public `store_publications.payload`.
- In a managed Postgres Data API environment, keep the application schema private. Only expose a schema after independently testing explicit RLS policies and least-privilege role grants.

## Change controls

- Run schema changes only through reviewed migrations with separate non-production tests, migration ordering and rollback/roll-forward plan.
- Prefer expand-migrate-contract for breaking changes; retain older public bootstrap contract versions until all supported clients have migrated.
- Treat `store_publications` as append-only public snapshots; restrict update/delete to privileged maintenance roles, enforce via privileges/policies when a real DB is provisioned.
- Archive instead of deleting orders or audit history; define retention limits and legal requirements for customer personal information.

## Primary sources

- Supabase observability: https://supabase.com/docs/guides/observability
- Supabase metrics: https://supabase.com/docs/guides/observability/metrics
- Supabase pooler: https://supabase.com/docs/guides/database/connecting-to-postgres/pooling-and-limits
- Supabase backup guidance: https://supabase.com/docs/guides/platform/backups
- AWS RDS Database Insights: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_DatabaseInsights.html
- PostgreSQL stats: https://www.postgresql.org/docs/current/monitoring-stats.html
- pg_stat_statements: https://www.postgresql.org/docs/current/pgstatstatements.html
