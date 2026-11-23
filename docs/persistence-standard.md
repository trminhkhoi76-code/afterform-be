# Persistence standard — Spring Data JPA (Issue #24)

AFTERFORM services use **Spring Data JPA + Hibernate** for routine database access, not custom SQL/JdbcTemplate wrappers. The Catalog bootstrap now uses `StoreRepository.findBySlugAndStatus` and `StorePublicationRepository.findById` (derived query/standard repository), plus a short read-only service.

## Defaults for new implementations

1. **Entity per service-owned table** using `jakarta.persistence`; explicit schema/table when PostgreSQL schema differs from `public`.
2. Extend `JpaRepository<Entity, ID>` for CRUD, derived queries, built-in pagination/sorting. Prefer `Pageable` and Specifications for increasingly dynamic lists; avoid inventing generic repository infrastructure.
3. Put transaction boundaries in use-case/application services (`@Transactional` for writes; `@Transactional(readOnly = true)` for reads). Do not put transactions in controllers or distribute cross-service ACID transactions.
4. Use JPA/Hibernate `@JdbcTypeCode(SqlTypes.JSON)` for PostgreSQL JSONB when the data is naturally a structured object. Validate JSON content using business/application schema constraints before publishing.
5. **Flyway is the schema authority.** Runtime has `spring.jpa.hibernate.ddl-auto=validate` and `spring.jpa.open-in-view=false`; no `create`, `update` or auto-generated tables.
6. DTOs/API contract are separate from entities; controllers never directly expose a mutable JPA aggregate. Public bootstrap uses an immutable publication payload from an approved publish workflow (issue #9).
7. Do **not** use JPA for every access pattern by dogma. Keep native SQL for measured bulk updates, atomic stock reservation with guards/locking, CTEs, advanced JSONB indexes or heavy reporting **only when justified by correctness/performance**. Document why; no SQL scattered across controllers.
8. Avoid N+1: use entity graphs, projections or selective fetch queries based on profiling. Monitor generated SQL with slow-query metrics and `EXPLAIN`, not verbose PII-bearing logs in production.
9. Services own their DB/schema and repositories; no shared entity JAR or cross-service table access. Catalog owns catalog/publications; Commerce owns inventory/orders, with atomic transactions and integration tests.

## Existing bootstrap mapping

```text
GET /api/v1/public/stores/{slug}/bootstrap
  BootstrapController
    -> PublicationReader (application port)
       -> JpaPublicationReader @Transactional(readOnly = true)
          -> StoreRepository (derived findBySlugAndStatus)
          -> StorePublicationRepository (findById compound PK)
             -> catalog.stores + catalog.publications (JSONB)
```

JPA maps a composite publication key with `@EmbeddedId` and JSONB payload to `Map<String,Object>` via `@JdbcTypeCode(SqlTypes.JSON)`. Spring MVC serializes the Map; no manual `ResultSet` rows or JSON string parsing. The API still enforces active/known/published store identity and returns an ETag based on publication version.

**Current scope:** read-only API plus test fixture persistence. Auth, admin write APIs and publish validation are pending. This PR does **not** change the schema or insert demo data.

## Verify

GitHub Actions uses PostgreSQL 16; run `mvn -B -ntp clean verify` after setting local `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`. Integration tests verify JSONB hydration from PostgreSQL, not just mock repository behavior.

Sources:
- https://docs.spring.io/spring-boot/reference/data/sql.html
- https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html
- https://docs.hibernate.org/orm/7.2/userguide/html_single/
