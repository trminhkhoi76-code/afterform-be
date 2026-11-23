package com.afterform.catalog.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<StoreEntity, UUID> {
    Optional<StoreEntity> findBySlugAndStatus(String slug, String status);
}
