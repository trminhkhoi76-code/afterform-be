package com.afterform.commerce.order;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Trusted pricing projection. NOT a public request DTO.
 * Obtain every unit price from an authenticated, server-side catalog pricing flow.
 */
public record QuoteLine(UUID variantId, String sku, String productName,
                        int quantity, BigDecimal unitPrice) {
    public QuoteLine {
        Objects.requireNonNull(variantId, "variantId");
        if(sku == null || sku.isBlank() || sku.length() > 120)
            throw new IllegalArgumentException("Invalid SKU");
        if(productName == null || productName.isBlank() || productName.length() > 240)
            throw new IllegalArgumentException("Invalid product name");
        if(quantity <= 0 || quantity > 1000)
            throw new IllegalArgumentException("Invalid quantity");
        if(unitPrice == null || unitPrice.signum() < 0 || unitPrice.scale() > 2)
            throw new IllegalArgumentException("Invalid price");
    }
}
