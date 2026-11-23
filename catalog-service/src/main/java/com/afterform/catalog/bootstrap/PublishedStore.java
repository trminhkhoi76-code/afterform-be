package com.afterform.catalog.bootstrap;

import java.util.Map;

/** Public payload is already a structured JSON object, serialized by Spring MVC. */
public record PublishedStore(Map<String, Object> payload, long version) {}
