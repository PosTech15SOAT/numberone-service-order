package br.com.fiap.numberone.serviceorder.application.services;

import br.com.fiap.numberone.serviceorder.application.gateways.*;
import br.com.fiap.numberone.serviceorder.domain.entities.*;
import br.com.fiap.numberone.serviceorder.domain.enums.ServiceOrderStatus;
import br.com.fiap.numberone.serviceorder.domain.exceptions.*;
import br.com.fiap.numberone.serviceorder.domain.valueobjects.MaintenanceSnapshot;
import br.com.fiap.numberone.shared.application.gateways.BranchScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

/** Each remote call is bracketed by independent committed local transactions. */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class SagaWorkflow {
    private final ServiceOrderGateway orders;
    private final JdbcTemplate jdbc;
    private final SagaParticipants participants;
    private final BranchScope branch;
    private final ObjectMapper json;
    private final MaintenanceHistoryGateway history;
    private final TransactionTemplate tx;
    public SagaWorkflow(ServiceOrderGateway orders, JdbcTemplate jdbc, SagaParticipants participants,
                        BranchScope branch, ObjectMapper json, PlatformTransactionManager manager,
                        MaintenanceHistoryGateway history) {
        this.orders = orders; this.jdbc = jdbc; this.participants = participants; this.branch = branch; this.json = json;
        this.history = history;
        tx = new TransactionTemplate(manager); tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(30);
    }
    public record StartResult(ServiceOrder order, UUID itemId, UUID operationId, String state, String reason) { }
    private record Attempt(UUID id, UUID order, UUID item, int composition, int budget, String state,
                           SagaParticipants.Consumption request, String reason) { }
    private Attempt attempt(UUID id) {
        return jdbc.queryForObject("SELECT * FROM saga_operation WHERE operation_id=?", (r, n) -> new Attempt(
                r.getObject("operation_id", UUID.class), r.getObject("service_order_id", UUID.class),
                r.getObject("item_id", UUID.class), r.getInt("composition_version"), r.getInt("budget_revision"),
                r.getString("state"), json.readValue(r.getString("request"), SagaParticipants.Consumption.class), r.getString("reason")), id);
    }
    private ServiceOrder load(UUID id) { return orders.find(id, branch.id(), true).orElseThrow(OrderNotFoundException::new); }
    private Map<String, Object> saga(UUID id) {
        return jdbc.queryForMap("SELECT * FROM service_order_saga WHERE service_order_id=? FOR UPDATE", id);
    }
    private void executionGuard(ServiceOrder o, Map<String, Object> s) {
        o.requestStart();
        if (Boolean.TRUE.equals(s.get("cancellation_in_progress"))) { throw new OrderRuleException("CANCELLATION_IN_PROGRESS"); }
        if (!Set.of("READY_FOR_EXECUTION", "START_PENDING", "IN_EXECUTION").contains(s.get("state"))) {
            throw new OrderRuleException("SAGA_NOT_READY_FOR_EXECUTION");
        }
    }
    private void state(UUID id, String value) {
        jdbc.update("UPDATE service_order_saga SET state=?, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE service_order_id=?", value, id);
    }
    public ServiceOrder readiness(UUID id) {
        return tx.execute(t -> { var o = load(id); executionGuard(o, saga(id)); return o; });
    }
    public StartResult start(UUID itemId, UUID requestedId, UUID responsible, UUID correlation) {
        var prepared = tx.execute(t -> {
            // Global key lock precedes parent lock, matching inbox's lock hierarchy.
            if (requestedId != null) { jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", requestedId.toString()); }
            var o = orders.findByItem(itemId, branch.id(), true).orElseThrow(OrderNotFoundException::new);
            var s = saga(o.getId());
            var item = o.item(itemId);
            if (Boolean.TRUE.equals(s.get("cancellation_in_progress"))) { throw new OrderRuleException("CANCELLATION_IN_PROGRESS"); }
            if (requestedId != null) {
                var found = jdbc.queryForList("SELECT operation_id FROM saga_operation WHERE operation_id=?", UUID.class, requestedId);
                if (!found.isEmpty()) {
                    if (!"START_ITEM".equals(jdbc.queryForObject("SELECT intent FROM saga_operation WHERE operation_id=?",String.class,requestedId))) {
                        throw new OrderRuleException("IDEMPOTENCY_CONFLICT");
                    }
                    var a = attempt(requestedId);
                    if (!a.item().equals(itemId) || a.composition() != o.getCompositionVersion()) {
                        throw new OrderRuleException("IDEMPOTENCY_CONFLICT");
                    }
                    if (a.state().equals("CONFIRMED") || a.state().equals("REJECTED")) { return a; }
                }
            }
            if (item.getStartOperationId() != null) {
                var last = attempt(item.getStartOperationId());
                if (Set.of("START_PENDING", "AWAITING_RESULT").contains(last.state())) {
                    executionGuard(o, s); return last; // A different key cannot supersede an active attempt.
                }
                if (last.state().equals("CONFIRMED") || requestedId == null) { return last; }
            }
            executionGuard(o, s);
            UUID operation = requestedId == null ? UUID.randomUUID() : requestedId;
            var supplies = o.getApprovedComposition().items().stream().filter(i -> i.serviceOrderItemId().equals(itemId))
                    .findFirst().orElseThrow().supplies();
            var request = new SagaParticipants.Consumption(operation, o.getId(), itemId, responsible, supplies);
            int number = jdbc.queryForObject("SELECT COALESCE(MAX(attempt),0)+1 FROM saga_operation WHERE item_id=?", Integer.class, itemId);
            jdbc.update("""
                    INSERT INTO saga_operation(operation_id,service_order_id,item_id,composition_version,budget_revision,attempt,state,request)
                    VALUES (?,?,?,?,?,?,'START_PENDING',CAST(? AS jsonb))
                    """, operation, o.getId(), itemId, o.getCompositionVersion(), o.getBudgetRevision(), number, json.writeValueAsString(request));
            item.requestStart(operation);
            if (supplies.isEmpty()) {
                o.confirmItemStart(itemId, now());
                jdbc.update("UPDATE saga_operation SET state='CONFIRMED', reason='NO_MATERIALS' WHERE operation_id=?", operation);
                state(o.getId(), "IN_EXECUTION");
            } else if (!s.get("state").equals("IN_EXECUTION")) { state(o.getId(), "START_PENDING"); }
            orders.save(o);
            return attempt(operation);
        });
        if (Set.of("CONFIRMED", "REJECTED").contains(prepared.state())) { return result(prepared); }
        dispatchStart(prepared.id(), correlation);
        return result(prepared);
    }
    private ServiceOrder loadInternal(UUID id) {
        var branchId = jdbc.queryForObject("SELECT branch_id FROM ordem_servico WHERE id=?", UUID.class, id);
        return orders.find(id, branchId, true).orElseThrow(OrderNotFoundException::new);
    }
    /** Lease and guards are shared by synchronous requests and restart recovery. */
    public void dispatchStart(UUID operation, UUID correlation) {
        dispatchStart(operation, correlation, false);
    }
    public void recoverStart(UUID operation, UUID correlation) {
        dispatchStart(operation, correlation, true);
    }
    private void dispatchStart(UUID operation, UUID correlation, boolean recovering) {
        UUID owner = UUID.randomUUID();
        var prepared = tx.execute(t -> {
            var a = attempt(operation); var o = loadInternal(a.order()); var s = saga(o.getId());
            if (!Set.of("START_PENDING", "AWAITING_RESULT").contains(a.state())) { return null; }
            try { executionGuard(o, s); }
            catch (OrderRuleException ex) { return null; }
            if (o.getCompositionVersion() != a.composition() || o.getBudgetRevision() != a.budget()
                    || !a.id().equals(o.item(a.item()).getStartOperationId())) {
                startReview(a,"LOCAL_GUARDS_CHANGED"); return null;
            }
            int claimed = jdbc.update("""
                    UPDATE saga_operation SET lease_owner=?,lease_until=CURRENT_TIMESTAMP+interval '30 seconds',
                    attempts=attempts+1,updated_at=CURRENT_TIMESTAMP WHERE operation_id=?
                    AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)
                    """, owner, operation);
            return claimed == 1 ? a : null;
        });
        if (prepared == null) { return; }
        try {
            SagaParticipants.ConsumptionResult remote = null;
            int attempts = jdbc.queryForObject("SELECT attempts FROM saga_operation WHERE operation_id=?", Integer.class, operation);
            if (recovering || attempts > 1) {
                var known = participants.inventoryOperation(operation, correlation);
                if (Set.of("CONSUMED", "REJECTED").contains(known.result())) {
                    remote = new SagaParticipants.ConsumptionResult(operation, known.result(), known.movements(), known.code());
                } else if (!"UNKNOWN".equals(known.result())) {
                    if (Set.of("CLOSED", "REVERSED").contains(known.result())) {
                        tx.executeWithoutResult(t -> {
                            loadInternal(prepared.order()); var s = saga(prepared.order());
                            if (!Boolean.TRUE.equals(s.get("cancellation_in_progress"))
                                    && owner.equals(jdbc.queryForObject("SELECT lease_owner FROM saga_operation WHERE operation_id=?",UUID.class,operation))
                                    && Set.of("START_PENDING","AWAITING_RESULT").contains(attempt(operation).state())) {
                                jdbc.update("UPDATE saga_operation SET state='NEEDS_REVIEW',result=CAST(? AS jsonb),reason='UNEXPECTED_STOCK_CLOSURE' WHERE operation_id=?", json.writeValueAsString(known), operation);
                                jdbc.update("UPDATE service_order_saga SET return_state=state,state='MANUAL_INTERVENTION',reason='UNEXPECTED_STOCK_CLOSURE',version=version+1 WHERE service_order_id=?", prepared.order());
                            }
                        });
                    }
                    return;
                }
            }
            if (remote == null) {
                // Recheck after CT-08. Cancellation never schedules a new CT-05 retry.
                boolean active = tx.execute(t -> {
                    var o = loadInternal(prepared.order()); var s = saga(o.getId());
                    try { executionGuard(o,s); } catch (OrderRuleException ex) { return false; }
                    return owner.equals(jdbc.queryForObject("SELECT lease_owner FROM saga_operation WHERE operation_id=?", UUID.class, operation));
                });
                if (!active) { return; }
                remote = participants.consume(prepared.request(), correlation);
            }
            applyStartResult(prepared, remote, owner);
        } catch (SagaDependencyException ex) {
            // A technical failure is not a refusal and never authorizes another identity.
        } finally {
            tx.executeWithoutResult(t -> {
                loadInternal(prepared.order()); saga(prepared.order());
                jdbc.update("""
                        UPDATE saga_operation SET state=CASE WHEN state='START_PENDING' THEN 'AWAITING_RESULT' ELSE state END,
                        reason=CASE WHEN state IN ('START_PENDING','AWAITING_RESULT') AND reason IS NULL THEN 'RESULT_UNKNOWN' ELSE reason END,
                        next_attempt_at=CURRENT_TIMESTAMP + LEAST(60,5*attempts)*interval '1 second',
                        lease_owner=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE operation_id=? AND lease_owner=?
                        """, operation, owner);
            });
        }
    }
    private void applyStartResult(Attempt prepared, SagaParticipants.ConsumptionResult remote, UUID owner) {
        tx.executeWithoutResult(t -> {
            var o = loadInternal(prepared.order()); var s = saga(o.getId()); var a = attempt(prepared.id());
            if (Boolean.TRUE.equals(s.get("cancellation_in_progress"))) {
                jdbc.update("UPDATE saga_operation SET late_result=CAST(? AS jsonb),updated_at=CURRENT_TIMESTAMP WHERE operation_id=?", json.writeValueAsString(remote), a.id());
                return;
            }
            if (Set.of("CONFIRMED", "REJECTED", "SKIPPED", "NEEDS_REVIEW").contains(a.state())) { return; }
            if (!owner.equals(jdbc.queryForObject("SELECT lease_owner FROM saga_operation WHERE operation_id=?", UUID.class, a.id()))) { return; }
            // Preserve the remote fact even when a concurrent local guard invalidates confirmation.
            jdbc.update("UPDATE saga_operation SET result=CAST(? AS jsonb), updated_at=CURRENT_TIMESTAMP WHERE operation_id=?", json.writeValueAsString(remote), a.id());
            try {
                executionGuard(o, s);
                if (o.getCompositionVersion() != a.composition() || o.getBudgetRevision() != a.budget()
                        || !a.id().equals(o.item(a.item()).getStartOperationId()) || !o.item(a.item()).isStartPending()) {
                    throw new OrderRuleException("START_VERSION_CHANGED");
                }
            } catch (OrderRuleException ex) {
                startReview(a,"LOCAL_GUARDS_CHANGED");
                return;
            }
            if ("CONSUMED".equals(remote.result())) {
                o.item(a.item()).getMaterials().forEach(m -> m.confirmConsumption(a.id(), remote.movements()));
                o.confirmItemStart(a.item(), now());
                jdbc.update("UPDATE saga_operation SET state='CONFIRMED', reason=NULL WHERE operation_id=?", a.id());
                state(o.getId(), "IN_EXECUTION");
            } else {
                o.item(a.item()).insufficientStock();
                jdbc.update("UPDATE saga_operation SET state='REJECTED', reason='INSUFFICIENT_STOCK' WHERE operation_id=?", a.id());
                int active = jdbc.queryForObject("SELECT count(*) FROM saga_operation WHERE service_order_id=? AND state IN ('START_PENDING','AWAITING_RESULT')", Integer.class, o.getId());
                if (!s.get("state").equals("IN_EXECUTION") && active == 0) { state(o.getId(), "READY_FOR_EXECUTION"); }
            }
            orders.save(o);
        });
    }
    private void startReview(Attempt a,String reason) {
        jdbc.update("UPDATE saga_operation SET state='NEEDS_REVIEW',reason=?,updated_at=CURRENT_TIMESTAMP WHERE operation_id=?",reason,a.id());
        jdbc.update("UPDATE service_order_saga SET return_state=state,state='MANUAL_INTERVENTION',reason=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE service_order_id=?",reason,a.order());
    }
    private StartResult result(Attempt a) {
        return tx.execute(t -> { var o = load(a.order()); var current = attempt(a.id());
            return new StartResult(o, a.item(), a.id(), current.state(), current.reason()); });
    }
    public ServiceOrder complete(UUID id, Integer odometerKm) {
        return tx.execute(t -> {
            var o = load(id); var s = saga(id); executionGuard(o, s);
            if (!s.get("state").equals("IN_EXECUTION")) { throw new OrderRuleException("ORDER_NOT_IN_PROGRESS"); }
            if (jdbc.queryForObject("SELECT count(*) FROM saga_operation WHERE service_order_id=? AND state IN ('START_PENDING','AWAITING_RESULT')", Integer.class, id) != 0) {
                throw new OrderRuleException("MATERIAL_CONSUMPTION_PENDING");
            }
            o.complete();
            var saved = orders.save(o);
            state(id, "WAITING_PAYMENT");
            history.append(MaintenanceSnapshot.capture(o, Instant.now(), odometerKm));
            return saved;
        });
    }
    private record DeliverySnapshot(ServiceOrder order, long sagaVersion) { }
    private void deliveryGuard(ServiceOrder o, Map<String,Object> s) {
        o.requestDelivery();
        if (Boolean.TRUE.equals(s.get("cancellation_in_progress"))
                || !Set.of("WAITING_PAYMENT", "READY_FOR_DELIVERY").contains(s.get("state"))) {
            throw new OrderRuleException("DELIVERY_GUARDS_CHANGED");
        }
        if (jdbc.queryForObject("SELECT count(*) FROM saga_operation WHERE service_order_id=? AND state IN ('START_PENDING','AWAITING_RESULT')", Integer.class, o.getId()) != 0) {
            throw new OrderRuleException("MATERIAL_CONSUMPTION_PENDING");
        }
    }
    public ServiceOrder deliver(UUID id, UUID correlation) {
        var before = tx.execute(t -> {
            var o = load(id); var s = saga(id);
            if (o.getStatus() != ServiceOrderStatus.DELIVERED) {
                deliveryGuard(o, s);
                jdbc.update("UPDATE service_order_saga SET settlement_pending=true WHERE service_order_id=?", id);
            }
            return new DeliverySnapshot(o, ((Number)s.get("version")).longValue());
        });
        if (before.order().getStatus() == ServiceOrderStatus.DELIVERED) { return before.order(); }
        var settlement = participants.settlement(id, correlation); // No event/projection precondition.
        var delivered = tx.execute(t -> {
            var o = load(id); var s = saga(id);
            if (o.getStatus() == ServiceOrderStatus.DELIVERED) { return o; }
            deliveryGuard(o, s);
            if (!Objects.equals(o.getVersion(), before.order().getVersion())
                    || ((Number)s.get("version")).longValue() != before.sagaVersion()) { throw new OrderRuleException("DELIVERY_GUARDS_CHANGED"); }
            if (!settlement.settled()) {
                state(id, "WAITING_PAYMENT");
                jdbc.update("UPDATE service_order_saga SET settlement_pending=false, settlement_verified_at=? WHERE service_order_id=?", java.sql.Timestamp.from(settlement.verifiedAt()), id);
                return null;
            }
            o.confirmDelivery(now()); var saved = orders.save(o); state(id, "COMPLETED");
            jdbc.update("UPDATE service_order_saga SET settlement_pending=false, settlement_verified_at=? WHERE service_order_id=?", java.sql.Timestamp.from(settlement.verifiedAt()), id);
            return saved;
        });
        if (delivered == null) { throw new OrderRuleException("NOT_SETTLED"); }
        return delivered;
    }
    /** Financial events request this refresh; a failure leaves durable pending verification. */
    public void refreshSettlement(UUID id, UUID correlation) {
        var version = tx.execute(t -> {
            var branchId = jdbc.queryForObject("SELECT branch_id FROM ordem_servico WHERE id=?", UUID.class, id);
            var o = orders.find(id, branchId, true).orElseThrow(); var s = saga(id);
            return o.getStatus() == ServiceOrderStatus.COMPLETED && Boolean.TRUE.equals(s.get("settlement_pending"))
                    && Set.of("WAITING_PAYMENT", "READY_FOR_DELIVERY").contains(s.get("state")) && !Boolean.TRUE.equals(s.get("cancellation_in_progress"))
                    ? ((Number)s.get("version")).longValue() : null;
        });
        if (version == null) { return; }
        SagaParticipants.Settlement result;
        try { result = participants.settlement(id, correlation); } catch (SagaDependencyException ex) { return; }
        tx.executeWithoutResult(t -> {
            var branchId = jdbc.queryForObject("SELECT branch_id FROM ordem_servico WHERE id=?", UUID.class, id);
            var o = orders.find(id, branchId, true).orElseThrow(); var s = saga(id);
            if (((Number)s.get("version")).longValue() != version || o.getStatus() != ServiceOrderStatus.COMPLETED) { return; }
            try { deliveryGuard(o, s); } catch (OrderRuleException ex) { return; }
            state(id, result.settled() ? "READY_FOR_DELIVERY" : "WAITING_PAYMENT");
            jdbc.update("UPDATE service_order_saga SET settlement_pending=false, settlement_verified_at=? WHERE service_order_id=?", java.sql.Timestamp.from(result.verifiedAt()), id);
        });
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}
