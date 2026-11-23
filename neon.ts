import { defineConfig } from "@neon/config/v1";

/**
 * Neon PostgreSQL is provided by the linked project automatically.
 * The private bucket is branch-scoped. Do not use public-read for raw uploads.
 * Link to org-sweet-violet-18230803 / small-night-14869410 / production with CLI.
 */
export default defineConfig({
  preview: {
    buckets: {
      "afterform-media": {},
    },
  },
});
