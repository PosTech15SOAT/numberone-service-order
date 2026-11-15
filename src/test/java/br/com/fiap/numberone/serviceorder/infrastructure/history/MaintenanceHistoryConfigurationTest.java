package br.com.fiap.numberone.serviceorder.infrastructure.history;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.*;

class MaintenanceHistoryConfigurationTest {
    private final MaintenanceHistoryConfiguration configuration = new MaintenanceHistoryConfiguration();

    @Test void localProfilesRejectMissingOrExternalEndpoint() {
        for (String profile : new String[]{"local", "kind"}) {
            var environment = new MockEnvironment(); environment.setActiveProfiles(profile);
            for (String endpoint : new String[]{"", "https://dynamodb.us-east-1.amazonaws.com", "http://other:4566", "http://test@localstack:4566"}) {
                assertThatThrownBy(() -> configuration.historyDynamoClient("us-east-1", endpoint, environment))
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @Test void localClientUsesExplicitEndpointWithoutCredentialDiscovery() {
        var environment = new MockEnvironment(); environment.setActiveProfiles("kind");
        try (var client = configuration.historyDynamoClient("us-east-1", "http://localstack-dynamodb:4566", environment)) {
            assertThat(client.serviceClientConfiguration().endpointOverride()).hasValue(java.net.URI.create("http://localstack-dynamodb:4566"));
        }
    }
}
