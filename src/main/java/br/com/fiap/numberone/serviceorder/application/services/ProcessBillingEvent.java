package br.com.fiap.numberone.serviceorder.application.services;

import br.com.fiap.numberone.serviceorder.application.events.BillingEvent;
import br.com.fiap.numberone.serviceorder.application.events.BillingEvent.Outcome;
import br.com.fiap.numberone.serviceorder.application.gateways.BillingInboxStore;
import br.com.fiap.numberone.serviceorder.application.gateways.BillingInboxStore.EventContext;
import br.com.fiap.numberone.serviceorder.application.gateways.BillingSagaGateway;
import br.com.fiap.numberone.serviceorder.application.gateways.OrderCompletionStore;
import br.com.fiap.numberone.serviceorder.application.gateways.OrderCompletionStore.SagaState;
import br.com.fiap.numberone.serviceorder.application.gateways.ServiceOrderGateway;
import br.com.fiap.numberone.serviceorder.domain.entities.ServiceOrder;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ProcessBillingEvent {
    private final BillingInboxStore inbox;
    private final ServiceOrderGateway orders;
    private final OrderCompletionStore sagas;
    private final BillingSagaGateway billing;
    private final FinishServiceOrder finishOrder;
    private final TransactionTemplate transaction;

    public ProcessBillingEvent(BillingInboxStore inbox, ServiceOrderGateway orders, OrderCompletionStore sagas,
                               BillingSagaGateway billing, FinishServiceOrder finishOrder,
                               PlatformTransactionManager manager) {
        this.inbox = inbox;
        this.orders = orders;
        this.sagas = sagas;
        this.billing = billing;
        this.finishOrder = finishOrder;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(30);
    }

    public Outcome process(BillingEvent event) {
        Outcome result;
        try {
            result = transaction.execute(status -> apply(event, null, null));
        } catch (MissingContext missing) {
            // The first transaction rolled back. Revalidate the order after consulting Billing.
            var current = billing.currentBudget(event.orderId(), event.correlationId());
            result = transaction.execute(status -> apply(event, current, missing.orderVersion));
        }
        if (event.payload() instanceof BillingEvent.Payment) {
            finishOrder.refreshSettlement(event.orderId(), event.correlationId());
        }
        return result;
    }

    private Outcome apply(BillingEvent event, BillingSagaGateway.CurrentBudget current, Long expectedVersion) {
        var hash = inbox.findProcessedHashForUpdate(event.id());
        if (hash.isPresent()) {
            if (!event.hash().equals(hash.get())) {
                throw new IllegalStateException("EVENT_ID_COLLISION");
            }
            return Outcome.DUPLICATE;
        }
        var context = inbox.begin(event);
        var outcome = switch (event.payload()) {
            case BillingEvent.Payment payment -> applyPayment(event, payment, context.order());
            case BillingEvent.Budget budget -> applyBudget(event, budget, context, current, expectedVersion);
        };
        inbox.complete(event.id(), outcome);
        return outcome;
    }

    private Outcome applyBudget(BillingEvent event, BillingEvent.Budget budget, EventContext context,
                                BillingSagaGateway.CurrentBudget current, Long expectedVersion) {
        var order = context.order();
        var state = context.saga().state();
        var budgetId = budget.id();
        int revision = budget.version();
        int composition = budget.compositionVersion();
        if (revision < order.getBudgetRevision()) {
            return Outcome.OBSOLETE;
        } else if (revision == order.getBudgetRevision() && order.getCurrentBudgetId() != null
                && (!budgetId.equals(order.getCurrentBudgetId()) || !Integer.valueOf(composition).equals(order.getBudgetCompositionVersion()))) {
            throw new OrderRuleException("BUDGET_COMPOSITION_CONFLICT");
        }
        if (Set.of("CANCELLED", "DELIVERED", "COMPLETED").contains(order.getStatus().name())
                || Set.of(SagaState.COMPENSATING, SagaState.COMPENSATED, SagaState.COMPLETED).contains(state)
                || context.saga().cancellationInProgress()) { return Outcome.IGNORED; }
        if (composition < order.getCompositionVersion()) { return Outcome.OBSOLETE; }
        boolean linked = budgetId.equals(order.getCurrentBudgetId()) && revision == order.getBudgetRevision();
        boolean sent = event.kind() == BillingEvent.Kind.BUDGET_SENT;
        if (linked && sent) { return Outcome.IGNORED; }
        if (linked && (order.hasApprovedBudget() || order.getStatus().name().equals("REJECTED"))) {
            if (event.kind() == BillingEvent.Kind.BUDGET_APPROVED && order.hasApprovedBudget()
                    && order.getApprovedAmount().compareTo(new BigDecimal(budget.approvedAmount())) == 0
                    && order.getApprovedCurrency().equals(budget.currency())) { return Outcome.IGNORED; }
            if (event.kind() == BillingEvent.Kind.BUDGET_REJECTED && order.getStatus().name().equals("REJECTED")) { return Outcome.IGNORED; }
            throw new OrderRuleException("BUDGET_DECISION_CONFLICT");
        }
        if (!Set.of(SagaState.IN_DIAGNOSIS, SagaState.WAITING_APPROVAL, SagaState.WAITING_REVISION).contains(state)) {
            throw new OrderRuleException("SAGA_STATE_REQUIRES_RECONCILIATION");
        }
        if (!linked && !sent) {
            if (current == null) { throw new MissingContext(order.getVersion()); }
            if (!Objects.equals(expectedVersion, order.getVersion())) { throw new OrderRuleException("BUDGET_CONTEXT_CHANGED"); }
            if (current.budgetVersion() > revision) { return Outcome.OBSOLETE; }
            String decision = event.kind() == BillingEvent.Kind.BUDGET_APPROVED ? "APPROVED" : "REJECTED";
            if (!current.serviceOrderId().equals(order.getId()) || !current.budgetId().equals(budgetId)
                    || current.budgetVersion() != revision || current.compositionVersion() != composition || !decision.equals(current.status())
                    || (decision.equals("APPROVED") && (!budget.approvedAmount().equals(current.amount()) || !"BRL".equals(current.currency())))) {
                throw new OrderRuleException("BUDGET_CONTEXT_CONFLICT");
            }
        }
        if (!linked) {
            // Billing serializes commercial revisions. A newer SENT or CT-10-confirmed
            // decision can overtake the previous revision's rejection on Standard SQS.
            order.registerBudget(budgetId, revision, composition);
        }
        SagaState next = SagaState.WAITING_APPROVAL;
        if (!sent) {
            if (composition != order.getCompositionVersion()) { throw new OrderRuleException("BUDGET_COMPOSITION_CONFLICT"); }
            if (event.kind() == BillingEvent.Kind.BUDGET_APPROVED) {
                order.approveBudget(budgetId, revision, order.compositionSnapshot(), new BigDecimal(budget.approvedAmount()), budget.currency());
                next = SagaState.READY_FOR_EXECUTION;
            } else { order.rejectBudget(); next = SagaState.WAITING_REVISION; }
        }
        orders.save(order);
        sagas.updateSagaState(event.orderId(), next);
        return Outcome.APPLIED;
    }
    private Outcome applyPayment(BillingEvent event, BillingEvent.Payment payment, ServiceOrder order) {
        var recorded = inbox.findPayment(payment.id());
        if (recorded.isPresent()) {
            var previous = recorded.get();
            if (!event.orderId().equals(previous.orderId())) {
                throw new OrderRuleException("PAYMENT_ORDER_CONFLICT");
            }
            if (payment.version() < previous.version()) {
                return Outcome.OBSOLETE;
            }
            if (payment.version() == previous.version()) {
                if (!payment.status().equals(previous.status())) {
                    throw new OrderRuleException("PAYMENT_VERSION_CONFLICT");
                }
                return Outcome.IGNORED;
            }
        }
        order.projectPayment(inbox.savePaymentAndFindLatestPaid(event, payment));
        orders.save(order);
        inbox.scheduleSettlementCheck(event.orderId());
        return Outcome.APPLIED;
    }

    private static class MissingContext extends RuntimeException {
        final Long orderVersion;

        MissingContext(Long orderVersion) {
            super("BUDGET_CONTEXT_UNAVAILABLE");
            this.orderVersion = orderVersion;
        }
    }
}
