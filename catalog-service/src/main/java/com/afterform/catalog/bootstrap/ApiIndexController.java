package com.afterform.catalog.bootstrap;

import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public API landing response; the storefront UI is hosted separately from this backend. */
@RestController
public class ApiIndexController {

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> index() {
        return Map.of(
            "service", "afterform-catalog",
            "type", "api",
            "endpoints", List.of(
                "/actuator/health",
                "/api/v1/public/stores/{storeSlug}/bootstrap"
            )
        );
    }
}
