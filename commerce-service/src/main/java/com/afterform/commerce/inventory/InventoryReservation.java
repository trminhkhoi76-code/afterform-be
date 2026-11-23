package com.afterform.commerce.inventory;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="inventory_reservations", schema="commerce")
public class InventoryReservation {
    public enum State { ACTIVE, RELEASED, COMMITTED }
    @Id private UUID id;
    @Column(name="store_id",nullable=false) private UUID storeId;
    @Column(name="variant_id",nullable=false) private UUID variantId;
    @Column(name="quantity",nullable=false) private int quantity;
    @Enumerated(EnumType.STRING) @Column(name="state",nullable=false)
    private State state;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="created_at",nullable=false) private Instant createdAt;

    protected InventoryReservation() {}
    public InventoryReservation(UUID id, BalanceId stock, int qty, Instant expiresAt) {
        this.id=id; this.storeId=stock.storeId(); this.variantId=stock.variantId();
        this.quantity=qty; this.expiresAt=expiresAt; this.createdAt=Instant.now();
        this.state=State.ACTIVE;
    }
    public UUID id() { return id; }
    public BalanceId stockId() { return new BalanceId(storeId,variantId); }
    public int quantity() { return quantity; }
    public State state() { return state; }
    public Instant expiresAt() { return expiresAt; }
    public boolean matches(BalanceId other,int qty) {
        return stockId().equals(other) && quantity==qty;
    }
    public void release() {
        if(state!=State.ACTIVE) throw new IllegalStateException("Reservation not active");
        state=State.RELEASED;
    }
    public void commit() {
        if(state!=State.ACTIVE) throw new IllegalStateException("Reservation not active");
        state=State.COMMITTED;
    }
}
