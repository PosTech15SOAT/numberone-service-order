package br.com.fiap.numberone.serviceorder.application.services;

import br.com.fiap.numberone.serviceorder.application.gateways.BillingSagaGateway;
import br.com.fiap.numberone.serviceorder.application.gateways.InventorySagaGateway;
import br.com.fiap.numberone.serviceorder.application.gateways.OrderCompletionStore;
import br.com.fiap.numberone.serviceorder.application.gateways.OrderCompletionStore.SagaState;
import br.com.fiap.numberone.serviceorder.application.gateways.SagaCompensationStore;
import br.com.fiap.numberone.serviceorder.application.gateways.SagaCompensationStore.*;
import br.com.fiap.numberone.serviceorder.application.gateways.SagaConflictException;
import br.com.fiap.numberone.serviceorder.application.gateways.SagaDependencyException;
import br.com.fiap.numberone.serviceorder.application.gateways.ServiceOrderGateway;
import br.com.fiap.numberone.serviceorder.domain.entities.ServiceOrder;
import br.com.fiap.numberone.serviceorder.domain.enums.ServiceOrderStatus;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderNotFoundException;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderRuleException;
import br.com.fiap.numberone.shared.application.gateways.BranchScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class SagaCompensation {
    private static final Set<ServiceOrderStatus> BEFORE_EXECUTION = Set.of(ServiceOrderStatus.RECEIVED,
            ServiceOrderStatus.IN_DIAGNOSIS, ServiceOrderStatus.WAITING_APPROVAL,
            ServiceOrderStatus.APPROVED, ServiceOrderStatus.REJECTED);
    private static final Set<SagaState> CANCELLATION_STATES = Set.of(
            SagaState.MANUAL_INTERVENTION, SagaState.COMPENSATING, SagaState.COMPENSATED);

    private final SagaCompensationStore cancellations;
    private final OrderCompletionStore sagas;
    private final ServiceOrderGateway orders;
    private final BranchScope branch;
    private final InventorySagaGateway inventory;
    private final BillingSagaGateway billing;
    private final TransactionTemplate tx;

    public SagaCompensation(SagaCompensationStore cancellations, OrderCompletionStore sagas, ServiceOrderGateway orders,
                            BranchScope branch, InventorySagaGateway inventory, BillingSagaGateway billing,
                            PlatformTransactionManager manager) {
        this.cancellations = cancellations;
        this.sagas = sagas;
        this.orders = orders;
        this.branch = branch;
        this.inventory = inventory;
        this.billing = billing;
        tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(30);
    }

    public Status request(UUID orderId, Request request, UUID author, UUID correlation) {
        tx.executeWithoutResult(status -> {
            var order = loadScoped(orderId);
            var cancellation = cancellations.lockCancellation(orderId);
            if (cancellation.audit() != null) {
                if (!cancellation.audit().request().equals(request)) {
                    throw new OrderRuleException("CANCELLATION_REQUEST_CONFLICT");
                }
                return;
            }
            if (request.verifiedAt().isAfter(Instant.now().plusSeconds(30))) {
                throw new OrderRuleException("INVALID_VERIFICATION_TIME");
            }
            var audit = new Audit(request, author, Instant.now(), correlation, order.compositionSnapshot(),
                    eligibleForAutomaticCancellation(order, cancellation.state(), request));
            String reason = request.physicallyRemoved() ? "VERIFIED_RETURN" : "CANCELLED_BEFORE_USE";
            cancellations.registerCancellation(orderId, audit, reason);
        });
        return status(orderId);
    }

    public Status status(UUID orderId) {
        return tx.execute(status -> {
            loadScoped(orderId);
            return cancellations.readStatus(orderId);
        });
    }

    public Status resume(UUID orderId, String decision, UUID author) {
        tx.executeWithoutResult(status -> {
            var order = loadScoped(orderId);
            var cancellation = cancellations.lockCancellation(orderId);
            if (cancellation.state() != SagaState.MANUAL_INTERVENTION || cancellation.audit() == null
                    || !cancellation.audit().automatic() || !beforeExecution(order)) {
                throw new OrderRuleException("OPERATIONAL_DECISION_REQUIRED");
            }
            cancellations.resumeCancellation(orderId, new ReviewDecision(author, decision, cancellation.reason(), Instant.now()));
        });
        return status(orderId);
    }

    public void recover(UUID orderId) {
        var work = tx.execute(status -> prepareNextStep(orderId));
        if (work == null) {
            return;
        }

        try {
            switch (work.step()) {
                case ResolveStock step -> {
                    var result = resolveStock(step, work.correlation());
                    tx.executeWithoutResult(status -> confirmStock(orderId, work.owner(), step, result));
                }
                case BillingStep step -> {
                    var result = closeBilling(orderId, step, work.correlation());
                    tx.executeWithoutResult(status -> finish(orderId, work.owner(), step, result));
                }
            }
        } catch (SagaConflictException conflict) {
            tx.executeWithoutResult(status -> {
                loadInternal(orderId);
                if (cancellations.lockCancellation(orderId).ownedBy(work.owner())) {
                    cancellations.markForReview(orderId, work.step().id(), conflict.getMessage());
                }
            });
        } catch (SagaDependencyException unavailable) {
            tx.executeWithoutResult(status -> {
                loadInternal(orderId);
                if (cancellations.lockCancellation(orderId).ownedBy(work.owner())) {
                    cancellations.deferStep(orderId, work.owner(), work.step().id());
                }
            });
        }
    }

    private Recovery prepareNextStep(UUID orderId) {
        var order = loadInternal(orderId);
        var cancellation = cancellations.lockCancellation(orderId);
        if (cancellation.state() != SagaState.COMPENSATING || cancellation.audit() == null) {
            return null;
        }
        var owner = cancellations.claimCancellation(orderId);
        if (owner.isEmpty()) {
            return null;
        }
        if (!beforeExecution(order) || !cancellation.audit().automatic()) {
            cancellations.markForReview(orderId, null, "CANCELLATION_GUARDS_CHANGED");
            return null;
        }

        var stock = cancellations.nextStockStep(orderId);
        Step step = stock.isPresent() ? stock.get() : cancellations.billingStep(orderId, () -> newBillingStep(order));
        return new Recovery(owner.get(), cancellation.audit().correlationId(), step);
    }

    private BillingStep newBillingStep(ServiceOrder order) {
        UUID operationId = UUID.randomUUID();
        String reason = "SERVICE_ORDER_CANCELLED";
        if (order.getCurrentBudgetId() == null) {
            return new CloseBudgetContext(operationId, order.getBudgetRevision(),
                    new BillingSagaGateway.ContextCancellation(operationId, reason));
        }
        return new CancelBudget(operationId, order.getCurrentBudgetId(),
                new BillingSagaGateway.Cancellation(operationId, order.getBudgetRevision(), reason));
    }

    private InventorySagaGateway.InventoryOperation resolveStock(ResolveStock step, UUID correlation) {
        var result = inventory.inventoryOperation(step.originalId(), correlation);
        if (!Set.of("REJECTED", "CLOSED", "REVERSED").contains(result.result())) {
            try {
                result = inventory.reverse(step.originalId(), step.request(), correlation);
            } catch (SagaConflictException conflict) {
                if (!"OPERATION_ALREADY_REJECTED".equals(conflict.getMessage())) {
                    throw conflict;
                }
                result = inventory.inventoryOperation(step.originalId(), correlation);
                if (!"REJECTED".equals(result.result())) {
                    throw new SagaDependencyException();
                }
            }
        }
        if (Set.of("CLOSED", "REVERSED").contains(result.result()) && !step.id().equals(result.compensationId())) {
            throw new SagaConflictException("COMPENSATION_ID_CONFLICT");
        }
        return result;
    }

    private BillingSagaGateway.CancellationResult closeBilling(UUID orderId, BillingStep step, UUID correlation) {
        return switch (step) {
            case CancelBudget cancel -> billing.cancelBudget(cancel.budgetId(), cancel.request(), correlation);
            case CloseBudgetContext close -> billing.closeBudgetContext(orderId, close.request(), correlation);
        };
    }

    private void confirmStock(UUID orderId, UUID owner, ResolveStock step, InventorySagaGateway.InventoryOperation result) {
        loadInternal(orderId);
        if (stillCompensating(orderId, owner)) {
            cancellations.recordStockResolution(orderId, owner, step, result);
        }
    }

    private void finish(UUID orderId, UUID owner, BillingStep step, BillingSagaGateway.CancellationResult result) {
        var order = loadInternal(orderId);
        if (!stillCompensating(orderId, owner)) {
            return;
        }
        if (!beforeExecution(order) || !cancellations.completeBilling(orderId, owner, step, result)) {
            throw new OrderRuleException("COMPENSATION_EFFECTS_PENDING");
        }
        order.confirmCompensatedCancellation();
        orders.save(order);
    }

    private boolean stillCompensating(UUID orderId, UUID owner) {
        var cancellation = cancellations.lockCancellation(orderId);
        return cancellation.ownedBy(owner) && cancellation.state() == SagaState.COMPENSATING;
    }

    private boolean eligibleForAutomaticCancellation(ServiceOrder order, SagaState state, Request request) {
        return "FULL".equals(request.scope()) && request.unusedConfirmed()
                && (!request.physicallyRemoved() || request.returnConfirmed()) && beforeExecution(order)
                && !CANCELLATION_STATES.contains(state)
                && order.getItems().stream().flatMap(item -> item.getMaterials().stream())
                        .noneMatch(material -> material.hasMaterialEffect());
    }

    private boolean beforeExecution(ServiceOrder order) {
        return BEFORE_EXECUTION.contains(order.getStatus())
                && order.getItems().stream().noneMatch(item -> item.getStartDateTime() != null);
    }

    private ServiceOrder loadScoped(UUID orderId) {
        return orders.find(orderId, branch.id(), true).orElseThrow(OrderNotFoundException::new);
    }

    private ServiceOrder loadInternal(UUID orderId) {
        return sagas.findInternalOrderForUpdate(orderId).orElseThrow(OrderNotFoundException::new);
    }

    private record Recovery(UUID owner, UUID correlation, Step step) { }
}
