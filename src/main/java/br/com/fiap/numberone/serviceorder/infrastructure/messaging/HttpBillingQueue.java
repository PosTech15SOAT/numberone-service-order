package br.com.fiap.numberone.serviceorder.infrastructure.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

final class HttpBillingQueue implements BillingQueue {
    private static final Duration SHORT_REQUEST = Duration.ofSeconds(5);
    private static final Duration RECEIVE_REQUEST = Duration.ofSeconds(32);
    private final HttpClient http;
    private final URI baseUrl;
    private final Path tokenFile;
    private final ObjectMapper json = new ObjectMapper();

    HttpBillingQueue(URI baseUrl, Path tokenFile) {
        if (!"https".equals(baseUrl.getScheme()) || baseUrl.getHost() == null || baseUrl.getUserInfo() != null
                || baseUrl.getRawQuery() != null || baseUrl.getRawFragment() != null
                || !List.of("", "/").contains(baseUrl.getPath())) {
            throw new IllegalArgumentException("BILLING_QUEUE_BRIDGE_HTTPS_URL_REQUIRED");
        }
        this.baseUrl = baseUrl;
        this.tokenFile = tokenFile;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @Override
    public Map<String, String> attributes() {
        return decode(request("/fila", null, SHORT_REQUEST, 200), new TypeReference<>() {});
    }

    @Override
    public List<Message> receive() {
        List<Delivery> deliveries = decode(request("/recebimentos", "", RECEIVE_REQUEST, 200),
                new TypeReference<>() {});
        return deliveries.stream().map(delivery -> Message.builder().messageId(delivery.messageId())
                .receiptHandle(delivery.receiptHandle()).body(delivery.body())
                .attributes(Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, delivery.receiveCount()))
                .build()).toList();
    }

    @Override
    public void acknowledge(String receiptHandle) {
        sendReceipt("/confirmacoes", receiptHandle);
    }

    @Override
    public void renewVisibility(String receiptHandle) {
        sendReceipt("/visibilidade", receiptHandle);
    }

    private void sendReceipt(String path, String receiptHandle) {
        try {
            request(path, json.writeValueAsString(Map.of("receiptHandle", receiptHandle)), SHORT_REQUEST, 204);
        } catch (IOException ex) {
            throw new IllegalStateException("BILLING_QUEUE_BRIDGE_INVALID_RECEIPT", ex);
        }
    }

    private String request(String path, String body, Duration timeout, int expectedStatus) {
        try {
            var builder = HttpRequest.newBuilder(baseUrl.resolve(path)).timeout(timeout)
                    .header("Authorization", "Bearer " + Files.readString(tokenFile).strip());
            if (body != null) {
                builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            }
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != expectedStatus) {
                throw new IllegalStateException("BILLING_QUEUE_BRIDGE_HTTP_" + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("BILLING_QUEUE_BRIDGE_INTERRUPTED", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("BILLING_QUEUE_BRIDGE_UNAVAILABLE", ex);
        }
    }

    private <T> T decode(String body, TypeReference<T> type) {
        try {
            return json.readValue(body, type);
        } catch (IOException ex) {
            throw new IllegalStateException("BILLING_QUEUE_BRIDGE_INVALID_RESPONSE", ex);
        }
    }

    @Override
    public void close() {
        http.close();
    }

    private record Delivery(String messageId, String receiptHandle, String body, String receiveCount) {}
}
