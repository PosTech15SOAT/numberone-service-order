package br.com.fiap.numberone.customer.api;

import br.com.fiap.numberone.support.HttpPostgresSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "OS_IDENTITY_TOKEN=synthetic-identity-token-for-transport-tests")
class CustomerIdentityTransportIT extends HttpPostgresSupport {

    private static final String TOKEN = "synthetic-identity-token-for-transport-tests";
    private static final String CPF = "52998224725";

    @Test
    void verifiesOnlyAnActiveCustomerWithTheMatchingCpfAndServiceToken() throws Exception {
        UUID customerId = createCustomer();

        assertThat(identity(customerId, CPF, null, UUID.randomUUID().toString()).statusCode()).isEqualTo(401);
        assertThat(identity(customerId, CPF, "Bearer invalid", UUID.randomUUID().toString()).statusCode()).isEqualTo(401);
        var accepted = identity(customerId, CPF, "Bearer " + TOKEN, UUID.randomUUID().toString());
        assertThat(accepted.statusCode()).isEqualTo(204);
        assertThat(accepted.headers().firstValue("X-Correlation-Id")).isPresent();

        assertThat(identity(customerId, "11144477735", "Bearer " + TOKEN,
                UUID.randomUUID().toString()).statusCode()).isEqualTo(404);
        assertThat(identity(UUID.randomUUID(), CPF, "Bearer " + TOKEN,
                UUID.randomUUID().toString()).statusCode()).isEqualTo(404);

        jdbc.update("UPDATE cliente SET ativo = false WHERE id = ?", customerId);
        assertThat(identity(customerId, CPF, "Bearer " + TOKEN,
                UUID.randomUUID().toString()).statusCode()).isEqualTo(404);
    }

    @Test
    void internalEndpointRequiresAValidCorrelationId() throws Exception {
        var response = identity(UUID.randomUUID(), CPF, "Bearer " + TOKEN, null);
        assertThat(response.statusCode()).isEqualTo(400);
    }

    private HttpResponse<String> identity(UUID customerId, String cpf, String authorization,
                                          String correlationId) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/internal/auth/customer-identity"))
                .header("Content-Type", "application/json");
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        if (correlationId != null) {
            request.header("X-Correlation-Id", correlationId);
        }
        request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                Map.of("customerId", customerId, "cpf", cpf))));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
