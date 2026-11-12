package br.com.fiap.numberone.serviceorder.infrastructure.integration;

import br.com.fiap.numberone.serviceorder.application.gateways.SagaDependencyException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.Set;
import br.com.fiap.numberone.serviceorder.application.gateways.BillingSagaGateway;
import java.time.Instant;

@Component
public class HttpBillingSagaGateway implements BillingSagaGateway {
    private final ObjectMapper json;
    private final ParticipantHttpClient http;

    public HttpBillingSagaGateway(ObjectMapper json, @Qualifier("billingSagaHttp") ParticipantHttpClient http) {
        this.json = json;
        this.http = http;
    }

    @Override
    public CurrentBudget currentBudget(UUID id, UUID correlation) {
        var reply = http.get("/internal/budgets/service-orders/" + id + "/current", correlation);
        try {
            if (reply.statusCode() != 200) { throw new SagaDependencyException(); }
            var r = json.readValue(reply.body(), CurrentBudget.class);
            if (!id.equals(r.serviceOrderId()) || r.budgetId() == null || r.budgetVersion() < 1 || r.compositionVersion() < 1
                    || !Set.of("DRAFT", "SENT", "APPROVED", "REJECTED", "CANCELLED").contains(r.status())) { throw new SagaDependencyException(); }
            return r;
        } catch (RuntimeException ex) { throw new SagaDependencyException(); }
    }
    @Override
    public CancellationResult cancelBudget(UUID budget, Cancellation request, UUID correlation) {
        var reply = http.post("/internal/budgets/" + budget + "/cancellation", request, request.operationId(), correlation);
        var r = cancellationReply(reply, request.operationId());
        if (!"CANCELLED".equals(r.result()) || !budget.equals(r.budgetId())
                || !Integer.valueOf(request.budgetVersion()).equals(r.budgetVersion())) { throw new SagaDependencyException(); }
        return r;
    }
    @Override
    public CancellationResult closeBudgetContext(UUID order, ContextCancellation request, UUID correlation) {
        var reply = http.post("/internal/budgets/service-orders/" + order + "/cancellation", request, request.operationId(), correlation);
        var r = cancellationReply(reply, request.operationId());
        if (!order.equals(r.serviceOrderId()) || !("NO_BUDGET".equals(r.result()) && r.budgetId() == null && r.budgetVersion() == null
                || "CANCELLED".equals(r.result()) && r.budgetId() != null && r.budgetVersion() != null && r.budgetVersion() > 0)) {
            throw new SagaDependencyException();
        }
        return r;
    }
    private CancellationResult cancellationReply(HttpResponse<String> reply, UUID operation) {
        http.checkConflict(reply, Set.of("IDEMPOTENCY_CONFLICT", "BUDGET_VERSION_CONFLICT", "BUDGET_NOT_ELIGIBLE", "FINANCIAL_EFFECT_PENDING"));
        try {
            var r = json.readValue(reply.body(), CancellationResult.class);
            if (reply.statusCode() != 200 || !operation.equals(r.operationId())) { throw new SagaDependencyException(); }
            return r;
        } catch (RuntimeException ex) { throw new SagaDependencyException(); }
    }
    @Override
    public Settlement settlement(UUID id, UUID correlation) {
        var reply = http.get("/internal/payments/service-orders/" + id + "/settlement", correlation);
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
