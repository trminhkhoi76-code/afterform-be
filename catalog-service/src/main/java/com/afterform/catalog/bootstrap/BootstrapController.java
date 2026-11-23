package com.afterform.catalog.bootstrap;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Serves only immutable published public snapshots. No demo seed, customer PII or credentials.
 * Frontend owns fallback logic when this API is unavailable.
 */
@RestController
@RequestMapping("/api/v1/public/stores")
public class BootstrapController {
    private static final Pattern STORE_SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{0,62}$");
    private final PublicationReader publications;

    public BootstrapController(PublicationReader publications) {
        this.publications = publications;
    }

    @GetMapping(value = "/{storeSlug}/bootstrap", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> bootstrap(@PathVariable String storeSlug) {
        if (!STORE_SLUG.matcher(storeSlug).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid store slug");
        }

        PublishedStore current = publications.findCurrent(storeSlug)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not published"));

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
            .eTag("store-" + storeSlug + "-v" + current.version())
            .body(current.payload());
    }
}
