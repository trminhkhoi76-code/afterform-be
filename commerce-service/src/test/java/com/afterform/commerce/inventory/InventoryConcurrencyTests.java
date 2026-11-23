package com.afterform.commerce.inventory;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Real PostgreSQL concurrency checks using independent transactions/threads. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
class InventoryConcurrencyTests {
    @Autowired InventoryService inventory;
    @Autowired InventoryBalanceRepository balances;

    @Test
    void concurrentReservationsCannotOversell() throws Exception {
        UUID store=UUID.randomUUID(),variant=UUID.randomUUID();
        inventory.receive(store,variant,1);
        ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch gate=new CountDownLatch(1);
        try {
            Future<Boolean> a=executor.submit(()->attemptReserve(store,variant,gate));
            Future<Boolean> b=executor.submit(()->attemptReserve(store,variant,gate));
            gate.countDown();
            int success=(a.get(20,TimeUnit.SECONDS)?1:0)+(b.get(20,TimeUnit.SECONDS)?1:0);
            assertEquals(1,success);
            assertEquals(0,inventory.available(store,variant));
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean attemptReserve(UUID store,UUID variant,CountDownLatch gate) throws Exception {
        gate.await();
        try {
            inventory.reserve(store,variant,UUID.randomUUID(),1,Duration.ofMinutes(5));
            return true;
        } catch(InsufficientStockException expected) {
            return false;
        }
    }

    @Test
    void aReservationCannotBeReleasedAndCommittedTwice() throws Exception {
        UUID store=UUID.randomUUID(),variant=UUID.randomUUID(),reservation=UUID.randomUUID();
        inventory.receive(store,variant,1);
        inventory.reserve(store,variant,reservation,1,Duration.ofMinutes(5));
        ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch gate=new CountDownLatch(1);
        try {
            Future<Boolean> release=executor.submit(()->{ gate.await(); return inventory.release(reservation); });
            Future<Boolean> commit=executor.submit(()->{ gate.await(); return inventory.commit(reservation); });
            gate.countDown();
            int success=(release.get(20,TimeUnit.SECONDS)?1:0)
                +(commit.get(20,TimeUnit.SECONDS)?1:0);
            assertEquals(1,success);
            var balance=balances.findById(new BalanceId(store,variant)).orElseThrow();
            assertEquals(0,balance.reserved());
            assertEquals(balance.onHand(),inventory.available(store,variant));
            assertTrue(balance.onHand()==0 || balance.onHand()==1);
        } finally {
            executor.shutdownNow();
        }
    }
}
