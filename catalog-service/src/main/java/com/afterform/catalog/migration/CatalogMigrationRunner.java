package com.afterform.catalog.migration;

import java.net.URI;
import org.flywaydb.core.Flyway;

/**
 * Explicit one-shot Catalog migration entrypoint.
 * Runs in CI/manual GitHub Actions, never inside Vercel's request cold start.
 */
public final class CatalogMigrationRunner {
    private CatalogMigrationRunner() {}

    public static void main(String[] args) {
        String url = required("CATALOG_MIGRATION_JDBC_URL");
        String username = required("CATALOG_MIGRATION_USERNAME");
        String password = required("CATALOG_MIGRATION_PASSWORD");
        validateUrl(url);

        Flyway flyway = Flyway.configure()
            .dataSource(url, username, password)
            .locations("classpath:db/migration")
            .defaultSchema("public")
            .load();

        // Flyway owns migrations. Never recreate tables with a separate SQL runner.
        flyway.migrate();
        flyway.validate();
        System.out.println("Catalog Flyway migrations applied and validated.");
    }

    static void validateUrl(String url) {
        if (!url.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("Migration URL must be a PostgreSQL JDBC URL");
        }
        final URI parsed;
        try {
            parsed = URI.create(url.substring("jdbc:".length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid PostgreSQL JDBC URL", e);
        }
        String host = parsed.getHost();
        if (host == null || host.isBlank() || parsed.getRawUserInfo() != null) {
            throw new IllegalArgumentException("Migration URL must have a host and no embedded credentials");
        }
        if (host.contains("-pooler")) {
            throw new IllegalArgumentException("Use a Neon DIRECT host for migrations, not -pooler");
        }
        boolean local = host.equals("localhost") || host.equals("127.0.0.1");
        String query = parsed.getRawQuery();
        if (!local && (query == null || !query.matches("(?s).*?(?:^|&)sslmode=(?:require|verify-ca|verify-full)(?:&|$).*"))) {
            throw new IllegalArgumentException("Remote migrations require sslmode=require or stronger");
        }
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing migration environment variable: " + key);
        }
        return value;
    }
}
