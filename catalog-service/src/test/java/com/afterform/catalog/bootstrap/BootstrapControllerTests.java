package com.afterform.catalog.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BootstrapControllerTests {
    private final String json = """
        {"store":{"slug":"beauty","name":"Beauty"},"products":[],"home":{},"looks":[]}
        """;

    @Test
    void validPublishedEmptyCatalogReturnsJsonAndEtag() throws Exception {
        PublicationReader reader = slug -> "beauty".equals(slug)
            ? Optional.of(new PublishedStore(json, 7))
            : Optional.empty();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BootstrapController(reader)).build();
        mvc.perform(get("/api/v1/public/stores/beauty/bootstrap"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(header().string("ETag", "\"store-beauty-v7\""))
            .andExpect(jsonPath("$.products").isEmpty())
            .andExpect(jsonPath("$.store.slug").value("beauty"));
    }

    @Test
    void unknownOrUnpublishedReturns404() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new BootstrapController(slug -> Optional.empty())
        ).build();
        mvc.perform(get("/api/v1/public/stores/missing/bootstrap"))
            .andExpect(status().isNotFound());
    }

    @Test
    void invalidSlugReturns400WithoutQueryingRepository() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new BootstrapController(slug -> { throw new AssertionError("should not query"); })
        ).build();
        mvc.perform(get("/api/v1/public/stores/INVALID/bootstrap"))
            .andExpect(status().isBadRequest());
    }
}
