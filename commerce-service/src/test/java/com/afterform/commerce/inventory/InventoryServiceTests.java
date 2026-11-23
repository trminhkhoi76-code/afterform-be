package com.afterform.commerce.inventory;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL integration test: Flyway, Hibernate and row-locked stock accounting. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class InventoryServiceTests {
    @Autowired InventoryService inventory;
    @Autowired InventoryBalanceRepository balances;
    @Autowired InventoryReservationRepository reservations;
    @Autowired InventoryMovementRepository movements;

    @Test void receiveReserveCommitAndIdempotency() {
        UUID store=UUID.randomUUID(),variant=UUID.randomUUID(),id=UUID.randomUUID();
        inventory.receive(store,variant,10);
        assertEquals(10,inventory.available(store,variant));
        inventory.reserve(store,variant,id,3,Duration.ofMinutes(15));
        inventory.reserve(store,variant,id,3,Duration.ofMinutes(15));
        assertEquals(7,inventory.available(store,variant));
        assertTrue(inventory.commit(id));
        assertFalse(inventory.commit(id));
        assertEquals(7,inventory.available(store,variant));
        var b=balances.findById(new BalanceId(store,variant)).orElseThrow();
        assertEquals(7,b.onHand());
        assertEquals(0,b.reserved());
        assertEquals(2,movements.countByStoreId(store));
    }

    @Test void releaseAndCrossTenantBoundaries() {
        UUID store=UUID.randomUUID(), other=UUID.randomUUID(), variant=UUID.randomUUID();
        UUID id=UUID.randomUUID();
        inventory.receive(store,variant,5);
        inventory.reserve(store,variant,id,4,Duration.ofMinutes(10));
        assertEquals(0,inventory.available(other,variant));
        assertThrows(IllegalArgumentException.class,
            ()->inventory.reserve(store,variant,id,2,Duration.ofMinutes(10)));
        assertTrue(inventory.release(id));
        assertFalse(inventory.release(id));
        assertEquals(5,inventory.available(store,variant));
    }

    @Test void cannotReserveMoreThanAvailable() {
        UUID store=UUID.randomUUID(), variant=UUID.randomUUID();
        inventory.receive(store,variant,2);
        assertThrows(InsufficientStockException.class,
            ()->inventory.reserve(store,variant,UUID.randomUUID(),3,Duration.ofMinutes(5)));
        assertEquals(2,inventory.available(store,variant));
    }
}
