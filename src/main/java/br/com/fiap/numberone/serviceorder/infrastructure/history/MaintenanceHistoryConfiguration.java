package br.com.fiap.numberone.serviceorder.infrastructure.history;

import br.com.fiap.numberone.serviceorder.application.gateways.MaintenanceHistory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.history.enabled", havingValue = "true")
public class MaintenanceHistoryConfiguration {
    @Bean(destroyMethod = "close")
    MaintenanceHistory maintenanceHistory(ObjectMapper json, Environment environment,
            @Value("${app.history.bridge-url}") String bridgeUrl, @Value("${app.history.token-path}") String tokenPath,
            @Value("${app.history.region}") String region, @Value("${app.history.endpoint}") String endpoint,
            @Value("${app.history.table}") String table) {
        if (!bridgeUrl.isBlank()) {
            return new MaintenanceHistoryBridge(URI.create(bridgeUrl), Path.of(tokenPath), json);
        }
        if (table.isBlank()) {
            throw new IllegalStateException("HISTORY_DYNAMODB_TABLE_REQUIRED");
        }
        return new DynamoMaintenanceHistory(historyDynamoClient(region, endpoint, environment), table, json);
    }

    DynamoDbClient historyDynamoClient(String region, String endpoint, Environment environment) {
        var builder = DynamoDbClient.builder().region(Region.of(region))
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(4))
                        .apiCallAttemptTimeout(Duration.ofSeconds(2)));
        if (environment.matchesProfiles("local", "kind")) {
            URI uri = URI.create(endpoint);
            if (!"http".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || uri.getHost() == null || !java.util.Set.of("localhost", "127.0.0.1", "localstack", "localstack-dynamodb").contains(uri.getHost())) {
                throw new IllegalStateException("LOCAL_HISTORY_REQUIRES_LOCALSTACK_ENDPOINT");
            }
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
            builder.endpointOverride(uri);
        } else if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    MaintenanceHistoryWorker maintenanceHistoryWorker(MaintenanceHistoryOutbox outbox, MaintenanceHistory history,
            MeterRegistry metrics, @Value("${app.history.worker-enabled}") boolean workerEnabled) {
        return new MaintenanceHistoryWorker(outbox, history, metrics, workerEnabled);
    }
}
