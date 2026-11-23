import test from "node:test";
import assert from "node:assert/strict";
import { parseNeonPostgresUrl, buildSpringEnv } from "./neon-runtime.mjs";

test("Neon Postgres URI converts to JDBC URL without credentials in URL", () => {
  const out = parseNeonPostgresUrl(
    "postgresql://alice:p%40ss%3Aword@ep.example.neon.tech/appdb?sslmode=require&channel_binding=require"
  );
  assert.equal(out.url, "jdbc:postgresql://ep.example.neon.tech:5432/appdb?sslmode=require");
  assert.equal(out.username, "alice");
  assert.equal(out.password, "p@ss:word");
  assert.ok(!out.url.includes("alice"));
  assert.ok(!out.url.includes("word"));
});

test("Unpooled Neon endpoint takes precedence for JDBC and Flyway", () => {
  const env = buildSpringEnv({
    DATABASE_URL: "postgresql://reader:secret@pool.example/appdb?sslmode=require",
    DATABASE_URL_UNPOOLED: "postgresql://writer:secret2@direct.example/appdb?sslmode=require"
  });
  assert.match(env.SPRING_DATASOURCE_URL, /direct\.example/);
  assert.equal(env.SPRING_DATASOURCE_USERNAME, "writer");
  assert.equal(env.COMMERCE_DB_URL, env.SPRING_DATASOURCE_URL);
  assert.equal(env.CATALOG_DB_MAX_POOL_SIZE, "3");
});

test("An explicitly configured Spring datasource is not overridden", () => {
  const env = buildSpringEnv({
    SPRING_DATASOURCE_URL: "jdbc:postgresql://localhost/db",
    SPRING_DATASOURCE_USERNAME: "local",
    SPRING_DATASOURCE_PASSWORD: "test"
  });
  assert.equal(env.SPRING_DATASOURCE_URL, "jdbc:postgresql://localhost/db");
  assert.equal(env.COMMERCE_DB_USERNAME, "local");
});

test("Rejects missing or non-TLS database settings", () => {
  assert.throws(() => parseNeonPostgresUrl(""), /missing/);
  assert.throws(() => parseNeonPostgresUrl("https://example.com"), /PostgreSQL/);
  assert.throws(() => parseNeonPostgresUrl(
    "postgresql://u:p@example.com/db?sslmode=disable"), /SSL/);
});
