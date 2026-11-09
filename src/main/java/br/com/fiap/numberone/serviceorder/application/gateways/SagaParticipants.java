package br.com.fiap.numberone.serviceorder.application.gateways;

import br.com.fiap.numberone.serviceorder.domain.entities.CompositionSnapshot.Material;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

/** Active 2.0.0-os-baseline.1 wire contracts. No payment initiation belongs to OS. */
public interface SagaParticipants {
    record Consumption(UUID operationId, UUID serviceOrderId, UUID serviceOrderItemId,
                       UUID responsibleUserId, List<Material> items) { }
    record ConsumptionResult(UUID operationId, String result, List<UUID> movements, String code) { }
    record CurrentBudget(UUID serviceOrderId, UUID budgetId, int budgetVersion, int compositionVersion,
                         String status, String amount, String currency) { }
    record Settlement(UUID serviceOrderId, boolean settled, String paymentStatus, Instant verifiedAt, UUID paymentId) { }
    record InventoryOperation(UUID operationId, UUID compensationId, String result, List<UUID> movements, String code) { }
    record Evidence(UUID verifiedBy, Instant verifiedAt, String reference) { }
    record Reversal(UUID compensationId, String reason, boolean unusedConfirmed, Evidence evidence) { }
    record Cancellation(UUID operationId, int budgetVersion, String reason) { }
    record ContextCancellation(UUID operationId, String reason) { }
    record CancellationResult(UUID operationId, UUID serviceOrderId, UUID budgetId, Integer budgetVersion, String result) { }
    InventoryOperation inventoryOperation(UUID operationId, UUID correlationId);
    InventoryOperation reverse(UUID operationId, Reversal request, UUID correlationId);
    CancellationResult cancelBudget(UUID budgetId, Cancellation request, UUID correlationId);
    CancellationResult closeBudgetContext(UUID orderId, ContextCancellation request, UUID correlationId);
    ConsumptionResult consume(Consumption request, UUID correlationId);
    CurrentBudget currentBudget(UUID orderId, UUID correlationId);
    Settlement settlement(UUID orderId, UUID correlationId);
}
