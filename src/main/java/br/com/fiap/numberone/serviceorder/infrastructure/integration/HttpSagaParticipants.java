package br.com.fiap.numberone.serviceorder.infrastructure.integration;

import br.com.fiap.numberone.serviceorder.application.gateways.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class HttpSagaParticipants implements SagaParticipants {
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String billing, inventory, billingToken, inventoryToken;
    public HttpSagaParticipants(ObjectMapper json, @Value("${app.saga.billing-url:}") String billing,
            @Value("${app.saga.inventory-url:}") String inventory,
            @Value("${app.saga.billing-token:}") String billingToken,
            @Value("${app.saga.inventory-token:}") String inventoryToken) {
        this.json = json; this.billing = billing; this.inventory = inventory;
        this.billingToken = billingToken; this.inventoryToken = inventoryToken;
    }
    private HttpResponse<String> exchange(String base, String token, String path, Object body, UUID key, UUID correlation) {
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
    public ConsumptionResult consume(Consumption request, UUID correlation) {
        var reply = exchange(inventory, inventoryToken, "/internal/inventory/consumptions", request, request.operationId(), correlation);
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
    public CurrentBudget currentBudget(UUID id, UUID correlation) {
        var reply = exchange(billing, billingToken, "/internal/budgets/service-orders/" + id + "/current", null, null, correlation);
        try {
            if (reply.statusCode() != 200) { throw new SagaDependencyException(); }
            var r = json.readValue(reply.body(), CurrentBudget.class);
            if (!id.equals(r.serviceOrderId()) || r.budgetId() == null || r.budgetVersion() < 1 || r.compositionVersion() < 1
                    || !Set.of("DRAFT", "SENT", "APPROVED", "REJECTED", "CANCELLED").contains(r.status())) { throw new SagaDependencyException(); }
            return r;
        } catch (RuntimeException ex) { throw new SagaDependencyException(); }
    }
    public InventoryOperation inventoryOperation(UUID id, UUID correlation) {
        var reply = exchange(inventory, inventoryToken, "/internal/inventory/operations/" + id, null, null, correlation);
        return inventoryReply(reply, id, null, false);
    }
    public InventoryOperation reverse(UUID id, Reversal request, UUID correlation) {
        var reply = exchange(inventory, inventoryToken, "/internal/inventory/consumptions/" + id + "/reversal",
                request, request.compensationId(), correlation);
        conflict(reply, Set.of("IDEMPOTENCY_CONFLICT", "MATERIAL_USE_NOT_RESOLVED", "EVIDENCE_REQUIRED", "OPERATION_ALREADY_REJECTED"));
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
    public CancellationResult cancelBudget(UUID budget, Cancellation request, UUID correlation) {
        var reply = exchange(billing, billingToken, "/internal/budgets/" + budget + "/cancellation", request, request.operationId(), correlation);
        var r = cancellationReply(reply, request.operationId());
        if (!"CANCELLED".equals(r.result()) || !budget.equals(r.budgetId())
                || !Integer.valueOf(request.budgetVersion()).equals(r.budgetVersion())) { throw new SagaDependencyException(); }
        return r;
    }
    public CancellationResult closeBudgetContext(UUID order, ContextCancellation request, UUID correlation) {
        var reply = exchange(billing, billingToken, "/internal/budgets/service-orders/" + order + "/cancellation", request, request.operationId(), correlation);
        var r = cancellationReply(reply, request.operationId());
        if (!order.equals(r.serviceOrderId()) || !("NO_BUDGET".equals(r.result()) && r.budgetId() == null && r.budgetVersion() == null
                || "CANCELLED".equals(r.result()) && r.budgetId() != null && r.budgetVersion() != null && r.budgetVersion() > 0)) {
            throw new SagaDependencyException();
        }
        return r;
    }
    private CancellationResult cancellationReply(HttpResponse<String> reply, UUID operation) {
        conflict(reply, Set.of("IDEMPOTENCY_CONFLICT", "BUDGET_VERSION_CONFLICT", "BUDGET_NOT_ELIGIBLE", "FINANCIAL_EFFECT_PENDING"));
        try {
            var r = json.readValue(reply.body(), CancellationResult.class);
            if (reply.statusCode() != 200 || !operation.equals(r.operationId())) { throw new SagaDependencyException(); }
            return r;
        } catch (RuntimeException ex) { throw new SagaDependencyException(); }
    }
    private void conflict(HttpResponse<String> reply, Set<String> codes) {
        if (reply.statusCode() != 409) { return; }
        String code;
        try { code = json.readTree(reply.body()).path("code").asString(); }
        catch (RuntimeException ex) { throw new SagaDependencyException(); }
        if (!codes.contains(code)) { throw new SagaDependencyException(); }
        throw new SagaConflictException(code);
    }
    public Settlement settlement(UUID id, UUID correlation) {
        var reply = exchange(billing, billingToken, "/internal/payments/service-orders/" + id + "/settlement", null, null, correlation);
        try {
            if (reply.statusCode() != 200) { throw new SagaDependencyException(); }
            var tree = json.readTree(reply.body());
            if (!tree.path("settled").isBoolean()) { throw new SagaDependencyException(); }
            var r = json.treeToValue(tree, Settlement.class);
            if (!id.equals(r.serviceOrderId()) || r.verifiedAt() == null || r.verifiedAt().isAfter(Instant.now().plusSeconds(30))
                    || r.verifiedAt().isBefore(Instant.now().minusSeconds(60))
                    || (r.settled() ? !"PAID".equals(r.paymentStatus()) || r.paymentId() == null
                                   : !"UNSETTLED".equals(r.paymentStatus()))) { throw new SagaDependencyException(); }
            return r;
        } catch (RuntimeException ex) { throw new SagaDependencyException(); }
    }
}
