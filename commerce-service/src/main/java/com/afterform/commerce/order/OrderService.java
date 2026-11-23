package com.afterform.commerce.order;

import com.afterform.commerce.inventory.InventoryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal order application service. No public controller. Quote must come from
 * trusted server-side pricing, not values supplied directly by a browser.
 */
@Service
public class OrderService {
    private static final Duration HOLD_TTL=Duration.ofMinutes(15);
    private static final BigDecimal MAX_TOTAL=new BigDecimal("9999999999999999.99");

    private final InventoryService inventory;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final Clock clock=Clock.systemUTC();

    public OrderService(InventoryService inventory,OrderRepository orders,OrderItemRepository items) {
        this.inventory=inventory;this.orders=orders;this.items=items;
    }

    @Transactional
    public PendingOrder place(UUID storeId,String idempotencyKey,CheckoutQuote trustedQuote) {
        if(storeId == null || idempotencyKey == null || idempotencyKey.isBlank()
           || idempotencyKey.length() > 128)
            throw new IllegalArgumentException("Invalid store or idempotency key");
        if(trustedQuote == null) throw new IllegalArgumentException("Missing quote");

        String fingerprint=QuoteFingerprint.forQuote(trustedQuote);
        var prior=orders.findByStoreIdAndIdempotencyKey(storeId,idempotencyKey);
        if(prior.isPresent()) {
            if(!prior.get().fingerprint().equals(fingerprint))
                throw new IllegalArgumentException("Idempotency key reused with different cart");
            return PendingOrder.from(prior.get());
        }

        BigDecimal total=trustedQuote.lines().stream()
            .map(l->l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())))
            .reduce(BigDecimal.ZERO,BigDecimal::add);
        if(total.compareTo(MAX_TOTAL)>0) throw new IllegalArgumentException("Order total too large");
        var order=new OrderEntity(storeId,idempotencyKey,fingerprint,
                                  trustedQuote.currency(),total,clock.instant());

        // Everything below is in one Commerce DB transaction; stock holds roll back on error.
        orders.saveAndFlush(order);
        for(QuoteLine line:trustedQuote.lines()) {
            UUID reservationId=UUID.randomUUID();
            inventory.reserve(storeId,line.variantId(),reservationId,line.quantity(),HOLD_TTL);
            items.save(new OrderItemEntity(storeId,order.id(),line,reservationId));
        }
        items.flush();
        return PendingOrder.from(order);
    }

    @Transactional
    public boolean cancel(UUID storeId,UUID orderId) {
        if(storeId == null || orderId == null) throw new IllegalArgumentException("Missing order scope");
        var order=orders.lockByTenantAndId(storeId,orderId).orElseThrow();
        if(order.status()==OrderEntity.Status.CANCELLED) return false;
        List<OrderItemEntity> lines=items.findByStoreIdAndOrderId(storeId,orderId);
        for(OrderItemEntity line:lines) {
            if(!inventory.release(line.reservationId()))
                throw new IllegalStateException("Stock reservation is already finalized");
        }
        order.cancel(clock.instant());
        return true;
    }

    @Transactional(readOnly=true)
    public PendingOrder find(UUID storeId,UUID orderId) {
        return orders.findById(orderId)
            .filter(order->order.storeId().equals(storeId))
            .map(PendingOrder::from)
            .orElseThrow();
    }
}
