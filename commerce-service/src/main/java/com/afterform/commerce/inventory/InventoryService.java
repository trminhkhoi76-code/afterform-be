package com.afterform.commerce.inventory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal domain service only: NEVER expose publicly without tenant-scoped identity and
 * server-validated order pricing. Pessimistic row lock protects each stock balance.
 */
@Service
public class InventoryService {
    private final InventoryBalanceRepository balances;
    private final InventoryReservationRepository reservations;
    private final InventoryMovementRepository movements;
    private final Clock clock;

    public InventoryService(InventoryBalanceRepository balances,
                            InventoryReservationRepository reservations,
                            InventoryMovementRepository movements) {
        this(balances,reservations,movements,Clock.systemUTC());
    }
    InventoryService(InventoryBalanceRepository balances,
                     InventoryReservationRepository reservations,
                     InventoryMovementRepository movements,Clock clock) {
        this.balances=balances;this.reservations=reservations;this.movements=movements;this.clock=clock;
    }

    /**
     * Provisioning requires a trusted admin caller; concurrent first creation of a stock row
     * may return a PK conflict, which should be retried at provisioning boundary.
     */
    @Transactional
    public void receive(UUID storeId, UUID variantId,int quantity) {
        if(quantity<=0) throw new IllegalArgumentException("Quantity must be positive");
        BalanceId id=new BalanceId(storeId,variantId);
        InventoryBalance balance=balances.lockById(id).orElseGet(()->new InventoryBalance(id));
        balance.receive(quantity);
        balances.saveAndFlush(balance);
        movements.save(new InventoryMovement(id,quantity,InventoryMovement.Reason.RESTOCK,null));
    }

    /** Idempotent by caller-generated reservation UUID. */
    @Transactional
    public InventoryReservation reserve(UUID storeId,UUID variantId,UUID reservationId,int qty,Duration ttl) {
        if(qty<=0 || ttl==null || ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofHours(24))>0)
            throw new IllegalArgumentException("Invalid quantity/TTL");
        BalanceId id=new BalanceId(storeId,variantId);
        InventoryBalance balance=balances.lockById(id).orElseThrow(InsufficientStockException::new);
        var previous=reservations.findById(reservationId);
        if(previous.isPresent()) {
            InventoryReservation found=previous.get();
            if(!found.matches(id,qty)) throw new IllegalArgumentException("Reservation ID reused for different item");
            return found;
        }
        balance.reserve(qty);
        balances.save(balance);
        return reservations.saveAndFlush(new InventoryReservation(
            reservationId,id,qty,clock.instant().plus(ttl)));
    }

    /** Returns false if already released/committed; caller can retry safely. */
    @Transactional
    public boolean release(UUID reservationId) {
        InventoryReservation original=reservations.findById(reservationId).orElseThrow();
        InventoryBalance balance=balances.lockById(original.stockId()).orElseThrow();
        InventoryReservation current=reservations.findById(reservationId).orElseThrow();
        if(current.state()!=InventoryReservation.State.ACTIVE) return false;
        balance.release(current.quantity());
        current.release();
        reservations.save(current);
        return true;
    }

    /** Payment/order code calls this only after trusted confirmation, never from a browser flag. */
    @Transactional
    public boolean commit(UUID reservationId) {
        InventoryReservation original=reservations.findById(reservationId).orElseThrow();
        InventoryBalance balance=balances.lockById(original.stockId()).orElseThrow();
        InventoryReservation current=reservations.findById(reservationId).orElseThrow();
        if(current.state()!=InventoryReservation.State.ACTIVE) return false;
        if(!current.expiresAt().isAfter(clock.instant())) throw new IllegalStateException("Reservation expired");
        balance.commit(current.quantity());
        current.commit();
        reservations.save(current);
        movements.save(new InventoryMovement(current.stockId(),-current.quantity(),
            InventoryMovement.Reason.ORDER_COMMIT,reservationId));
        return true;
    }

    @Transactional(readOnly=true)
    public int available(UUID storeId,UUID variantId) {
        return balances.findById(new BalanceId(storeId,variantId)).map(InventoryBalance::available).orElse(0);
    }

    /**
     * Explicit expiration operation (called by a future bounded scheduler/worker).
     * The DB index supports selecting active expired reservations efficiently.
     */
    @Transactional
    public boolean expire(UUID reservationId) {
        InventoryReservation original=reservations.findById(reservationId).orElseThrow();
        if(original.expiresAt().isAfter(clock.instant())) return false;
        return release(reservationId);
    }
}
