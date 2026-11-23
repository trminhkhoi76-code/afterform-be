package com.afterform.commerce.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class BalanceId implements Serializable {
    @Column(name = "store_id", nullable = false)
    private UUID storeId;
    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    protected BalanceId() {}
    public BalanceId(UUID storeId, UUID variantId) {
        this.storeId = Objects.requireNonNull(storeId);
        this.variantId = Objects.requireNonNull(variantId);
    }
    public UUID storeId() { return storeId; }
    public UUID variantId() { return variantId; }
    @Override public boolean equals(Object o) {
        return o instanceof BalanceId other
            && Objects.equals(storeId, other.storeId)
            && Objects.equals(variantId, other.variantId);
    }
    @Override public int hashCode() { return Objects.hash(storeId, variantId); }
}
