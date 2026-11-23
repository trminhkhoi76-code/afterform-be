# AFTERFORM Backend (TODO)

This repository is reserved for the **API, persistence and authenticated admin services** of the AFTERFORM multi-store storefront engine. The frontend lives in [afterform](https://github.com/trminhkhoi76-code/afterform).

> Status: **contract / roadmap only**. No backend API, authentication, database or production checkout is implemented yet.

## First endpoint to implement

`GET /api/v1/public/stores/{storeSlug}/bootstrap`

- Public, read-only, no customer credentials required
- Respond `200` with the documented store, products and home payload; an empty product list is valid
- Respond `404` for unknown stores, `5xx` for server failures
- Ensure the returned `store.slug` exactly matches `{storeSlug}`
- Enable CORS for the deployed AFTERFORM frontend origin
- Use HTTPS when frontend is hosted over HTTPS
- Provide stable, cache-friendly responses and URL-addressable product images

See [the API contract](docs/bootstrap-contract.md).

## Implementation backlog

- [ ] Choose backend framework and deployment strategy
- [ ] Define persistence for stores, themes, categories, products, variants, media, pages and published versions
- [ ] Implement the read-only public bootstrap endpoint; validate by schema and tenant
- [ ] Add integration tests for 200, 404, empty catalog, invalid store and CORS
- [ ] Add secure admin auth + per-store permissions; protect every write by tenant ID
- [ ] Implement draft/publish APIs for store settings, catalog and page content
- [ ] Add image/media storage and upload authorization
- [ ] Add server-validated checkout, atomic stock changes, orders, payment webhooks and audit logs **before** live commerce
- [ ] Coordinate migration from the frontend's browser-local demo data
- [ ] Add CI and environment configuration; never commit API secrets

The frontend loads live data only if `runtime-config.js` has a valid `apiBaseUrl`. When missing, unreachable, timed out, or invalid, it uses the existing fashion demo data. API-mode admin is intentionally **read-only** until backend write endpoints are available.
