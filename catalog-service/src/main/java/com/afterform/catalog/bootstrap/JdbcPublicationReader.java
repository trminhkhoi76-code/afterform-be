package com.afterform.catalog.bootstrap;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPublicationReader implements PublicationReader {
    private static final String SQL = """
        SELECT pub.payload::text AS payload, pub.version
        FROM catalog.stores s
        JOIN catalog.publications pub
          ON pub.store_id = s.id AND pub.version = s.published_version
        WHERE s.slug = ?
          AND s.status = 'active'
          AND pub.payload->'store'->>'slug' = s.slug
        """;

    private final JdbcTemplate jdbc;

    public JdbcPublicationReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PublishedStore> findCurrent(String storeSlug) {
        return jdbc.query(
            SQL,
            (rs, rowNum) -> new PublishedStore(
                rs.getString("payload"),
                rs.getLong("version")
            ),
            storeSlug
        ).stream().findFirst();
    }
}
