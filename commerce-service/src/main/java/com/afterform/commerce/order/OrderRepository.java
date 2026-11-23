package com.afterform.commerce.order;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    Optional<OrderEntity> findByStoreIdAndIdempotencyKey(UUID storeId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.storeId=:storeId and o.id=:orderId")
    Optional<OrderEntity> lockByTenantAndId(@Param("storeId") UUID storeId,
                                             @Param("orderId") UUID orderId);
}
