package br.com.fiap.numberone.serviceorder.infrastructure.messaging;

import br.com.fiap.numberone.serviceorder.application.gateways.ServiceOrderGateway;
import br.com.fiap.numberone.serviceorder.application.gateways.BillingSagaGateway;
import br.com.fiap.numberone.serviceorder.application.services.FinishServiceOrder;
import br.com.fiap.numberone.serviceorder.domain.entities.ServiceOrder;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderRuleException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.Set;
import java.util.Objects;
import java.math.BigDecimal;

@Service
public class BillingInboxHandler {
    public enum Outcome { APPLIED, DUPLICATE, OBSOLETE, IGNORED }
    private final JdbcTemplate jdbc;
    private final ServiceOrderGateway orders;
    private final BillingEventCodec codec;
    private final TransactionTemplate transaction;
    private final BillingSagaGateway participants;
    private final FinishServiceOrder workflow;
    public BillingInboxHandler(JdbcTemplate jdbc, ServiceOrderGateway orders, BillingEventCodec codec,
                               PlatformTransactionManager manager, BillingSagaGateway participants, FinishServiceOrder workflow) {
        this.jdbc = jdbc; this.orders = orders; this.codec = codec;
        this.participants = participants; this.workflow = workflow;
        transaction = new TransactionTemplate(manager);
        // Returning from handle always means a real commit, even with an ambient caller transaction.
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(30);
    }
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public Outcome handle(String body) {
        var event = codec.decode(body);
        Outcome result;
        try { result = transaction.execute(status -> apply(event, null, null)); }
        catch (MissingContext missing) {
            var current = participants.currentBudget(event.orderId(), event.correlationId());
            result = transaction.execute(status -> apply(event, current, missing.version));
        }
        if (event.type().startsWith("Pagamento")) { workflow.refreshSettlement(event.orderId(), event.correlationId()); }
        return result;
    }
    private static class MissingContext extends RuntimeException {
        final Long version;
        MissingContext(Long version) { super("BUDGET_CONTEXT_UNAVAILABLE"); this.version = version; }
    }
    private Outcome apply(BillingEventCodec.Event e, BillingSagaGateway.CurrentBudget current, Long expectedVersion) {
        // Serialize identity globally, then parent, then Saga. Inserting the FK before
        // locking the parent would allow concurrent deliveries to deadlock on lock upgrades.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", e.id().toString());
        var hashes = jdbc.queryForList("SELECT content_hash FROM inbox_event WHERE event_id=?", String.class, e.id());
        if (!hashes.isEmpty()) {
            if (!e.hash().equals(hashes.getFirst())) { throw new IllegalStateException("EVENT_ID_COLLISION"); }
            return Outcome.DUPLICATE;
        }
        if (e.type().startsWith("Pagamento")) {
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 1))", e.payload().path("paymentId").asText());
        }
        // Same parent lock as HTTP mutations; order before Saga is the common lock order.
        var branch = jdbc.queryForObject("SELECT branch_id FROM ordem_servico WHERE id=? FOR UPDATE", UUID.class, e.orderId());
        var order = orders.find(e.orderId(), branch, true).orElseThrow();
        var state = jdbc.queryForObject("SELECT state FROM service_order_saga WHERE service_order_id=? FOR UPDATE", String.class, e.orderId());
        jdbc.update("""
                INSERT INTO inbox_event(event_id, content_hash, event_type, service_order_id, correlation_id, occurred_at, outcome)
                VALUES (?, ?, ?, ?, ?, ?, 'PROCESSING')
                """, e.id(), e.hash(), e.type(), e.orderId(), e.correlationId(), Timestamp.from(e.occurredAt()));
        Outcome outcome = e.type().startsWith("Pagamento") ? payment(e, order)
                : budget(e, order, state, current, expectedVersion);
        jdbc.update("UPDATE inbox_event SET outcome=? WHERE event_id=?", outcome.name(), e.id());
        return outcome;
    }
    private Outcome budget(BillingEventCodec.Event e, ServiceOrder order, String state,
                           BillingSagaGateway.CurrentBudget current, Long expectedVersion) {
        var p = e.payload();
        var budget = UUID.fromString(p.path("budgetId").asText());
        var revision = p.path("budgetVersion").intValue();
        var composition = p.path("compositionVersion").intValue();
        if (revision < order.getBudgetRevision()) {
            return Outcome.OBSOLETE;
        } else if (revision == order.getBudgetRevision() && order.getCurrentBudgetId() != null
                && (!budget.equals(order.getCurrentBudgetId()) || !Integer.valueOf(composition).equals(order.getBudgetCompositionVersion()))) {
            throw new OrderRuleException("BUDGET_COMPOSITION_CONFLICT");
        }
        if (Set.of("CANCELLED", "DELIVERED", "COMPLETED").contains(order.getStatus().name())
                || Set.of("COMPENSATING", "COMPENSATED", "COMPLETED").contains(state)
                || Boolean.TRUE.equals(jdbc.queryForObject("SELECT cancellation_in_progress FROM service_order_saga WHERE service_order_id=?", Boolean.class, order.getId()))) { return Outcome.IGNORED; }
        if (composition < order.getCompositionVersion()) { return Outcome.OBSOLETE; }
        boolean linked = budget.equals(order.getCurrentBudgetId()) && revision == order.getBudgetRevision();
        boolean sent = e.type().equals("OrcamentoEnviado");
        if (linked && sent) { return Outcome.IGNORED; }
        if (linked && (order.hasApprovedBudget() || order.getStatus().name().equals("REJECTED"))) {
            if (e.type().equals("OrcamentoAprovado") && order.hasApprovedBudget()
                    && order.getApprovedAmount().compareTo(new BigDecimal(p.path("approvedAmount").asText())) == 0
                    && order.getApprovedCurrency().equals(p.path("currency").asText())) { return Outcome.IGNORED; }
            if (e.type().equals("OrcamentoRejeitado") && order.getStatus().name().equals("REJECTED")) { return Outcome.IGNORED; }
            throw new OrderRuleException("BUDGET_DECISION_CONFLICT");
        }
        if (!Set.of("IN_DIAGNOSIS", "WAITING_APPROVAL", "WAITING_REVISION").contains(state)) {
            throw new OrderRuleException("SAGA_STATE_REQUIRES_RECONCILIATION");
        }
        if (!linked && !sent) {
            if (current == null) { throw new MissingContext(order.getVersion()); }
            if (!Objects.equals(expectedVersion, order.getVersion())) { throw new OrderRuleException("BUDGET_CONTEXT_CHANGED"); }
            if (current.budgetVersion() > revision) { return Outcome.OBSOLETE; }
            String decision = e.type().equals("OrcamentoAprovado") ? "APPROVED" : "REJECTED";
            if (!current.serviceOrderId().equals(order.getId()) || !current.budgetId().equals(budget)
                    || current.budgetVersion() != revision || current.compositionVersion() != composition || !decision.equals(current.status())
                    || (decision.equals("APPROVED") && (!p.path("approvedAmount").asText().equals(current.amount()) || !"BRL".equals(current.currency())))) {
                throw new OrderRuleException("BUDGET_CONTEXT_CONFLICT");
            }
        }
        if (!linked) {
            // Billing serializes commercial revisions. A newer SENT or CT-10-confirmed
            // decision can overtake the previous revision's rejection on Standard SQS.
            order.registerBudget(budget, revision, composition);
        }
        String next = "WAITING_APPROVAL";
        if (!sent) {
            if (composition != order.getCompositionVersion()) { throw new OrderRuleException("BUDGET_COMPOSITION_CONFLICT"); }
            if (e.type().equals("OrcamentoAprovado")) {
                order.approveBudget(budget, revision, order.compositionSnapshot(), new BigDecimal(p.path("approvedAmount").asText()), p.path("currency").asText());
                next = "READY_FOR_EXECUTION";
            } else { order.rejectBudget(); next = "WAITING_REVISION"; }
        }
        orders.save(order);
        jdbc.update("UPDATE service_order_saga SET state=?, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE service_order_id=?", next, e.orderId());
        return Outcome.APPLIED;
    }
    private Outcome payment(BillingEventCodec.Event e, ServiceOrder order) {
        var p = e.payload(); var id = UUID.fromString(p.path("paymentId").asText());
        int version = p.path("paymentVersion").intValue(); String status = p.path("paymentStatus").asText();
        var existing = jdbc.queryForList("SELECT * FROM service_order_payment WHERE payment_id=?", id);
        if (!existing.isEmpty()) {
            var old = existing.getFirst();
            if (!e.orderId().equals(old.get("service_order_id"))) { throw new OrderRuleException("PAYMENT_ORDER_CONFLICT"); }
            int previous = ((Number)old.get("payment_version")).intValue();
            if (version < previous) { return Outcome.OBSOLETE; }
            if (version == previous) {
                if (!status.equals(old.get("status"))) { throw new OrderRuleException("PAYMENT_VERSION_CONFLICT"); }
                return Outcome.IGNORED;
            }
        }
        jdbc.update("""
                INSERT INTO service_order_payment(payment_id,service_order_id,payment_version,status,occurred_at) VALUES (?,?,?,?,?)
                ON CONFLICT(payment_id) DO UPDATE SET payment_version=EXCLUDED.payment_version, status=EXCLUDED.status, occurred_at=EXCLUDED.occurred_at
                """, id, e.orderId(), version, status, Timestamp.from(e.occurredAt()));
        var paid = jdbc.queryForObject("SELECT max(occurred_at) FROM service_order_payment WHERE service_order_id=? AND status='PAID'", Timestamp.class, e.orderId());
        order.projectPayment(paid == null ? null : paid.toInstant().atOffset(java.time.ZoneOffset.UTC).toLocalDateTime());
        orders.save(order);
        jdbc.update("UPDATE service_order_saga SET settlement_pending=true, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE service_order_id=?", e.orderId());
        return Outcome.APPLIED;
    }
}
