package com.example.callback;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test — verifies the Spring context starts without errors.
 * For full integration tests add Testcontainers with PostgreSQL and Kafka.
 */
@SpringBootTest
@ActiveProfiles("test")
class CallbackSystemApplicationTests {

    @Test
    void contextLoads() {
        // If the application context fails to start the test will fail automatically
    }
}
