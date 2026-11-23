package com.afterform.catalog.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JdbcPublicationReaderTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired PublicationReader reader;

    @Test
    void returnsOnlyCurrentlyPublishedActiveTenantData() {
        String slug = "ci-" + UUID.randomUUID().toString().substring(0, 10);
        UUID storeId = jdbc.queryForObject(
            "INSERT INTO catalog.stores(slug, name, status) VALUES (?, ?, 'active') RETURNING id",
            UUID.class, slug, "CI Store");
        assertNotNull(storeId);
        assertTrue(reader.findCurrent(slug).isEmpty(), "No draft should be public");

        String json = "{\"store\":{\"slug\":\"" + slug + "\",\"name\":\"CI Store\"},\"products\":[],\"home\":{},\"looks\":[]}";
        jdbc.update("INSERT INTO catalog.publications(store_id, version, payload) VALUES (?, 1, ?::jsonb)", storeId, json);
        jdbc.update("UPDATE catalog.stores SET published_version = 1 WHERE id = ?", storeId);
        PublishedStore published = reader.findCurrent(slug).orElseThrow();
        assertEquals(1, published.version());
        assertTrue(published.payload().contains("\"products\": []") ||
                   published.payload().contains("\"products\":[]"));

        jdbc.update("UPDATE catalog.stores SET status = 'suspended' WHERE id = ?", storeId);
        assertTrue(reader.findCurrent(slug).isEmpty(), "Suspended store must not be public");
    }

    @Test
    void rejectsCrossStorePublishedPayload() {
        String slug = "ci-" + UUID.randomUUID().toString().substring(0, 10);
        UUID id = jdbc.queryForObject(
            "INSERT INTO catalog.stores(slug, name, status) VALUES (?, ?, 'active') RETURNING id",
            UUID.class, slug, "CI Store");
        String json = "{\"store\":{\"slug\":\"different\",\"name\":\"Wrong\"},\"products\":[]}";
        jdbc.update("INSERT INTO catalog.publications(store_id, version, payload) VALUES (?, 1, ?::jsonb)", id, json);
        jdbc.update("UPDATE catalog.stores SET published_version = 1 WHERE id = ?", id);
        assertTrue(reader.findCurrent(slug).isEmpty());
    }
}
