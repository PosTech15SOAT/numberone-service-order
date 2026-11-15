package br.com.fiap.numberone.serviceorder.application.services;

import br.com.fiap.numberone.serviceorder.application.gateways.SagaRecoveryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SagaRecoveryWorker {
    private static final Logger LOG = LoggerFactory.getLogger(SagaRecoveryWorker.class);

    private final SagaRecoveryStore recovery;
    private final SagaCompensation compensation;
    private final StartServiceOrderItem startItem;
    private final FinishServiceOrder finishOrder;

    public SagaRecoveryWorker(SagaRecoveryStore recovery, SagaCompensation compensation,
                              StartServiceOrderItem startItem, FinishServiceOrder finishOrder) {
        this.recovery = recovery;
        this.compensation = compensation;
        this.startItem = startItem;
        this.finishOrder = finishOrder;
    }

    public void tick() {
        for (UUID orderId : recovery.dueCompensations()) {
            safely(orderId, () -> compensation.recover(orderId));
        }
        for (UUID operationId : recovery.dueStarts()) {
            safely(operationId, () -> startItem.recoverStart(operationId, operationId));
        }
        for (UUID orderId : recovery.dueSettlementChecks()) {
            recoverSettlement(orderId);
        }
    }

    private void recoverSettlement(UUID orderId) {
        recovery.claimSettlementCheck(orderId).ifPresent(owner -> {
            try {
                safely(orderId, () -> finishOrder.refreshSettlement(orderId, orderId));
            } finally {
                recovery.finishSettlementCheck(orderId, owner);
            }
        });
    }

    private void safely(UUID id, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException failure) {
            LOG.warn("Saga recovery failed identity={} type={}", id, failure.getClass().getSimpleName());
        }
    }
}
