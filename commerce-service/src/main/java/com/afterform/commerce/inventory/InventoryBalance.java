package com.afterform.commerce.inventory;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "inventory_balances", schema = "commerce")
public class InventoryBalance {
    @EmbeddedId private BalanceId id;
    @Column(name="on_hand", nullable=false) private int onHand;
    @Column(name="reserved", nullable=false) private int reserved;
    @Version @Column(name="version", nullable=false) private Long version;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;

    protected InventoryBalance() {}
    public InventoryBalance(BalanceId id) {
        this.id=id;
        this.updatedAt=Instant.now();
    }
    public BalanceId id() { return id; }
    public int available() { return onHand-reserved; }
    public int onHand() { return onHand; }
    public int reserved() { return reserved; }
    public void receive(int quantity) {
        if(quantity<=0) throw new IllegalArgumentException("Quantity must be positive");
        onHand=Math.addExact(onHand,quantity);
        updatedAt=Instant.now();
    }
    public void reserve(int quantity) {
        if(quantity<=0) throw new IllegalArgumentException("Quantity must be positive");
        if(available()<quantity) throw new InsufficientStockException();
        reserved=Math.addExact(reserved,quantity);
        updatedAt=Instant.now();
    }
    public void release(int quantity) {
        if(quantity<=0 || reserved<quantity) throw new IllegalStateException("Invalid release");
        reserved-=quantity;
        updatedAt=Instant.now();
    }
    public void commit(int quantity) {
        release(quantity);
        onHand-=quantity;
    }
}
