package com.afterform.catalog.persistence;

import java.util.Map;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Read-only published snapshot, already sanitized for the public bootstrap API. */
@Entity
@Immutable
@Table(name = "publications", schema = "catalog")
public class StorePublicationEntity {
    @EmbeddedId
    private PublicationId id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;

    protected StorePublicationEntity() {
        // Required by JPA.
    }

    public StorePublicationEntity(PublicationId id, Map<String, Object> payload) {
        this.id = id;
        this.payload = payload;
    }

    public PublicationId getId() {
        return id;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}
