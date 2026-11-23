# Commerce internal Order Core (Issue #12)

**Scope: internal domain only.** No anonymous checkout endpoint, external payments, or ability to mark an order paid.

- Spring Data JPA entities/repositories own `commerce.orders` and `commerce.order_items`, with tenant-scoped constraints, UUIDs, optimistic version and immutable price/product JSONB snapshots.
- OrderService.place accepts a `CheckoutQuote` whose unit prices and product metadata must be obtained **server-side from trusted Catalog pricing**. Do not wire this record directly to a browser request.
- On creation, the service reserves all requested variant quantities using InventoryService in a single Commerce DB transaction; any insufficient-stock failure rolls back the order and earlier holds.
- The result status is always `PENDING`, which means **reserved, not paid**. The payment state machine, order confirmation and webhook handling are separate work.
- Idempotency key is unique per store and a SHA-256 fingerprint prevents accidental reuse with different cart contents. Concurrent duplicate first requests may yield a unique-key conflict; an eventual authenticated API boundary should perform bounded retries/read-back.
- `OrderService.cancel(storeId, orderId)` checks tenant scope, locks the order, releases every hold and marks cancelled; repeat cancellation is a no-op.
- Reservation expiration is not scheduled yet; a cleanup worker and order status reconciliation are required before production checkout.
- Orders are logically isolated per tenant and do not reference Catalog database rows through FKs. Commerce must receive catalog pricing through a verified internal contract.
- Prices are decimal and stored in the requested ISO currency; taxes, promotions, shipping and multi-currency rounding policy are **not** implemented. Do not expose a paid checkout to customers.

### Verify

CI's PostgreSQL 16 service runs the Commerce Flyway V1/V2 migrations; Spring Boot integration tests cover sequential idempotency, two-line holds, insufficient stock rollback, tenant lookup and cancellation.

### Future gates

OIDC JWT + tenant roles (#6), pricing adapter and public server-validated checkout request (#12), transactional reservation cleanup (#11), verified payment gateways/webhooks (#13), security audit and operational monitoring.
