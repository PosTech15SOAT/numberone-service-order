package br.com.fiap.numberone.serviceorder.application.gateways;

import br.com.fiap.numberone.serviceorder.domain.entities.ServiceOrder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrderCompletionStore {
    enum SagaState {
        IN_DIAGNOSIS, WAITING_APPROVAL, WAITING_REVISION, READY_FOR_EXECUTION, START_PENDING,
        IN_EXECUTION, WAITING_PAYMENT, READY_FOR_DELIVERY, COMPLETED, COMPENSATING,
        COMPENSATED, MANUAL_INTERVENTION
    }

    record SagaSnapshot(SagaState state, long version, boolean cancellationInProgress, boolean settlementPending) { }

    SagaSnapshot lockSaga(UUID orderId);
    Optional<ServiceOrder> findInternalOrderForUpdate(UUID orderId);
    boolean hasPendingStarts(UUID orderId);
    void updateSagaState(UUID orderId, SagaState state);
    void markSettlementPending(UUID orderId);
    void recordSettlementVerification(UUID orderId, SagaState state, Instant verifiedAt);
}
