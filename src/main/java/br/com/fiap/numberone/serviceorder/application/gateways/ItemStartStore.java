package br.com.fiap.numberone.serviceorder.application.gateways;

import br.com.fiap.numberone.serviceorder.application.gateways.OrderCompletionStore.SagaSnapshot;
import br.com.fiap.numberone.serviceorder.application.gateways.OrderCompletionStore.SagaState;
import br.com.fiap.numberone.serviceorder.domain.entities.ServiceOrder;

import java.util.Optional;
import java.util.UUID;

public interface ItemStartStore {
    enum StartState {
        START_PENDING, AWAITING_RESULT, CONFIRMED, REJECTED, SKIPPED, NEEDS_REVIEW;

        public boolean isPending() {
            return this == START_PENDING || this == AWAITING_RESULT;
        }

        public boolean hasDefinitiveResult() {
            return this == CONFIRMED || this == REJECTED;
        }
    }

    record StartAttempt(UUID id, UUID orderId, UUID itemId, int compositionVersion, int budgetRevision,
                        StartState state, InventorySagaGateway.Consumption request, String reason) { }

    record StartRequest(ServiceOrder order, SagaSnapshot saga, String requestedIntent,
                        StartAttempt requestedAttempt, StartAttempt lastAttempt) { }

    record DispatchContext(ServiceOrder order, SagaSnapshot saga, StartAttempt attempt, UUID leaseOwner) {
        public boolean ownedBy(DispatchTicket ticket) {
            return ticket.owner().equals(leaseOwner);
        }
    }

    record DispatchTicket(StartAttempt attempt, UUID owner, boolean retry) { }

    record StartOutcome(StartState state, String reason, SagaState nextSagaState, Object inventoryResult) { }

    StartRequest loadForRequest(UUID itemId, UUID branchId, UUID requestedId);
    StartAttempt registerStart(InventorySagaGateway.Consumption request, int compositionVersion, int budgetRevision,
                               StartOutcome outcome);
    DispatchContext loadForDispatch(UUID operationId);
    Optional<DispatchTicket> claimDispatch(StartAttempt attempt);
    void finishDispatch(DispatchTicket ticket);
    StartAttempt readAttempt(UUID operationId);
    boolean hasOtherPendingStarts(UUID orderId, UUID operationId);
    void saveOutcome(StartAttempt attempt, StartOutcome outcome);
    void saveLateResult(UUID operationId, InventorySagaGateway.ConsumptionResult result);
}
