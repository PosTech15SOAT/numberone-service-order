package br.com.fiap.numberone.serviceorder.infrastructure.integration;

import br.com.fiap.numberone.serviceorder.application.gateways.MaterialReferenceUnavailableException;
import br.com.fiap.numberone.serviceorder.application.gateways.SagaDependencyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpMaterialReferenceGatewayTest {
    private final UUID material = UUID.randomUUID();
    private final String path = "/internal/inventory/items/" + material;
    private final ParticipantHttpClient participants = mock(ParticipantHttpClient.class);
    private final HttpMaterialReferenceGateway gateway = new HttpMaterialReferenceGateway(participants, new ObjectMapper());

    @ParameterizedTest
    @ValueSource(strings = {"20.40", "\"20.409\"", "\"1e2\"", "\"0.00\"", "\"-1.00\"", "\"100000000.00\""})
    void invalidPriceBecomesDependencyFailure(String invalidPrice) {
        var body = """
                {"id":"%s","description":"Peca","referenceUnitPrice":%s,"active":true}
                """.formatted(material, invalidPrice);
        reply(200, body);
        assertThatThrownBy(() -> gateway.find(material)).isInstanceOf(MaterialReferenceUnavailableException.class);
    }

    @Test
    void wrongIdOrWrongActiveTypeBecomesDependencyFailure() {
        reply(200, """
                {"id":"%s","description":"Peca","referenceUnitPrice":"20.40","active":true}
                """.formatted(UUID.randomUUID()));
        assertThatThrownBy(() -> gateway.find(material)).isInstanceOf(MaterialReferenceUnavailableException.class);
        reply(200, """
                {"id":"%s","description":"Peca","referenceUnitPrice":"20.40","active":"true"}
                """.formatted(material));
        assertThatThrownBy(() -> gateway.find(material)).isInstanceOf(MaterialReferenceUnavailableException.class);
    }

    @Test
    void dependencyFailureCoversHttpErrorAndUnreachableService() {
        reply(503, "{}");
        assertThatThrownBy(() -> gateway.find(material)).isInstanceOf(MaterialReferenceUnavailableException.class);
        when(participants.get(eq(path), any(UUID.class))).thenThrow(new SagaDependencyException());
        assertThatThrownBy(() -> gateway.find(material)).isInstanceOf(MaterialReferenceUnavailableException.class);
    }

    @SuppressWarnings("unchecked")
    private void reply(int status, String body) {
        HttpResponse<String> reply = mock(HttpResponse.class);
        when(reply.statusCode()).thenReturn(status);
        when(reply.body()).thenReturn(body);
        when(participants.get(eq(path), any(UUID.class))).thenReturn(reply);
    }
}
