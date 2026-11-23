package com.afterform.commerce.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable product/price snapshot, never loaded by joining another service's DB. */
@Entity
@Table(name = "order_items", schema = "commerce")
public class OrderItemEntity {
    @Id private UUID id;
    @Column(name = "store_id",nullable = false) private UUID storeId;
    @Column(name = "order_id",nullable = false) private UUID orderId;
    @Column(name = "variant_id",nullable = false) private UUID variantId;
    @Column(name = "reservation_id",nullable = false) private UUID reservationId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_snapshot",nullable = false,columnDefinition = "jsonb")
    private Map<String, Object> productSnapshot;
    @Column(nullable = false) private int quantity;
    @Column(name = "unit_price",nullable = false) private BigDecimal unitPrice;
    @Column(name = "line_total",nullable = false) private BigDecimal lineTotal;

    protected OrderItemEntity() {}
    public OrderItemEntity(UUID storeId, UUID orderId, QuoteLine line, UUID reservationId) {
        this.id=UUID.randomUUID();this.storeId=storeId;this.orderId=orderId;
        this.variantId=line.variantId();this.reservationId=reservationId;
        this.quantity=line.quantity();this.unitPrice=line.unitPrice();
        this.lineTotal=line.unitPrice().multiply(BigDecimal.valueOf(line.quantity()));
        this.productSnapshot=Map.of("sku",line.sku(),"name",line.productName());
    }
    public UUID reservationId() { return reservationId; }
}
