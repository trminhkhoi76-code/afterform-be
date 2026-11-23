package com.afterform.catalog.bootstrap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApiIndexControllerTests {
    @Test
    void rootReturnsApiIndexInsteadOfUnmatched404() throws Exception {
        MockMvcBuilders.standaloneSetup(new ApiIndexController()).build()
            .perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.service").value("afterform-catalog"))
            .andExpect(jsonPath("$.type").value("api"));
    }
}
