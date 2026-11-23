package com.afterform.commerce.inventory;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="inventory_movements", schema="commerce")
public class InventoryMovement {
    public enum Reason { RESTOCK, ORDER_COMMIT, ADJUSTMENT }
    @Id private UUID id;
    @Column(name="store_id",nullable=false) private UUID storeId;
    @Column(name="variant_id",nullable=false) private UUID variantId;
    @Column(name="delta",nullable=false) private int delta;
    @Enumerated(EnumType.STRING) @Column(name="reason",nullable=false) private Reason reason;
    @Column(name="reference_id") private UUID referenceId;
    @Column(name="created_at",nullable=false) private Instant createdAt;

    protected InventoryMovement() {}
    public InventoryMovement(BalanceId stock,int delta,Reason reason,UUID referenceId) {
        this.id=UUID.randomUUID(); this.storeId=stock.storeId(); this.variantId=stock.variantId();
        this.delta=delta; this.reason=reason; this.referenceId=referenceId; this.createdAt=Instant.now();
    }
}
