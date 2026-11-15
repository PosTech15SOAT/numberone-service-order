package br.com.fiap.numberone.serviceorder.infrastructure.persistence.gateways;

import br.com.fiap.numberone.serviceorder.application.gateways.BillingSagaGateway;
import br.com.fiap.numberone.serviceorder.application.gateways.InventorySagaGateway;
import br.com.fiap.numberone.serviceorder.application.gateways.OrderCompletionStore.SagaState;
import br.com.fiap.numberone.serviceorder.application.gateways.SagaCompensationStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Repository
public class JdbcSagaCompensationStore implements SagaCompensationStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcSagaCompensationStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public CancellationState lockCancellation(UUID orderId) {
        return jdbc.queryForObject("""
                SELECT state, reason, cancellation::text, recovery_owner
                FROM service_order_saga WHERE service_order_id=? FOR UPDATE
                """, (row, index) -> {
            String cancellation = row.getString("cancellation");
            return new CancellationState(SagaState.valueOf(row.getString("state")), row.getString("reason"),
                    cancellation == null ? null : json.readValue(cancellation, Audit.class),
                    row.getObject("recovery_owner", UUID.class));
        }, orderId);
    }

    @Override
    public void registerCancellation(UUID orderId, Audit audit, String reversalReason) {
        jdbc.update("""
                UPDATE service_order_saga SET cancellation=CAST(? AS jsonb),cancellation_in_progress=true,
                return_state=state,state=?,reason=?,version=version+1,next_attempt_at=CURRENT_TIMESTAMP,
                updated_at=CURRENT_TIMESTAMP WHERE service_order_id=?
                """, json.writeValueAsString(audit), audit.automatic() ? "COMPENSATING" : "MANUAL_INTERVENTION",
                audit.automatic() ? null : "OUTSIDE_AUTOMATIC_WINDOW", orderId);
        if (!audit.automatic()) {
            return;
        }
        var originals = jdbc.queryForList("""
                SELECT operation_id FROM saga_operation WHERE service_order_id=? AND intent='START_ITEM'
                ORDER BY created_at,operation_id
                """, UUID.class, orderId);
        var evidence = new InventorySagaGateway.Evidence(audit.author(), audit.request().verifiedAt(),
                audit.request().evidenceReference());
        for (UUID original : originals) {
            UUID compensationId = UUID.randomUUID();
            var request = new InventorySagaGateway.Reversal(compensationId, reversalReason, true, evidence);
            jdbc.update("""
                    INSERT INTO saga_operation(operation_id,service_order_id,intent,state,original_operation_id,request)
                    VALUES (?,?,'RESOLVE_STOCK','PENDING',?,CAST(? AS jsonb))
                    """, compensationId, orderId, original, json.writeValueAsString(request));
        }
    }

    @Override
    public Status readStatus(UUID orderId) {
        var saga = jdbc.queryForMap("""
                SELECT state,reason,return_state,cancellation,review_decisions
                FROM service_order_saga WHERE service_order_id=? FOR UPDATE
                """, orderId);
        var operations = jdbc.queryForList("""
                SELECT operation_id,original_operation_id,budget_id,budget_revision,intent,state,attempts,next_attempt_at,
                    reason,result,late_result
                FROM saga_operation WHERE service_order_id=? ORDER BY created_at,operation_id
                """, orderId);
        for (var operation : operations) {
            for (String field : List.of("result", "late_result")) {
                if (operation.get(field) != null) {
                    operation.put(field, json.readTree(operation.get(field).toString()));
                }
            }
        }
        var audit = saga.get("cancellation");
        return new Status(orderId, (String) saga.get("state"), (String) saga.get("reason"),
                (String) saga.get("return_state"), audit == null ? null : json.readTree(audit.toString()),
                json.readTree(saga.get("review_decisions").toString()), operations);
    }

    @Override
    public void resumeCancellation(UUID orderId, ReviewDecision decision) {
        jdbc.update("""
                UPDATE service_order_saga SET review_decisions=review_decisions || CAST(? AS jsonb)
                WHERE service_order_id=?
                """, json.writeValueAsString(decision), orderId);
        jdbc.update("""
                UPDATE saga_operation SET state='PENDING',reason=NULL,next_attempt_at=CURRENT_TIMESTAMP
                WHERE service_order_id=? AND intent<>'START_ITEM' AND state='NEEDS_REVIEW'
                """, orderId);
        jdbc.update("""
                UPDATE service_order_saga SET state='COMPENSATING',reason=NULL,version=version+1,
                next_attempt_at=CURRENT_TIMESTAMP WHERE service_order_id=?
                """, orderId);
    }

    @Override
    public Optional<UUID> claimCancellation(UUID orderId) {
        UUID owner = UUID.randomUUID();
        int updated = jdbc.update("""
                UPDATE service_order_saga SET recovery_owner=?,recovery_until=CURRENT_TIMESTAMP+interval '30 seconds',
                recovery_attempts=recovery_attempts+1 WHERE service_order_id=? AND next_attempt_at<=CURRENT_TIMESTAMP
                AND (recovery_until IS NULL OR recovery_until<CURRENT_TIMESTAMP)
                """, owner, orderId);
        return updated == 1 ? Optional.of(owner) : Optional.empty();
    }

    @Override
    public Optional<ResolveStock> nextStockStep(UUID orderId) {
        var next = jdbc.query("""
                SELECT operation_id, original_operation_id, request FROM saga_operation
                WHERE service_order_id=? AND intent='RESOLVE_STOCK' AND state NOT IN ('SUCCEEDED','SKIPPED')
                ORDER BY created_at,operation_id LIMIT 1
                """, (row, index) -> new ResolveStock(row.getObject("operation_id", UUID.class),
                row.getObject("original_operation_id", UUID.class),
                json.readValue(row.getString("request"), InventorySagaGateway.Reversal.class)), orderId)
                .stream().findFirst();
        next.ifPresent(step -> recordDispatch(step.id()));
        return next;
    }

    @Override
    public BillingStep billingStep(UUID orderId, Supplier<BillingStep> create) {
        var step = jdbc.query("""
                SELECT operation_id, intent, request, budget_id, budget_revision FROM saga_operation
                WHERE service_order_id=? AND intent IN ('CANCEL_BUDGET','CLOSE_BUDGET_CONTEXT')
                """, (row, index) -> {
            var id = row.getObject("operation_id", UUID.class);
            var request = row.getString("request");
            return (BillingStep) switch (row.getString("intent")) {
                case "CANCEL_BUDGET" -> new CancelBudget(id, row.getObject("budget_id", UUID.class),
                        json.readValue(request, BillingSagaGateway.Cancellation.class));
                case "CLOSE_BUDGET_CONTEXT" -> new CloseBudgetContext(id, row.getInt("budget_revision"),
                        json.readValue(request, BillingSagaGateway.ContextCancellation.class));
                default -> throw new IllegalStateException("UNEXPECTED_BILLING_STEP");
            };
        }, orderId).stream().findFirst().orElseGet(() -> {
            var draft = create.get();
            saveBillingStep(orderId, draft);
            return draft;
        });
        recordDispatch(step.id());
        return step;
    }

    private void saveBillingStep(UUID orderId, BillingStep step) {
        String intent;
        UUID budgetId;
        int revision;
        String request;
        switch (step) {
            case CancelBudget cancel -> {
                intent = "CANCEL_BUDGET";
                budgetId = cancel.budgetId();
                revision = cancel.request().budgetVersion();
                request = json.writeValueAsString(cancel.request());
            }
            case CloseBudgetContext close -> {
                intent = "CLOSE_BUDGET_CONTEXT";
                budgetId = null;
                revision = close.budgetRevision();
                request = json.writeValueAsString(close.request());
            }
        }
        jdbc.update("""
                INSERT INTO saga_operation(operation_id,service_order_id,intent,state,request,budget_revision,budget_id)
                VALUES (?,?,?,'PENDING',CAST(? AS jsonb),?,?)
                """, step.id(), orderId, intent, request, revision, budgetId);
    }

    private void recordDispatch(UUID operationId) {
        jdbc.update("UPDATE saga_operation SET attempts=attempts+1,updated_at=CURRENT_TIMESTAMP WHERE operation_id=?",
                operationId);
    }

    @Override
    public void recordStockResolution(UUID orderId, UUID owner, ResolveStock step, InventorySagaGateway.InventoryOperation result) {
        recordSuccess(step.id(), json.writeValueAsString(result));
        jdbc.update("""
                UPDATE saga_operation SET state=CASE WHEN state='REJECTED' THEN state ELSE 'SKIPPED' END,
                reason='CANCELLATION_STOCK_RESOLVED',updated_at=CURRENT_TIMESTAMP WHERE operation_id=?
                """, step.originalId());
        release(orderId, owner, 0);
    }

    @Override
    public boolean completeBilling(UUID orderId, UUID owner, BillingStep step, BillingSagaGateway.CancellationResult result) {
        recordSuccess(step.id(), json.writeValueAsString(result));
        boolean pending = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM saga_operation WHERE service_order_id=?
                AND state NOT IN ('SUCCEEDED','SKIPPED','REJECTED'))
                """, Boolean.class, orderId);
        if (pending) {
            return false;
        }
        jdbc.update("""
                UPDATE service_order_saga SET state='COMPENSATED',reason=NULL,version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE service_order_id=?
                """, orderId);
        release(orderId, owner, 0);
        return true;
    }

    private void recordSuccess(UUID operationId, String result) {
        jdbc.update("""
                UPDATE saga_operation SET state='SUCCEEDED',result=CAST(? AS jsonb),reason=NULL,
                updated_at=CURRENT_TIMESTAMP WHERE operation_id=?
                """, result, operationId);
    }

    @Override
    public void markForReview(UUID orderId, UUID operationId, String reason) {
        if (operationId != null) {
            jdbc.update("""
                    UPDATE saga_operation SET state='NEEDS_REVIEW',reason=?,updated_at=CURRENT_TIMESTAMP WHERE operation_id=?
                    """, reason, operationId);
        }
        jdbc.update("""
                UPDATE service_order_saga SET return_state=state,state='MANUAL_INTERVENTION',reason=?,version=version+1,
                recovery_owner=NULL,recovery_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE service_order_id=?
                """, reason, orderId);
    }

    @Override
    public void deferStep(UUID orderId, UUID owner, UUID operationId) {
        jdbc.update("""
                UPDATE saga_operation SET state='AWAITING_RESULT',reason='RESULT_UNKNOWN',
                next_attempt_at=CURRENT_TIMESTAMP+interval '5 seconds',updated_at=CURRENT_TIMESTAMP WHERE operation_id=?
                """, operationId);
        release(orderId, owner, 5);
    }

    private void release(UUID orderId, UUID owner, int delaySeconds) {
        jdbc.update("""
                UPDATE service_order_saga SET recovery_owner=NULL,recovery_until=NULL,
                next_attempt_at=CURRENT_TIMESTAMP+?*interval '1 second' WHERE service_order_id=? AND recovery_owner=?
                """, delaySeconds, orderId, owner);
    }
}
