# AFTERFORM catalog operations

## Product Owner → Senior Developer → Tester

In Settings → Secrets and variables → Actions for `afterform-be`, add the repository secret `OPENAI_API_KEY`. In Actions → AFTERFORM agent workflow, select `main`, choose **Run workflow**, and enter the requested change and expected outcome.

1. Product Owner reads the pinned frontend and backend revisions and produces acceptance criteria and rollout dependencies.
2. Senior Developer edits the relevant checkout and supplies reproducible Git patches and source revisions as the `candidate` artifact.
3. Repository checks apply the patches in a fresh runner, test both repositories, and feed real Catalog API data from disposable PostgreSQL into the storefront loader.
4. Tester reviews both diffs against the acceptance criteria and emits a verdict in `tester.json`. The run passes when both the deterministic checks and Tester pass.

Artifacts include the Product Owner brief, the candidate patches, and the Tester verdict. Apply the supplied patches to review branches based on `source-revisions.txt`. The workflow runs on demand and uses read-only GitHub permissions. Production releases still use the existing Vercel Git integration and deploy after merges to `main`. Keep production credentials, customer records and API keys out of briefs and artifacts.

For a read-only smoke test against the deployed frontend proxy:

```bash
node scripts/storefront-smoke.mjs https://afterform-lovat.vercel.app ../afterform afterform
```

## Import a publication and its public media

This operator flow imports an exported public Catalog bootstrap snapshot into an existing active store. It updates matching existing `catalog.product_media` URL references in the same transaction. Catalog row creation, admin uploads, customer data and orders have separate workflows. Use trusted JPG, PNG, WebP, AVIF, MP4 or WebM files. In product galleries use `video` for a clip and optional `image` for its poster.

Start on an isolated Neon branch. Use that branch's Postgres connection and `public_read` media bucket. In production the public catalog bucket is `afterform-media`. Its contents are served anonymously. The application's internal storage adapter for private files uses a separate private bucket.

Read the current version from the bootstrap ETag or `catalog.stores.published_version`. Export the public JSON and prepare the import using Node 22+, the AWS CLI configured for path-style S3, and the PostgreSQL CLI:

```bash
node scripts/catalog-import.mjs plan snapshot.json 1 ../afterform/assets \
  https://afterform-lovat.vercel.app/assets/ "$NEON_PUBLIC_BUCKET_URL" ./import-output
node scripts/catalog-import.mjs upload "$NEON_PUBLIC_BUCKET_URL" ./import-output/manifest.json
node scripts/catalog-import.mjs verify ./import-output/manifest.json
psql "$DIRECT_DATABASE_URL" -v ON_ERROR_STOP=1 -f ./import-output/publish.sql
```

`NEON_PUBLIC_BUCKET_URL` is the branch S3 endpoint plus the bucket name. Supply AWS credentials only to the CLI. The plan rewrites local `assets/` references and files from the supplied source assets URL. External URLs remain in the document. Each object key includes its SHA-256, and the verifier checks byte length, content type and hash before publishing. Compare the destination branch and tenant with the planned snapshot before running the SQL.

Publishing locks the store row, checks the expected version, and advances the public pointer atomically. Running the same plan again after a successful publication is a no-op. If another publication wins the race, reload its snapshot and generate a new plan. Previous versions remain available for rollback. To revert a public view, restore the previous `published_version` with a condition on the current version and the store slug, then verify the bootstrap ETag and assets again.
