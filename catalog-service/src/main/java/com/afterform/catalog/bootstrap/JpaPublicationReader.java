package com.afterform.catalog.bootstrap;

import com.afterform.catalog.persistence.PublicationId;
import com.afterform.catalog.persistence.StorePublicationRepository;
import com.afterform.catalog.persistence.StoreRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pure Spring Data JPA read path. Derived query + findById replace handwritten JDBC.
 * The persisted public document is checked again for tenant identity before returning it.
 */
@Service
public class JpaPublicationReader implements PublicationReader {
    private final StoreRepository stores;
    private final StorePublicationRepository publications;

    public JpaPublicationReader(StoreRepository stores, StorePublicationRepository publications) {
        this.stores = stores;
        this.publications = publications;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublishedStore> findCurrent(String storeSlug) {
        return stores.findBySlugAndStatus(storeSlug, "active")
            .filter(store -> store.getPublishedVersion() != null)
            .flatMap(store -> publications.findById(
                new PublicationId(store.getId(), store.getPublishedVersion())
            ))
            .filter(snapshot -> hasMatchingTenant(snapshot.getPayload(), storeSlug))
            .map(snapshot -> new PublishedStore(
                snapshot.getPayload(), snapshot.getId().getVersion()
            ));
    }

    private boolean hasMatchingTenant(Map<String, Object> payload, String slug) {
        if (payload == null || !(payload.get("store") instanceof Map<?, ?> publicStore)) {
            return false;
        }
        return slug.equals(publicStore.get("slug"))
            && payload.get("products") instanceof List<?>;
    }
}
