package com.afterform.catalog.bootstrap;

import java.util.Optional;

public interface PublicationReader {
    Optional<PublishedStore> findCurrent(String storeSlug);
}
