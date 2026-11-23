# Commerce inventory: JPA-first (Issue #11)

**Implemented as an internal domain service, not a public API.** There is no anonymous checkout or inventory mutation endpoint.

- Commerce Service owns `commerce.inventory_balances`, `commerce.inventory_reservations` and `commerce.inventory_movements` in its own PostgreSQL database and Flyway migration. Catalog has no SQL access to this schema.
- `InventoryService.receive`: trusted restock/provision operation (positive quantities).
- `reserve`: caller-generated UUID makes repeating the same reservation idempotent; quantity/TTL validated (max 24 hours); each stock row protected by JPA **PESSIMISTIC_WRITE**.
- `release`: idempotent; restores `reserved` availability.
- `commit`: trusted order/payment confirmation only; decrements both on-hand/reserved, records immutable movement; refuses expired reservations.
- `expire`: idempotent one-at-a-time hook; a bounded scheduler/worker and reconciliation tests will be needed before real checkout. Default is **no background worker**.
- DB enforces `on_hand >= reserved >= 0`; unknown `store_id`/variant are not provisioned by the public API.
- New stock-row creation is not concurrency-safe when two administrators provision the same ID simultaneously: a unique-key exception requires bounded caller retry; production ingestion workflow will address this.

Local Docker requires `COMMERCE_DB_PASSWORD` in ignored `.env`. JVM startup requires `COMMERCE_DB_URL/USERNAME/PASSWORD`. CI uses one **disposable** PostgreSQL server with separate `catalog` and `commerce` schemas for economy; production roles must not share credentials.

**Important missing gates:** verify tenant identity before any caller can invoke these methods; implement order/payment state machine and expiring reservation cleanup; audit permission and load/row-lock contention with concurrent transactions. No actual payment processing or public order endpoints exist. This PR only completes the stock reservation domain portion of issue #11; external API / operational worker remain separate work.
