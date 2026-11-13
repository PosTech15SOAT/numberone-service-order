package br.com.fiap.numberone.shared.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class HealthTransportIT extends br.com.fiap.numberone.support.PostgresSupport {
    @Value("${local.server.port}")
    private int port;
    private final HttpClient client = HttpClient.newHttpClient();

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void probesWorkOverRealHttpWithoutIdentityOrSensitiveDetails(String path) throws Exception {
        var response = client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"").doesNotContain("components", "details");
    }

    @Test
    void healthPreservesCorrelationOverRealHttp() throws Exception {
        var response = client.send(request("/api/public/health").header("X-Correlation-Id", "demo-phase1")
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("numberone-service-order", "\"status\":\"UP\"");
        assertThat(response.headers().firstValue("X-Correlation-Id")).contains("demo-phase1");
    }

    @Test
    void missingCorrelationIsSanitizedBadRequest() throws Exception {
        var response = client.send(request("/api/public/health").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("content-type").orElseThrow())
                .startsWith("application/problem+json");
        assertThat(response.body()).doesNotContain("exception", "stackTrace");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/ordens-servico", "/login", "/actuator/env"})
    void forgedIdentityDoesNotUnlockUnimplementedEndpoints(String path) throws Exception {
        var response = client.send(request(path).header("X-Correlation-Id", "demo-denied")
                .header("X-Authenticated-Subject", "fixture-subject")
                .header("X-Authenticated-Roles", "ADMIN").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("Authentication required").doesNotContain("fixture-subject");
    }

    @Test
    void stateChangingRequestsRemainDenied() throws Exception {
        var response = client.send(request("/api/admin/ordens-servico").header("X-Correlation-Id", "demo-post")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
    }
}
