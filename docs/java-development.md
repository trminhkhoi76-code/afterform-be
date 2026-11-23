# Running AFTERFORM microservices (foundation)

This is a **scaffold**: the Catalog and Commerce JVMs run independently but do not expose business endpoints yet. API bootstrap and database integration are tracked by [#4](https://github.com/trminhkhoi76-code/afterform-be/issues/4) and [#5](https://github.com/trminhkhoi76-code/afterform-be/issues/5).

Prerequisites: **Java 25 LTS**, Maven 3.9+, internet access to Maven Central for initial builds.

```bash
mvn -B -ntp clean verify
mvn -pl catalog-service -am spring-boot:run
# In another terminal:
mvn -pl commerce-service -am spring-boot:run
```

> For the `spring-boot:run` goal, launch from a module directory when Maven reactor execution causes ambiguous plugin selection; e.g. `cd catalog-service && mvn spring-boot:run`.

Default ports: catalog 8081, commerce 8082. Override with `PORT=...` per process. Check `/actuator/health/liveness` and `/actuator/health/readiness`.

Do not point services at the legacy combined schema without service ownership changes. As of this PR there are **no datasource credentials, runtime database connections, checkout, customer auth or payments**.
