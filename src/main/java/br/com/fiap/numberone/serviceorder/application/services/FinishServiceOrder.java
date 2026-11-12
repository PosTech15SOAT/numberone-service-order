package br.com.fiap.numberone.serviceorder.application.services;

import br.com.fiap.numberone.serviceorder.application.gateways.MaintenanceHistoryGateway;
import br.com.fiap.numberone.serviceorder.application.gateways.SagaDependencyException;
import br.com.fiap.numberone.serviceorder.application.gateways.SagaParticipants;
import br.com.fiap.numberone.serviceorder.application.gateways.OrderCompletionStore;
import br.com.fiap.numberone.serviceorder.application.gateways.OrderCompletionStore.SagaSnapshot;
import br.com.fiap.numberone.serviceorder.application.gateways.OrderCompletionStore.SagaState;
import br.com.fiap.numberone.serviceorder.application.gateways.ServiceOrderGateway;
import br.com.fiap.numberone.serviceorder.domain.entities.ServiceOrder;
import br.com.fiap.numberone.serviceorder.domain.enums.ServiceOrderStatus;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderNotFoundException;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderRuleException;
import br.com.fiap.numberone.serviceorder.domain.valueobjects.MaintenanceSnapshot;
import br.com.fiap.numberone.shared.application.gateways.BranchScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class FinishServiceOrder {
    private static final Set<SagaState> DELIVERY_STATES = Set.of(SagaState.WAITING_PAYMENT, SagaState.READY_FOR_DELIVERY);
    private final ServiceOrderGateway orders;
    private final OrderCompletionStore persistence;
    private final SagaParticipants participants;
    private final BranchScope branch;
    private final MaintenanceHistoryGateway history;
    private final TransactionTemplate tx;

    public FinishServiceOrder(ServiceOrderGateway orders, OrderCompletionStore persistence, SagaParticipants participants,
                        BranchScope branch, PlatformTransactionManager manager, MaintenanceHistoryGateway history) {
        this.orders = orders;
        this.persistence = persistence;
        this.participants = participants;
        this.branch = branch;
        this.history = history;
        tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(30);
    }

    private ServiceOrder load(UUID id) {
        return orders.find(id, branch.id(), true).orElseThrow(OrderNotFoundException::new);
    }

    public ServiceOrder complete(UUID id, Integer odometerKm) {
        return tx.execute(status -> {
            var order = load(id);
            var saga = persistence.lockSaga(id);
            StartServiceOrderItem.executionGuard(order, saga);
            if (saga.state() != SagaState.IN_EXECUTION) {
                throw new OrderRuleException("ORDER_NOT_IN_PROGRESS");
            }
            if (persistence.hasPendingStarts(id)) {
                throw new OrderRuleException("MATERIAL_CONSUMPTION_PENDING");
            }
            order.complete();
            var saved = orders.save(order);
            persistence.updateSagaState(id, SagaState.WAITING_PAYMENT);
            history.append(MaintenanceSnapshot.capture(order, Instant.now(), odometerKm));
            return saved;
        });
    }

    private record DeliverySnapshot(ServiceOrder order, long sagaVersion) { }

    private void deliveryGuard(ServiceOrder order, SagaSnapshot saga) {
        order.requestDelivery();
        if (saga.cancellationInProgress() || !DELIVERY_STATES.contains(saga.state())) {
            throw new OrderRuleException("DELIVERY_GUARDS_CHANGED");
        }
        if (persistence.hasPendingStarts(order.getId())) {
            throw new OrderRuleException("MATERIAL_CONSUMPTION_PENDING");
        }
    }

    public ServiceOrder deliver(UUID id, UUID correlation) {
        var before = tx.execute(status -> {
            var order = load(id);
            var saga = persistence.lockSaga(id);
            if (order.getStatus() != ServiceOrderStatus.DELIVERED) {
                deliveryGuard(order, saga);
                persistence.markSettlementPending(id);
            }
            return new DeliverySnapshot(order, saga.version());
        });
        if (before.order().getStatus() == ServiceOrderStatus.DELIVERED) {
            return before.order();
        }

        var settlement = participants.settlement(id, correlation);
        var delivered = tx.execute(status -> {
            var order = load(id);
            var saga = persistence.lockSaga(id);
            if (order.getStatus() == ServiceOrderStatus.DELIVERED) {
                return order;
            }
            deliveryGuard(order, saga);
            if (!Objects.equals(order.getVersion(), before.order().getVersion()) || saga.version() != before.sagaVersion()) {
                throw new OrderRuleException("DELIVERY_GUARDS_CHANGED");
            }
            if (!settlement.settled()) {
                persistence.recordSettlementVerification(id, SagaState.WAITING_PAYMENT, settlement.verifiedAt());
                return null;
            }
            order.confirmDelivery(now());
            var saved = orders.save(order);
            persistence.recordSettlementVerification(id, SagaState.COMPLETED, settlement.verifiedAt());
            return saved;
        });
        if (delivered == null) {
            throw new OrderRuleException("NOT_SETTLED");
        }
        return delivered;
    }

    /** Financial events request this refresh; a failure leaves durable pending verification. */
    public void refreshSettlement(UUID id, UUID correlation) {
        var version = tx.execute(status -> {
            var order = persistence.findInternalOrderForUpdate(id).orElseThrow();
            var saga = persistence.lockSaga(id);
            return order.getStatus() == ServiceOrderStatus.COMPLETED && saga.settlementPending()
                    && DELIVERY_STATES.contains(saga.state()) && !saga.cancellationInProgress()
                    ? saga.version() : null;
        });
        if (version == null) {
            return;
        }
        SagaParticipants.Settlement result;
        try {
            result = participants.settlement(id, correlation);
        } catch (SagaDependencyException unavailable) {
            return;
        }
        tx.executeWithoutResult(status -> {
            var order = persistence.findInternalOrderForUpdate(id).orElseThrow();
            var saga = persistence.lockSaga(id);
            if (saga.version() != version || order.getStatus() != ServiceOrderStatus.COMPLETED) {
                return;
            }
            try {
                deliveryGuard(order, saga);
            } catch (OrderRuleException blocked) {
                return;
            }
            var next = result.settled() ? SagaState.READY_FOR_DELIVERY : SagaState.WAITING_PAYMENT;
            persistence.recordSettlementVerification(id, next, result.verifiedAt());
        });
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
