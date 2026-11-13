package br.com.fiap.numberone.serviceorder.application.gateways;

import java.time.Instant;
import java.util.UUID;

public interface BillingSagaGateway {
    record CurrentBudget(UUID serviceOrderId, UUID budgetId, int budgetVersion, int compositionVersion,
                         String status, String amount, String currency) { }
    record Settlement(UUID serviceOrderId, boolean settled, String paymentStatus, Instant verifiedAt, UUID paymentId) { }
    record Cancellation(UUID operationId, int budgetVersion, String reason) { }
    record ContextCancellation(UUID operationId, String reason) { }
    record CancellationResult(UUID operationId, UUID serviceOrderId, UUID budgetId, Integer budgetVersion, String result) { }

    CancellationResult cancelBudget(UUID budgetId, Cancellation request, UUID correlationId);
    CancellationResult closeBudgetContext(UUID orderId, ContextCancellation request, UUID correlationId);
    CurrentBudget currentBudget(UUID orderId, UUID correlationId);
    Settlement settlement(UUID orderId, UUID correlationId);
}
