# Storefront bootstrap API contract (v1)

**Owner:** `afterform-be` (TODO). **Consumer:** `afterform/store-loader.js`.

`GET /api/v1/public/stores/{storeSlug}/bootstrap`

This is a public read endpoint intended to hydrate one storefront in one request. No backend implementation exists yet.

## Example response (200)

```json
{
  "store": {
    "slug": "beauty",
    "name": "AF Beauty",
    "description": "Skincare essentials",
    "locale": "vi-VN",
    "currency": "VND",
    "market": "Vietnam",
    "hero": "https://cdn.example.com/beauty/hero.webp",
    "categories": ["Skincare", "Makeup"],
    "productOptionLabel": "Dung tích",
    "optionKey": "volume",
    "theme": {
      "primaryColor": "#D8A7B1",
      "backgroundColor": "#FFF9F8",
      "textColor": "#22231B"
    },
    "navigation": [
      {"label": "Skincare", "href": "#/category/skincare"},
      {"label": "Shop", "href": "#/shop"}
    ],
    "shipping": {"freeAbove": 500000, "standard": 25000, "express": 50000}
  },
  "products": [
    {
      "id": "serum-001",
      "name": "Hydrating Serum",
      "category": "Skincare",
      "price": 350000,
      "description": "Hydrating facial serum",
      "image": "https://cdn.example.com/beauty/serum.webp",
      "alt": "Hydrating serum bottle",
      "featured": true,
      "isNew": true,
      "optionKey": "volume",
      "optionLabel": "Dung tích",
      "variants": [
        {"sku": "SERUM-30", "options": {"volume": "30ml"}, "stock": 20},
        {"sku": "SERUM-50", "options": {"volume": "50ml"}, "stock": 12}
      ],
      "details": ["Hyaluronic Acid"],
      "attributes": {"skinType": ["Dry", "Combination"]}
    }
  ],
  "looks": [],
  "home": {
    "slides": [{
      "tag": "New arrivals",
      "first": "Beauty",
      "second": "essentials.",
      "description": "Carefully selected skincare",
      "action": "Shop now",
      "href": "#/shop",
      "image": "https://cdn.example.com/beauty/hero.webp",
      "alt": "Beauty collection"
    }],
    "journal": null
  }
}
```

## Contract rules

1. `store` object and `products` array are **required**. `store.slug` must exactly match the path's `storeSlug`.
2. `store.name` must be nonempty; `locale` and `currency` must be supported by `Intl.NumberFormat`. Products require nonempty `id` and `name`, unique IDs, and a finite, nonnegative `price`.
3. Valid `products: []` is a real empty catalog, not a fallback error.
4. Optional: `home.slides`, `home.journal`, `looks`, navigation, theme and shipping. The frontend can generate a generic hero if slides are absent.
5. Phase 1 supports **one selectable variant option axis** per product (`optionKey`). For example `size` or `volume`. Multi-axis variants and variant-specific prices are future work; backend should not publish them until the UI supports them.
6. Set explicit publication state and filter unpublished products on the server in future implementations. A public response must never expose private inventory/supplier/customer data or credentials.
7. Product/media URLs should be publicly fetchable (HTTPS). `theme` colors must be six-digit hex values. `navigation.href` must be an internal `#/` route.
8. No cookies, auth tokens or secrets are sent for this public request. CORS must allow the frontend's origin. Use `Cache-Control` / ETags as appropriate.
9. Return 404 for unknown store; 5xx for errors. Non-200, invalid JSON, schema errors and client timeout trigger the frontend's **dummy fallback**.

## Deferred write endpoints (not yet implemented)

- `POST /api/v1/admin/stores` / `PATCH /api/v1/admin/stores/{id}`
- `POST /api/v1/admin/stores/{id}/publish`
- `GET|POST|PATCH|DELETE /api/v1/admin/stores/{id}/products[/{productId}]`
- `POST /api/v1/admin/stores/{id}/media`
- `POST /api/v1/checkout/orders` with server-side authoritative pricing and atomic inventory checks
- Secure customer auth / order lookup and payment status transitions

**Security:** authentication, store-scoped authorization, audit logs, rate limits, validated payloads, atomic stock decrement and verified payment webhooks are mandatory before any live order is accepted. The current frontend's checkout and account functions are mock/demo only and intentionally disabled in API mode.
