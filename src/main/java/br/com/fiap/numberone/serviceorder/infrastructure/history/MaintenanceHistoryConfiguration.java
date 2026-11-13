package br.com.fiap.numberone.serviceorder.infrastructure.history;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.history.enabled", havingValue = "true")
public class MaintenanceHistoryConfiguration {
    @Bean(destroyMethod = "close")
    DynamoDbClient historyDynamoClient(@Value("${app.history.region}") String region,
                                      @Value("${app.history.endpoint}") String endpoint) {
        var builder = DynamoDbClient.builder().region(Region.of(region))
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(4))
                        .apiCallAttemptTimeout(Duration.ofSeconds(2)));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    DynamoMaintenanceHistory dynamoMaintenanceHistory(DynamoDbClient client, ObjectMapper json,
                                                     @Value("${app.history.table}") String table) {
        if (table.isBlank()) {
            throw new IllegalStateException("HISTORY_DYNAMODB_TABLE_REQUIRED");
        }
        return new DynamoMaintenanceHistory(client, table, json);
    }

    @Bean
    MaintenanceHistoryWorker maintenanceHistoryWorker(MaintenanceHistoryOutbox outbox, DynamoMaintenanceHistory history,
            MeterRegistry metrics, @Value("${app.history.worker-enabled}") boolean workerEnabled) {
        return new MaintenanceHistoryWorker(outbox, history, metrics, workerEnabled);
    }
}
