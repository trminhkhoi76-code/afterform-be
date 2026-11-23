package com.afterform.commerce.order;

import static org.junit.jupiter.api.Assertions.*;
import com.afterform.commerce.inventory.InventoryService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
class OrderServiceTests {
    @Autowired InventoryService stock;
    @Autowired OrderService orders;
    @Autowired OrderRepository repo;
    @Autowired OrderItemRepository items;

    @Test void pendingOrderHoldsStockAndReplayIsIdempotent() {
        UUID store=UUID.randomUUID(), a=UUID.randomUUID(), b=UUID.randomUUID();
        stock.receive(store,a,10);
        stock.receive(store,b,8);
        var quote=new CheckoutQuote("USD",List.of(
            new QuoteLine(a,"A","Shirt",2,new BigDecimal("14.50")),
            new QuoteLine(b,"B","Socks",3,new BigDecimal("5.00"))
        ));
        PendingOrder first=orders.place(store,"cart-one",quote);
        assertEquals("PENDING",first.status());
        assertEquals(0,first.total().compareTo(new BigDecimal("44.00")));
        assertEquals(8,stock.available(store,a));
        assertEquals(5,stock.available(store,b));
        PendingOrder again=orders.place(store,"cart-one",quote);
        assertEquals(first.id(),again.id());
        assertEquals(2,items.findByStoreIdAndOrderId(store,first.id()).size());
        assertEquals(8,stock.available(store,a));

        assertThrows(IllegalArgumentException.class,
            ()->orders.place(store,"cart-one",new CheckoutQuote("USD",List.of(
                new QuoteLine(a,"A","Shirt",1,new BigDecimal("14.50"))))));
    }

    @Test void insufficientStockRollsBackAllHoldsAndOrderRows() {
        UUID store=UUID.randomUUID(), a=UUID.randomUUID(), b=UUID.randomUUID();
        stock.receive(store,a,5);
        stock.receive(store,b,1);
        var quote=new CheckoutQuote("USD",List.of(
            new QuoteLine(a,"A","Item A",2,new BigDecimal("10.00")),
            new QuoteLine(b,"B","Item B",2,new BigDecimal("10.00"))
        ));
        long before=repo.count();
        assertThrows(RuntimeException.class,()->orders.place(store,"insufficient",quote));
        assertEquals(5,stock.available(store,a));
        assertEquals(1,stock.available(store,b));
        assertEquals(before,repo.count());
    }

    @Test void cancellationReturnsReservedInventoryAndEnforcesTenantScope() {
        UUID store=UUID.randomUUID(),other=UUID.randomUUID(),sku=UUID.randomUUID();
        stock.receive(store,sku,3);
        PendingOrder placed=orders.place(store,"cancel-me",new CheckoutQuote("USD",List.of(
            new QuoteLine(sku,"S","Sample",2,new BigDecimal("3.00")))));
        assertEquals(1,stock.available(store,sku));
        assertThrows(java.util.NoSuchElementException.class,()->orders.find(other,placed.id()));
        assertThrows(java.util.NoSuchElementException.class,()->orders.cancel(other,placed.id()));
        assertTrue(orders.cancel(store,placed.id()));
        assertFalse(orders.cancel(store,placed.id()));
        assertEquals(3,stock.available(store,sku));
        assertEquals("CANCELLED",orders.find(store,placed.id()).status());
    }
}
