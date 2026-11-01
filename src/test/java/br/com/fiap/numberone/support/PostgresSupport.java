package br.com.fiap.numberone.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.branch.id=40000000-0000-4000-8000-000000000001",
        "app.security.identity.provider=gateway", "app.security.identity.gateway-trusted=true",
        "app.material-reference.provider=fixture", "management.statsd.metrics.export.enabled=false"
})
public abstract class PostgresSupport {
    // One ephemeral, real PostgreSQL per test JVM. Ryuk owns cleanup, no reuse and no production URL.
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("service_order_test")
            .withCommand("postgres", "-c", "log_min_error_statement=panic", "-c", "log_error_verbosity=terse");
    static { POSTGRES.start(); }
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
