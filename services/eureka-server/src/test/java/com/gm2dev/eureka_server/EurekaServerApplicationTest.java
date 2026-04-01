package com.gm2dev.eureka_server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EurekaServerApplicationTest {

    @Test
    void contextLoads() {
        // Verifies that the Eureka server Spring context starts without errors.
    }
}
