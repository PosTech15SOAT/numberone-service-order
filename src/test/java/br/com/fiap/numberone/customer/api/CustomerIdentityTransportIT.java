package br.com.fiap.numberone.customer.api;

import br.com.fiap.numberone.support.HttpPostgresSupport;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerIdentityTransportIT extends HttpPostgresSupport {

    private static final String CPF = "52998224725";

    @Test
    void verifiesOnlyAnActiveCustomerWithTheMatchingCpfWithoutAuthorization() throws Exception {
        UUID customerId = createCustomer();
        String correlationId = UUID.randomUUID().toString();
        var accepted = identity(Map.of("customerId", customerId, "cpf", CPF), correlationId);
        assertThat(accepted.statusCode()).isEqualTo(204);
        assertThat(accepted.headers().firstValue("X-Correlation-Id")).contains(correlationId);

        assertThat(identity(Map.of("customerId", customerId, "cpf", "11144477735"),
                UUID.randomUUID().toString()).statusCode()).isEqualTo(404);
        assertThat(identity(Map.of("customerId", UUID.randomUUID(), "cpf", CPF),
                UUID.randomUUID().toString()).statusCode()).isEqualTo(404);

        jdbc.update("UPDATE cliente SET tipo_documento = 'PESSOA_JURIDICA' WHERE id = ?", customerId);
        assertThat(identity(Map.of("customerId", customerId, "cpf", CPF),
                UUID.randomUUID().toString()).statusCode()).isEqualTo(404);
        jdbc.update("UPDATE cliente SET tipo_documento = 'PESSOA_FISICA' WHERE id = ?", customerId);
        jdbc.update("UPDATE cliente SET ativo = false WHERE id = ?", customerId);
        assertThat(identity(Map.of("customerId", customerId, "cpf", CPF),
                UUID.randomUUID().toString()).statusCode()).isEqualTo(404);
    }

    @Test
    void rejectsInvalidPayloads() throws Exception {
        String correlationId = UUID.randomUUID().toString();
        assertThat(identity(Map.of("customerId", "invalid-uuid", "cpf", CPF), correlationId).statusCode()).isEqualTo(400);
        assertThat(identity(Map.of("cpf", CPF), correlationId).statusCode()).isEqualTo(400);
        assertThat(identity(Map.of("customerId", UUID.randomUUID(), "cpf", "123"), correlationId).statusCode()).isEqualTo(400);
    }

    @Test
    void internalEndpointRequiresAValidCorrelationId() throws Exception {
        var response = identity(Map.of("customerId", UUID.randomUUID(), "cpf", CPF), null);
        assertThat(response.statusCode()).isEqualTo(400);
    }

    private HttpResponse<String> identity(Map<String, ?> payload, String correlationId) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/internal/auth/customer-identity"))
                .header("Content-Type", "application/json");
        if (correlationId != null) {
            request.header("X-Correlation-Id", correlationId);
        }
        request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
