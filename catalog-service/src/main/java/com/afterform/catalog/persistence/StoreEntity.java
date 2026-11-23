package com.afterform.catalog.persistence;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Minimal Catalog-owned store aggregate. Flyway is the schema authority;
 * other columns in catalog.stores keep their database defaults until admin APIs are added.
 */
@Entity
@Table(name = "stores", schema = "catalog")
public class StoreEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "status", nullable = false)
    private String status = "draft";

    @Column(name = "published_version")
    private Long publishedVersion;

    protected StoreEntity() {
        // Required by JPA.
    }

    public StoreEntity(String slug, String name) {
        this.slug = slug;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public Long getPublishedVersion() {
        return publishedVersion;
    }

    public void activate() {
        this.status = "active";
    }

    public void suspend() {
        this.status = "suspended";
    }

    public void setPublishedVersion(long publishedVersion) {
        this.publishedVersion = publishedVersion;
    }
}
