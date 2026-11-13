package br.com.fiap.numberone.serviceorder.application.gateways;

import br.com.fiap.numberone.serviceorder.application.gateways.OrderCompletionStore.SagaState;
import br.com.fiap.numberone.serviceorder.domain.entities.CompositionSnapshot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public interface SagaCompensationStore {
    record Request(@NotBlank @Pattern(regexp = "FULL|PARTIAL") String scope,
                   @NotBlank @Size(max = 1000) String reason, boolean unusedConfirmed,
                   boolean physicallyRemoved, boolean returnConfirmed,
                   @NotNull Instant verifiedAt, @NotBlank @Size(max = 1000) String evidenceReference) { }

    record Audit(Request request, UUID author, Instant acceptedAt, UUID correlationId,
                 CompositionSnapshot composition, boolean automatic) { }

    record ReviewDecision(UUID author, String reason, String previousReason, Instant at) { }

    record Status(UUID serviceOrderId, String state, String reason, String returnState,
                  Object cancellation, Object reviewDecisions, List<Map<String, Object>> operations) { }

    record CancellationState(SagaState state, String reason, Audit audit, UUID recoveryOwner) {
        public boolean ownedBy(UUID owner) {
            return owner.equals(recoveryOwner);
        }
    }

    sealed interface Step permits ResolveStock, BillingStep {
        UUID id();
    }

    record ResolveStock(UUID id, UUID originalId, InventorySagaGateway.Reversal request) implements Step { }

    sealed interface BillingStep extends Step permits CancelBudget, CloseBudgetContext { }

    record CancelBudget(UUID id, UUID budgetId, BillingSagaGateway.Cancellation request) implements BillingStep { }

    record CloseBudgetContext(UUID id, int budgetRevision, BillingSagaGateway.ContextCancellation request) implements BillingStep { }

    CancellationState lockCancellation(UUID orderId);
    void registerCancellation(UUID orderId, Audit audit, String reversalReason);
    Status readStatus(UUID orderId);
    void resumeCancellation(UUID orderId, ReviewDecision decision);
    Optional<UUID> claimCancellation(UUID orderId);
    Optional<ResolveStock> nextStockStep(UUID orderId);
    BillingStep billingStep(UUID orderId, Supplier<BillingStep> create);
    void recordStockResolution(UUID orderId, UUID owner, ResolveStock step, InventorySagaGateway.InventoryOperation result);
    boolean completeBilling(UUID orderId, UUID owner, BillingStep step, BillingSagaGateway.CancellationResult result);
    void markForReview(UUID orderId, UUID operationId, String reason);
    void deferStep(UUID orderId, UUID owner, UUID operationId);
}
