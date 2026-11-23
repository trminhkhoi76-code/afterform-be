package com.afterform.catalog.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import com.afterform.catalog.persistence.PublicationId;
import com.afterform.catalog.persistence.StoreEntity;
import com.afterform.catalog.persistence.StorePublicationEntity;
import com.afterform.catalog.persistence.StorePublicationRepository;
import com.afterform.catalog.persistence.StoreRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full PostgreSQL/JPA integration: verifies Flyway schema, UUID and JSONB mapping,
 * composite publication IDs and the public tenant guard without handwritten SQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class JpaPublicationReaderTests {
    @Autowired StoreRepository stores;
    @Autowired StorePublicationRepository publications;
    @Autowired PublicationReader reader;

    @Test
    void onlyAnActivePublishedTenantIsVisibleAndEmptyProductsRemainEmpty() {
        String slug = "ci-" + UUID.randomUUID().toString().substring(0, 10);
        StoreEntity store = new StoreEntity(slug, "CI Store");
        store.activate();
        stores.saveAndFlush(store);
        assertTrue(reader.findCurrent(slug).isEmpty(), "Draft/unpublished content must remain private");

        var payload = Map.<String, Object>of(
            "store", Map.of("slug", slug, "name", "CI Store"),
            "products", List.of(),
            "home", Map.of(),
            "looks", List.of()
        );
        publications.saveAndFlush(new StorePublicationEntity(
            new PublicationId(store.getId(), 1), payload
        ));
        store.setPublishedVersion(1);
        stores.saveAndFlush(store);

        var result = reader.findCurrent(slug).orElseThrow();
        assertEquals(1, result.version());
        assertEquals(List.of(), result.payload().get("products"));
        assertEquals(slug, ((Map<?, ?>) result.payload().get("store")).get("slug"));

        store.suspend();
        stores.saveAndFlush(store);
        assertTrue(reader.findCurrent(slug).isEmpty(), "Suspended store must not be public");
    }

    @Test
    void wrongStorePayloadIsRejectedDespiteValidCompositeForeignKey() {
        String slug = "ci-" + UUID.randomUUID().toString().substring(0, 10);
        StoreEntity store = new StoreEntity(slug, "CI Store");
        store.activate();
        stores.saveAndFlush(store);

        var wrongPayload = Map.<String, Object>of(
            "store", Map.of("slug", "other-store", "name", "Other"),
            "products", List.of()
        );
        publications.saveAndFlush(new StorePublicationEntity(
            new PublicationId(store.getId(), 1), wrongPayload
        ));
        store.setPublishedVersion(1);
        stores.saveAndFlush(store);
        assertTrue(reader.findCurrent(slug).isEmpty());
    }

    @Test
    void unpublishedAndUnknownStoresAreNotReturned() {
        assertTrue(reader.findCurrent("no-such-store").isEmpty());
        String slug = "ci-" + UUID.randomUUID().toString().substring(0, 10);
        StoreEntity store = new StoreEntity(slug, "Draft Store");
        stores.saveAndFlush(store);
        assertTrue(reader.findCurrent(slug).isEmpty());
    }
}
