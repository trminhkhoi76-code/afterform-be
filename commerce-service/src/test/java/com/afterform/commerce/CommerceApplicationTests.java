package com.afterform.commerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CommerceApplicationTests {
    @Test
    void contextLoads() {
        // Production app context must start without external services during scaffold phase.
    }
}
