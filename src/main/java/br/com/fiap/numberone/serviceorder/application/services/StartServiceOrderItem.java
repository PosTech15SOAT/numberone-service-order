package br.com.fiap.numberone.serviceorder.application.services;

import br.com.fiap.numberone.serviceorder.application.gateways.ItemStartStore;
import br.com.fiap.numberone.serviceorder.application.gateways.ItemStartStore.DispatchTicket;
import br.com.fiap.numberone.serviceorder.application.gateways.ItemStartStore.StartAttempt;
import br.com.fiap.numberone.serviceorder.application.gateways.ItemStartStore.StartOutcome;
import br.com.fiap.numberone.serviceorder.application.gateways.ItemStartStore.StartState;
import br.com.fiap.numberone.serviceorder.application.gateways.OrderCompletionStore;
import br.com.fiap.numberone.serviceorder.application.gateways.OrderCompletionStore.SagaSnapshot;
import br.com.fiap.numberone.serviceorder.application.gateways.OrderCompletionStore.SagaState;
import br.com.fiap.numberone.serviceorder.application.gateways.SagaDependencyException;
import br.com.fiap.numberone.serviceorder.application.gateways.SagaParticipants;
import br.com.fiap.numberone.serviceorder.application.gateways.ServiceOrderGateway;
import br.com.fiap.numberone.serviceorder.domain.entities.ServiceOrder;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderNotFoundException;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderRuleException;
import br.com.fiap.numberone.shared.application.gateways.BranchScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StartServiceOrderItem {
    private static final Set<SagaState> EXECUTION_STATES = Set.of(
            SagaState.READY_FOR_EXECUTION, SagaState.START_PENDING, SagaState.IN_EXECUTION);
    private static final String LOCAL_GUARDS_CHANGED = "LOCAL_GUARDS_CHANGED";

    private final ServiceOrderGateway orders;
    private final ItemStartStore starts;
    private final OrderCompletionStore sagas;
    private final SagaParticipants inventory;
    private final BranchScope branch;
    private final TransactionTemplate tx;

    public StartServiceOrderItem(ServiceOrderGateway orders, ItemStartStore starts, OrderCompletionStore sagas,
                                 SagaParticipants inventory, BranchScope branch, PlatformTransactionManager manager) {
        this.orders = orders;
        this.starts = starts;
        this.sagas = sagas;
        this.inventory = inventory;
        this.branch = branch;
        tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(30);
    }

    public record StartResult(ServiceOrder order, UUID itemId, UUID operationId, String state, String reason) { }

    public ServiceOrder readiness(UUID orderId) {
        return tx.execute(status -> {
            var order = load(orderId);
            executionGuard(order, sagas.lockSaga(orderId));
            return order;
        });
    }

    public StartResult start(UUID itemId, UUID requestedId, UUID responsible, UUID correlation) {
        var attempt = tx.execute(status -> prepareStart(itemId, requestedId, responsible));
        if (!attempt.state().hasDefinitiveResult()) {
            dispatch(attempt.id(), correlation, false);
        }
        return tx.execute(status -> {
            var order = load(attempt.orderId());
            var current = starts.readAttempt(attempt.id());
            return new StartResult(order, current.itemId(), current.id(), current.state().name(), current.reason());
        });
    }

    private StartAttempt prepareStart(UUID itemId, UUID requestedId, UUID responsible) {
        var context = starts.loadForRequest(itemId, branch.id(), requestedId);
        var order = context.order();
        var saga = context.saga();
        requireNoCancellation(saga);

        if (context.requestedIntent() != null) {
            var previous = context.requestedAttempt();
            if (previous == null || !previous.itemId().equals(itemId)
                    || previous.compositionVersion() != order.getCompositionVersion()) {
                throw new OrderRuleException("IDEMPOTENCY_CONFLICT");
            }
            if (previous.state().hasDefinitiveResult()) {
                return previous;
            }
        }

        var last = context.lastAttempt();
        if (last != null) {
            if (last.state().isPending()) {
                executionGuard(order, saga);
                return last;
            }
            if (last.state() == StartState.CONFIRMED || requestedId == null) {
                return last;
            }
        }

        executionGuard(order, saga);
        UUID operationId = requestedId == null ? UUID.randomUUID() : requestedId;
        var supplies = order.getApprovedComposition().items().stream()
                .filter(item -> item.serviceOrderItemId().equals(itemId)).findFirst().orElseThrow().supplies();
        var request = new SagaParticipants.Consumption(operationId, order.getId(), itemId, responsible, supplies);
        var outcome = supplies.isEmpty()
                ? new StartOutcome(StartState.CONFIRMED, "NO_MATERIALS", SagaState.IN_EXECUTION, null)
                : new StartOutcome(StartState.START_PENDING, null,
                        saga.state() == SagaState.IN_EXECUTION ? null : SagaState.START_PENDING, null);
        var attempt = starts.registerStart(request, order.getCompositionVersion(), order.getBudgetRevision(), outcome);
        order.item(itemId).requestStart(operationId);
        if (supplies.isEmpty()) {
            order.confirmItemStart(itemId, now());
        }
        orders.save(order);
        return attempt;
    }

    public void recoverStart(UUID operationId, UUID correlation) {
        dispatch(operationId, correlation, true);
    }

    private void dispatch(UUID operationId, UUID correlation, boolean recovering) {
        var ticket = tx.execute(status -> claimDispatch(operationId));
        if (ticket.isEmpty()) {
            return;
        }
        var dispatch = ticket.get();
        try {
            var result = requestInventoryResult(dispatch, correlation, recovering);
            if (result != null) {
                tx.executeWithoutResult(status -> applyResult(dispatch, result));
            }
        } catch (SagaDependencyException unavailable) {
            // The original operation stays available for recovery.
        } finally {
            tx.executeWithoutResult(status -> starts.finishDispatch(dispatch));
        }
    }

    private Optional<DispatchTicket> claimDispatch(UUID operationId) {
        var context = starts.loadForDispatch(operationId);
        var attempt = context.attempt();
        if (!attempt.state().isPending() || !canExecute(context.order(), context.saga())) {
            return Optional.empty();
        }
        if (!matchesCurrentStart(context.order(), attempt)) {
            starts.saveOutcome(attempt, review(LOCAL_GUARDS_CHANGED, null));
            return Optional.empty();
        }
        return starts.claimDispatch(attempt);
    }

    private SagaParticipants.ConsumptionResult requestInventoryResult(DispatchTicket ticket, UUID correlation, boolean recovering) {
        var attempt = ticket.attempt();
        if (recovering || ticket.retry()) {
            var known = inventory.inventoryOperation(attempt.id(), correlation);
            switch (known.result()) {
                case "CONSUMED", "REJECTED" -> {
                    return new SagaParticipants.ConsumptionResult(attempt.id(), known.result(), known.movements(), known.code());
                }
                case "CLOSED", "REVERSED" -> {
                    tx.executeWithoutResult(status -> recordUnexpectedClosure(ticket, known));
                    return null;
                }
                case "UNKNOWN" -> { /* The original request can be retried after rechecking the order. */ }
                default -> { return null; }
            }
        }

        boolean mayConsume = tx.execute(status -> {
            var context = starts.loadForDispatch(attempt.id());
            return canExecute(context.order(), context.saga()) && context.ownedBy(ticket);
        });
        return mayConsume ? inventory.consume(attempt.request(), correlation) : null;
    }

    private void recordUnexpectedClosure(DispatchTicket ticket, SagaParticipants.InventoryOperation result) {
        var context = starts.loadForDispatch(ticket.attempt().id());
        if (!context.saga().cancellationInProgress() && context.ownedBy(ticket) && context.attempt().state().isPending()) {
            starts.saveOutcome(context.attempt(), review("UNEXPECTED_STOCK_CLOSURE", result));
        }
    }

    private void applyResult(DispatchTicket ticket, SagaParticipants.ConsumptionResult result) {
        var context = starts.loadForDispatch(ticket.attempt().id());
        var attempt = context.attempt();
        if (context.saga().cancellationInProgress()) {
            starts.saveLateResult(attempt.id(), result);
            return;
        }
        if (!attempt.state().isPending() || !context.ownedBy(ticket)) {
            return;
        }

        var order = context.order();
        if (!canExecute(order, context.saga()) || !matchesCurrentStart(order, attempt)
                || !order.item(attempt.itemId()).isStartPending()) {
            starts.saveOutcome(attempt, review(LOCAL_GUARDS_CHANGED, result));
            return;
        }

        var item = order.item(attempt.itemId());
        if ("CONSUMED".equals(result.result())) {
            item.getMaterials().forEach(material -> material.confirmConsumption(attempt.id(), result.movements()));
            order.confirmItemStart(attempt.itemId(), now());
            starts.saveOutcome(attempt, new StartOutcome(StartState.CONFIRMED, null, SagaState.IN_EXECUTION, result));
        } else {
            item.insufficientStock();
            boolean ready = context.saga().state() != SagaState.IN_EXECUTION
                    && !starts.hasOtherPendingStarts(order.getId(), attempt.id());
            starts.saveOutcome(attempt, new StartOutcome(StartState.REJECTED, "INSUFFICIENT_STOCK",
                    ready ? SagaState.READY_FOR_EXECUTION : null, result));
        }
        orders.save(order);
    }

    private static StartOutcome review(String reason, Object result) {
        return new StartOutcome(StartState.NEEDS_REVIEW, reason, SagaState.MANUAL_INTERVENTION, result);
    }

    private boolean matchesCurrentStart(ServiceOrder order, StartAttempt attempt) {
        return order.getCompositionVersion() == attempt.compositionVersion()
                && order.getBudgetRevision() == attempt.budgetRevision()
                && attempt.id().equals(order.item(attempt.itemId()).getStartOperationId());
    }

    private boolean canExecute(ServiceOrder order, SagaSnapshot saga) {
        try {
            executionGuard(order, saga);
            return true;
        } catch (OrderRuleException blocked) {
            return false;
        }
    }

    static void executionGuard(ServiceOrder order, SagaSnapshot saga) {
        order.requestStart();
        requireNoCancellation(saga);
        if (!EXECUTION_STATES.contains(saga.state())) {
            throw new OrderRuleException("SAGA_NOT_READY_FOR_EXECUTION");
        }
    }

    private static void requireNoCancellation(SagaSnapshot saga) {
        if (saga.cancellationInProgress()) {
            throw new OrderRuleException("CANCELLATION_IN_PROGRESS");
        }
    }

    private ServiceOrder load(UUID orderId) {
        return orders.find(orderId, branch.id(), true).orElseThrow(OrderNotFoundException::new);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
