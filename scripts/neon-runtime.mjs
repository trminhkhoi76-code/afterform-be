import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { spawnSync } from "node:child_process";

/**
 * Neon returns postgres:// URIs, while Spring Boot expects jdbc:postgresql://.
 * Keep the username and password out of the JDBC URL and never log secrets.
 */
export function parseNeonPostgresUrl(raw) {
  if (!raw || typeof raw !== "string") throw new Error("DATABASE_URL is missing");
  let uri;
  try { uri = new URL(raw); } catch { throw new Error("Invalid Neon DATABASE_URL"); }
  if (!["postgres:", "postgresql:"].includes(uri.protocol) || !uri.hostname ||
      !uri.pathname || uri.pathname === "/") {
    throw new Error("Expected a PostgreSQL URI with host and database name");
  }
  if (!uri.username || !uri.password) throw new Error("Neon credentials missing in database URI");
  const sslmode = uri.searchParams.get("sslmode") || "require";
  if (!new Set(["require", "verify-ca", "verify-full"]).has(sslmode)) {
    throw new Error("Neon requires an SSL-enabled connection");
  }
  return {
    url: "jdbc:postgresql://" + uri.hostname + ":" + (uri.port || "5432") +
         uri.pathname + "?sslmode=" + sslmode,
    username: decodeURIComponent(uri.username),
    password: decodeURIComponent(uri.password),
  };
}

function loadLocalEnv() {
  if (existsSync(resolve(".env.local"))) process.loadEnvFile(resolve(".env.local"));
  else if (existsSync(resolve(".env"))) process.loadEnvFile(resolve(".env"));
}

export function buildSpringEnv(source = process.env) {
  const env = { ...source };
  // Explicit Spring/Vercel configuration takes precedence over Neon-pulled vars.
  if (!env.SPRING_DATASOURCE_URL) {
    const parsed = parseNeonPostgresUrl(env.DATABASE_URL_UNPOOLED || env.DATABASE_URL);
    env.SPRING_DATASOURCE_URL = parsed.url;
    env.SPRING_DATASOURCE_USERNAME = parsed.username;
    env.SPRING_DATASOURCE_PASSWORD = parsed.password;
  }
  if (!env.COMMERCE_DB_URL) {
    // Logical schema split on a shared Neon instance is dev/test only.
    env.COMMERCE_DB_URL = env.SPRING_DATASOURCE_URL;
    env.COMMERCE_DB_USERNAME = env.SPRING_DATASOURCE_USERNAME;
    env.COMMERCE_DB_PASSWORD = env.SPRING_DATASOURCE_PASSWORD;
  }
  env.CATALOG_DB_MAX_POOL_SIZE ||= "3";
  env.CATALOG_DB_MIN_IDLE ||= "0";
  return env;
}

if (process.argv[1] && import.meta.url === new URL("file://" + resolve(process.argv[1])).href) {
  try {
    loadLocalEnv();
    const action = process.argv[2] || "catalog";
    const env = buildSpringEnv();
    if (action === "check") {
      console.log("Neon Postgres -> Spring JDBC environment: configured");
      console.log("Neon Object Storage: " +
        (["AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_ENDPOINT_URL_S3", "AWS_REGION"]
          .every(k => !!env[k]) ? "credentials present" : "not configured"));
      process.exit(0);
    }
    const command = process.platform === "win32" ? "mvn.cmd" : "mvn";
    const root = resolve(".");
    let args;
    let cwd = root;
    if (action === "build") {
      args = ["-B", "-ntp", "clean", "verify"];
    } else if (action === "storage-smoke") {
      if (!env.AFTERFORM_MEDIA_BUCKET || !env.AWS_ENDPOINT_URL_S3) {
        throw new Error("Storage smoke test requires Neon bucket and credentials");
      }
      env.NEON_LIVE_STORAGE_TEST = "true";
      args = ["-pl", "catalog-service", "-Dtest=NeonMediaStorageLiveTests", "test"];
    } else if (["catalog", "commerce"].includes(action)) {
      cwd = resolve(action + "-service");
      args = ["spring-boot:run"];
    } else {
      throw new Error("Choose build, catalog, commerce, storage-smoke or check");
    }
    const child = spawnSync(command, args, {
      cwd, env, stdio: "inherit"
    });
    if (child.error) throw child.error;
    process.exit(child.status ?? 1);
  } catch (error) {
    console.error("Neon environment setup: " + error.message);
    process.exit(1);
  }
}
