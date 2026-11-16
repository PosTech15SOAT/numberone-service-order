package br.com.fiap.numberone.serviceorder.infrastructure.history;

import br.com.fiap.numberone.serviceorder.application.gateways.MaintenanceHistory;
import br.com.fiap.numberone.serviceorder.domain.valueobjects.MaintenanceSnapshot;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class MaintenanceHistoryBridge implements MaintenanceHistory {
    private final HttpClient client;
    private final URI endpoint;
    private final Path tokenFile;
    private final ObjectMapper json;

    MaintenanceHistoryBridge(URI baseUrl, Path tokenFile, ObjectMapper json) {
        this(baseUrl, tokenFile, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    }

    MaintenanceHistoryBridge(URI baseUrl, Path tokenFile, ObjectMapper json, HttpClient client) {
        if (!"https".equals(baseUrl.getScheme()) || baseUrl.getHost() == null || baseUrl.getUserInfo() != null
                || baseUrl.getRawQuery() != null || baseUrl.getRawFragment() != null
                || !List.of("", "/").contains(baseUrl.getRawPath())) {
            throw new IllegalArgumentException("HISTORY_BRIDGE_HTTPS_URL_REQUIRED");
        }
        this.endpoint = baseUrl.resolve("/historico");
        this.tokenFile = tokenFile;
        this.json = json;
        this.client = client;
    }

    @Override
    public Result put(Revision revision) {
        var body = Map.of("vehicleId", revision.vehicleId().toString(), "historyKey", revision.key(),
                "branchId", revision.branchId().toString(), "serviceOrderId", revision.orderId().toString(),
                "historyVersion", revision.version(), "snapshot", revision.snapshot());
        var response = send(endpoint, json.writeValueAsString(body));
        Result result = switch (response.statusCode()) {
            case 201 -> Result.WRITTEN;
            case 200 -> Result.REPLAYED;
            case 409 -> Result.COLLISION;
            default -> throw new Unavailable("HISTORY_BRIDGE_WRITE_FAILED_" + response.statusCode());
        };
        if (!result.name().equals(decode(response.body()).path("result").asText())) {
            throw new Unavailable("HISTORY_BRIDGE_INVALID_WRITE_RESULT");
        }
        return result;
    }

    @Override
    public Page query(UUID vehicle, UUID branch, int limit, String after) {
        var query = "?vehicleId=" + vehicle + "&branchId=" + branch + "&limit=" + limit
                + (after == null ? "" : "&after=" + URLEncoder.encode(after, StandardCharsets.UTF_8));
        var response = send(URI.create(endpoint + query), null);
        if (response.statusCode() != 200) {
            throw new Unavailable("HISTORY_BRIDGE_QUERY_FAILED_" + response.statusCode());
        }
        JsonNode root = decode(response.body());
        JsonNode rows = root.path("entries");
        JsonNode cursor = root.path("lastKey");
        if (!rows.isArray() || rows.size() > limit || !(cursor.isNull() || cursor.isTextual())) {
            throw new Unavailable("HISTORY_BRIDGE_INVALID_PAGE");
        }
        var entries = new ArrayList<MaintenanceSnapshot>();
        for (JsonNode row : rows) {
            var entry = decodeSnapshot(row);
            if (!vehicle.equals(entry.vehicleId()) || !branch.equals(entry.branchId())) {
                throw new Unavailable("HISTORY_BRIDGE_INVALID_SCOPE");
            }
            entries.add(entry);
        }
        String lastKey = cursor.isNull() ? null : cursor.asText();
        if (lastKey != null && (entries.isEmpty() || !lastKey.equals(entries.getLast().historyKey()))) {
            throw new Unavailable("HISTORY_BRIDGE_INVALID_CURSOR");
        }
        return new Page(List.copyOf(entries), lastKey);
    }

    private HttpResponse<String> send(URI uri, String body) {
        try {
            String token = Files.readString(tokenFile).strip();
            if (token.isEmpty()) {
                throw new Unavailable("HISTORY_SERVICE_ACCOUNT_TOKEN_EMPTY");
            }
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + token).header("Accept", "application/json");
            if (body == null) {
                request.GET();
            } else {
                request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            }
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new Unavailable("HISTORY_BRIDGE_REQUEST_FAILED", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new Unavailable("HISTORY_BRIDGE_REQUEST_INTERRUPTED", ex);
        }
    }

    private JsonNode decode(String body) {
        try {
            return json.readTree(body);
        } catch (JacksonException ex) {
            throw new Unavailable("HISTORY_BRIDGE_INVALID_RESPONSE", ex);
        }
    }

    private MaintenanceSnapshot decodeSnapshot(JsonNode row) {
        if (!row.isObject()) {
            throw new Unavailable("HISTORY_BRIDGE_INVALID_SNAPSHOT");
        }
        try {
            return json.treeToValue(row, MaintenanceSnapshot.class);
        } catch (JacksonException ex) {
            throw new Unavailable("HISTORY_BRIDGE_INVALID_SNAPSHOT", ex);
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
