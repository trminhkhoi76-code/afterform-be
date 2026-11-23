package com.afterform.catalog.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StorePublicationRepository extends JpaRepository<StorePublicationEntity, PublicationId> {
}
