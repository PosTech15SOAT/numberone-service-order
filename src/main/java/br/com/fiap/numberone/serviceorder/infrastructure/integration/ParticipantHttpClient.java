package br.com.fiap.numberone.serviceorder.infrastructure.integration;

import br.com.fiap.numberone.serviceorder.application.gateways.SagaConflictException;
import br.com.fiap.numberone.serviceorder.application.gateways.SagaDependencyException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

public class ParticipantHttpClient {
    private final ObjectMapper json;
    private final String base;
    private final String token;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public ParticipantHttpClient(ObjectMapper json, String base, String token) {
        this.json = json;
        this.base = base;
        this.token = token;
    }

    HttpResponse<String> get(String path, UUID correlation) {
        return exchange(path, null, null, correlation);
    }

    HttpResponse<String> post(String path, Object body, UUID key, UUID correlation) {
        return exchange(path, body, key, correlation);
    }

    private HttpResponse<String> exchange(String path, Object body, UUID key, UUID correlation) {
        if (base.isBlank() || token.isBlank()) { throw new SagaDependencyException(); }
        try {
            var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + token).header("X-Correlation-Id", correlation.toString())
                    .header("Accept", "application/json");
            if (body == null) { request.GET(); }
            else { request.header("Content-Type", "application/json").header("Idempotency-Key", key.toString())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))); }
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new SagaDependencyException(); }
        catch (Exception ex) { throw new SagaDependencyException(); }
    }
    void checkConflict(HttpResponse<String> reply, Set<String> codes) {
        if (reply.statusCode() != 409) { return; }
        String code;
        try { code = json.readTree(reply.body()).path("code").asString(); }
        catch (RuntimeException ex) { throw new SagaDependencyException(); }
        if (!codes.contains(code)) { throw new SagaDependencyException(); }
        throw new SagaConflictException(code);
    }
}
