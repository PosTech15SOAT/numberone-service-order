package br.com.fiap.numberone.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.saga.recovery-enabled=false",
        "app.branch.id=40000000-0000-4000-8000-000000000001",
        "app.security.identity.provider=gateway", "app.security.identity.gateway-trusted=true",
        "app.material-reference.provider=fixture", "management.statsd.metrics.export.enabled=false"
})
public abstract class PostgresSupport {
    private static final String LOCAL_PORT = System.getenv("PHASE2_TEST_POSTGRES_PORT");
    private static final PostgreSQLContainer<?> POSTGRES = LOCAL_PORT == null ? startContainer() : null;
    private static final String APP_SCHEMA = LOCAL_PORT == null ? "public" : isolatedSchema();

    private static PostgreSQLContainer<?> startContainer() {
        var postgres = new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("service_order_test")
                .withCommand("postgres", "-c", "log_min_error_statement=panic", "-c", "log_error_verbosity=terse");
        postgres.start();
        return postgres;
    }

    protected static String isolatedSchema() {
        return "phase2_" + UUID.randomUUID().toString().replace("-", "");
    }

    protected static DriverManagerDataSource postgresDataSource(String schema) {
        var url = POSTGRES == null
                ? "jdbc:postgresql://127.0.0.1:" + Integer.parseInt(LOCAL_PORT) + "/postgres"
                : POSTGRES.getJdbcUrl();
        return new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                POSTGRES == null ? "phase2_test" : POSTGRES.getUsername(), POSTGRES == null ? "" : POSTGRES.getPassword());
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        var dataSource = postgresDataSource(APP_SCHEMA);
        registry.add("spring.datasource.url", dataSource::getUrl);
        registry.add("spring.datasource.username", dataSource::getUsername);
        registry.add("spring.datasource.password", dataSource::getPassword);
        registry.add("spring.flyway.default-schema", () -> APP_SCHEMA);
    }
}
