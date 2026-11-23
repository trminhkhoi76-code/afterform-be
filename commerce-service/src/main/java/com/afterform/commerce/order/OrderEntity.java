package com.afterform.commerce.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders", schema = "commerce")
public class OrderEntity {
    public enum Status { PENDING, CANCELLED }
    @Id private UUID id;
    @Column(name = "store_id", nullable = false) private UUID storeId;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false) private String requestFingerprint;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(nullable = false) private String currency;
    @Column(nullable = false) private BigDecimal total;
    @Version @Column(nullable = false) private Long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected OrderEntity() {}
    public OrderEntity(UUID storeId, String key, String fingerprint, String currency,
                       BigDecimal total, Instant now) {
        this.id = UUID.randomUUID();
        this.storeId = storeId;
        this.idempotencyKey = key;
        this.requestFingerprint = fingerprint;
        this.currency = currency;
        this.total = total;
        this.status = Status.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }
    public UUID id() { return id; }
    public UUID storeId() { return storeId; }
    public String fingerprint() { return requestFingerprint; }
    public String currency() { return currency; }
    public BigDecimal total() { return total; }
    public Status status() { return status; }
    public void cancel(Instant now) {
        if (status != Status.PENDING) throw new IllegalStateException("Order not pending");
        status = Status.CANCELLED;
        updatedAt = now;
    }
}
