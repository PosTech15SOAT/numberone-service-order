package br.com.fiap.numberone.serviceorder.infrastructure.integration;

import br.com.fiap.numberone.serviceorder.application.gateways.SagaDependencyException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.Set;
import br.com.fiap.numberone.serviceorder.application.gateways.InventorySagaGateway;
import java.util.HashSet;

@Component
public class HttpInventorySagaGateway implements InventorySagaGateway {
    private final ObjectMapper json;
    private final ParticipantHttpClient http;

    public HttpInventorySagaGateway(ObjectMapper json, @Qualifier("inventorySagaHttp") ParticipantHttpClient http) {
        this.json = json;
        this.http = http;
    }

    @Override
    public ConsumptionResult consume(Consumption request, UUID correlation) {
        var reply = http.post("/internal/inventory/consumptions", request, request.operationId(), correlation);
        try {
            var r = json.readValue(reply.body(), ConsumptionResult.class);
            if (!request.operationId().equals(r.operationId()) || r.movements() == null) { throw new SagaDependencyException(); }
            if (reply.statusCode() == 200 && "CONSUMED".equals(r.result()) && !r.movements().isEmpty()
                    && r.movements().stream().allMatch(id -> id != null && !id.equals(new UUID(0, 0)))
                    && new HashSet<>(r.movements()).size() == r.movements().size()) { return r; }
            if (reply.statusCode() == 409 && "REJECTED".equals(r.result())
                    && "INSUFFICIENT_STOCK".equals(r.code()) && r.movements().isEmpty()) { return r; }
            // Closed/conflicting/unknown/malformed results never authorize physical use.
            throw new SagaDependencyException();
        } catch (RuntimeException ex) { throw new SagaDependencyException(); }
    }
    @Override
    public InventoryOperation inventoryOperation(UUID id, UUID correlation) {
        var reply = http.get("/internal/inventory/operations/" + id, correlation);
        return inventoryReply(reply, id, null, false);
    }
    @Override
    public InventoryOperation reverse(UUID id, Reversal request, UUID correlation) {
        var reply = http.post("/internal/inventory/consumptions/" + id + "/reversal",
                request, request.compensationId(), correlation);
        http.checkConflict(reply, Set.of("IDEMPOTENCY_CONFLICT", "MATERIAL_USE_NOT_RESOLVED", "EVIDENCE_REQUIRED", "OPERATION_ALREADY_REJECTED"));
        return inventoryReply(reply, id, request.compensationId(), true);
    }
    private InventoryOperation inventoryReply(HttpResponse<String> reply, UUID id, UUID compensation, boolean reversal) {
        try {
            var r = json.readValue(reply.body(), InventoryOperation.class);
            if (!id.equals(r.operationId()) || r.result() == null) { throw new SagaDependencyException(); }
            if (!reversal && ((reply.statusCode() == 404 && r.result().equals("UNKNOWN"))
                    || (reply.statusCode() == 202 && r.result().equals("PENDING")))) { return r; }
            if (reply.statusCode() != 200 || !Set.of("CONSUMED", "REJECTED", "CLOSED", "REVERSED").contains(r.result())
                    || r.movements() == null || r.movements().stream().anyMatch(m -> m == null || m.equals(new UUID(0,0)))
                    || new HashSet<>(r.movements()).size() != r.movements().size()) { throw new SagaDependencyException(); }
            boolean closed = Set.of("CLOSED", "REVERSED").contains(r.result());
            if (reversal && (!closed || !compensation.equals(r.compensationId()))) { throw new SagaDependencyException(); }
            if (closed && (r.compensationId() == null || r.compensationId().equals(new UUID(0,0)))) { throw new SagaDependencyException(); }
            if (Set.of("CONSUMED", "REVERSED").contains(r.result()) == r.movements().isEmpty()
                    || (r.result().equals("REJECTED") && !"INSUFFICIENT_STOCK".equals(r.code()))) { throw new SagaDependencyException(); }
            return r;
        } catch (RuntimeException ex) { throw new SagaDependencyException(); }
    }
}
