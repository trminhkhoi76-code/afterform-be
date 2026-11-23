package com.afterform.catalog.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class PublicationId implements Serializable {
    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "version", nullable = false)
    private Long version;

    protected PublicationId() {
        // Required by JPA.
    }

    public PublicationId(UUID storeId, long version) {
        this.storeId = Objects.requireNonNull(storeId);
        this.version = version;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PublicationId id)) {
            return false;
        }
        return Objects.equals(storeId, id.storeId) && Objects.equals(version, id.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storeId, version);
    }
}
