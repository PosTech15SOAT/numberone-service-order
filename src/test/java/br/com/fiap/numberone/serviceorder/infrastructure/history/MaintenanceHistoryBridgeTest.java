package br.com.fiap.numberone.serviceorder.infrastructure.history;

import br.com.fiap.numberone.serviceorder.application.gateways.MaintenanceHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaintenanceHistoryBridgeTest {
    @TempDir Path directory;
    private Path token;
    private HttpClient client;
    private MaintenanceHistoryBridge bridge;

    @BeforeEach
    void setUp() throws Exception {
        token = Files.writeString(directory.resolve("token"), "first\n");
        client = mock(HttpClient.class);
        bridge = new MaintenanceHistoryBridge(URI.create("https://bridge.example.test/"), token,
                JsonMapper.builder().build(), client);
    }

    @Test
    void projectsOnceAndReadsTheRotatedCredentialOnReplay() throws Exception {
        var id = UUID.randomUUID();
        var revision = new MaintenanceHistory.Revision(id, "revision-1", id, id, 1, "{}");
        respond("POST", "first", 201, "{\"result\":\"WRITTEN\"}");
        assertThat(bridge.put(revision)).isEqualTo(MaintenanceHistory.Result.WRITTEN);
        Files.writeString(token, "second");
        respond("POST", "second", 200, "{\"result\":\"REPLAYED\"}");
        assertThat(bridge.put(revision)).isEqualTo(MaintenanceHistory.Result.REPLAYED);
    }

    @Test
    void treatsUnexpectedResponsesAsUnavailable() throws Exception {
        respond("GET", "first", 200, "{\"entries\":[],\"lastKey\":\"unexpected\"}");
        assertThatThrownBy(() -> bridge.query(UUID.randomUUID(), UUID.randomUUID(), 20, null))
                .isInstanceOf(MaintenanceHistory.Unavailable.class)
                .hasMessage("HISTORY_BRIDGE_INVALID_CURSOR");
    }

    @SuppressWarnings("unchecked")
    private void respond(String method, String credential, int status, String json) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(json);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            assertThat(request.uri().getPath()).isEqualTo("/historico");
            assertThat(request.method()).isEqualTo(method);
            assertThat(request.headers().firstValue("Authorization")).contains("Bearer " + credential);
            return response;
        });
    }
}
