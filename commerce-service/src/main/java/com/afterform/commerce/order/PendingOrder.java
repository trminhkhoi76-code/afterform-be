package com.afterform.commerce.order;

import java.math.BigDecimal;
import java.util.UUID;

/** Internal result: status PENDING means stock is held, not paid. */
public record PendingOrder(UUID id, String status, String currency, BigDecimal total) {
    public static PendingOrder from(OrderEntity o) {
        return new PendingOrder(o.id(),o.status().name(),o.currency(),o.total());
    }
}
