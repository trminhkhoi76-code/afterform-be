package com.afterform.catalog.migration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CatalogMigrationRunnerTests {
    @Test void refusesPooledHostAndNonJdbc() {
        assertThrows(IllegalArgumentException.class,
            () -> CatalogMigrationRunner.validateUrl(
                "jdbc:postgresql://ep-example-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require"));
        assertThrows(IllegalArgumentException.class,
            () -> CatalogMigrationRunner.validateUrl("postgresql://localhost/test"));
    }
    @Test void requiresTlsForRemoteHost() {
        assertThrows(IllegalArgumentException.class,
            () -> CatalogMigrationRunner.validateUrl("jdbc:postgresql://ep-example.neon.tech/neondb"));
        assertDoesNotThrow(
            () -> CatalogMigrationRunner.validateUrl("jdbc:postgresql://ep-example.neon.tech/neondb?sslmode=require"));
    }
    @Test void acceptsDisposableLocalDatabaseAndNoEmbeddedCredentials() {
        assertDoesNotThrow(() -> CatalogMigrationRunner.validateUrl(
            "jdbc:postgresql://localhost:5432/catalog_ci"));
        assertThrows(IllegalArgumentException.class,
            () -> CatalogMigrationRunner.validateUrl(
                "jdbc:postgresql://user:secret@localhost:5432/catalog_ci"));
    }
}
