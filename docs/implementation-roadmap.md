# Implementation plan (issue → PR)

| Phase | Issues | Expected PR scope |
| --- | --- | --- |
| P0 architecture | #2 | ADR, ownership and version policy |
| P0 foundations | #3 | Maven parent, two independently runnable Spring Boot services, CI |
| P0 catalog persistence | #4 | Catalog-owned Flyway migrations and PostgreSQL integration tests |
| P0 first value | #5 | PostgreSQL-backed public bootstrap matching frontend |
| P1 secure admin | #6 #7 #8 #9 | OIDC, tenant RBAC, product CRUD and atomic publish |
| P1 commerce | #11 #12 | Inventory transactions and authenticated orders |
| P1 platform | #14 #15 #16 #17 | Edge, observability, Docker, end-to-end contracts |
| P2 maturity | #10 #13 #18 | Media uploads, payments/webhooks and outbox |

PR conventions: one independently reviewable feature per PR, `Refs #N` or `Closes #N` only when complete, explicit test evidence, no auto merge. Service-specific contract/API changes need frontend compatibility checks. CI must be green before merging.

**DB migration caveat:** Existing cross-domain v1 DDL was a design artifact. It must be decomposed before being used by individual services; only the new catalog schema should be in catalog-service resources.
