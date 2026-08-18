package com.tradewise;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests: boots the full application against a real PostgreSQL
 * container (never H2). The container is started once and shared across IT classes.
 *
 * <p>Managed manually rather than via the Testcontainers JUnit extension so the tests do
 * not depend on extension/JUnit version coupling.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("tradewise")
                    .withUsername("tradewise")
                    .withPassword("tradewise");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("tradewise.security.jwt.secret",
                () -> "integration-test-secret-0123456789abcdef0123456789abcdef");
    }
}
