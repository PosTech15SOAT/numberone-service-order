package br.com.fiap.numberone.serviceorder.infrastructure.integration;

import br.com.fiap.numberone.serviceorder.application.gateways.MaterialReferenceGateway;
import br.com.fiap.numberone.serviceorder.application.gateways.MaterialReferenceUnavailableException;
import br.com.fiap.numberone.serviceorder.application.gateways.SagaDependencyException;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderNotFoundException;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderRuleException;
import br.com.fiap.numberone.shared.infrastructure.correlation.SagaCorrelation;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Pattern;

final class HttpMaterialReferenceGateway implements MaterialReferenceGateway {
    private static final Pattern PRICE = Pattern.compile("[0-9]{1,8}(?:\\.[0-9]{1,2})?");
    private final HttpSagaParticipants participants;
    private final ObjectMapper json;

    HttpMaterialReferenceGateway(HttpSagaParticipants participants, ObjectMapper json) {
        this.participants = participants;
        this.json = json;
    }

    @Override
    public MaterialReference find(UUID id) {
        HttpResponse<String> reply;
        try {
            reply = participants.inventoryGet("/internal/inventory/items/" + id, SagaCorrelation.current());
        } catch (SagaDependencyException ex) {
            throw new MaterialReferenceUnavailableException();
        }

        if (reply.statusCode() == 404) {
            throw new OrderNotFoundException();
        }
        if (reply.statusCode() != 200) {
            throw new MaterialReferenceUnavailableException();
        }

        MaterialReference reference;
        try {
            var item = json.readTree(reply.body());
            var returnedId = item.path("id");
            var description = item.path("description");
            var price = item.path("referenceUnitPrice");
            var active = item.path("active");
            if (!returnedId.isString() || !id.toString().equalsIgnoreCase(returnedId.asString())
                    || !description.isString() || description.asString().isBlank() || description.asString().length() > 255
                    || !price.isString() || !PRICE.matcher(price.asString()).matches() || !active.isBoolean()) {
                throw new IllegalArgumentException("Invalid inventory reference");
            }
            var unitPrice = new BigDecimal(price.asString());
            if (unitPrice.signum() <= 0) {
                throw new IllegalArgumentException("Invalid inventory price");
            }
            reference = new MaterialReference(id, description.asString(), unitPrice, active.asBoolean());
        } catch (RuntimeException ex) {
            throw new MaterialReferenceUnavailableException();
        }
        if (!reference.active()) {
            throw new OrderRuleException("MATERIAL_NOT_ACTIVE");
        }
        return reference;
    }
}
