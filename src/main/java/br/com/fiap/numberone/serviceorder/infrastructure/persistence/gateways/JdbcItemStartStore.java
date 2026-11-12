package br.com.fiap.numberone.serviceorder.infrastructure.persistence.gateways;

import br.com.fiap.numberone.serviceorder.application.gateways.ItemStartStore;
import br.com.fiap.numberone.serviceorder.application.gateways.OrderCompletionStore;
import br.com.fiap.numberone.serviceorder.application.gateways.SagaParticipants;
import br.com.fiap.numberone.serviceorder.application.gateways.ServiceOrderGateway;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcItemStartStore implements ItemStartStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ServiceOrderGateway orders;
    private final OrderCompletionStore sagas;

    public JdbcItemStartStore(JdbcTemplate jdbc, ObjectMapper json, ServiceOrderGateway orders, OrderCompletionStore sagas) {
        this.jdbc = jdbc;
        this.json = json;
        this.orders = orders;
        this.sagas = sagas;
    }

    @Override
    public StartRequest loadForRequest(UUID itemId, UUID branchId, UUID requestedId) {
        // Keep the operation key, order, saga lock order shared with the inbox.
        if (requestedId != null) {
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", requestedId.toString());
        }
        var order = orders.findByItem(itemId, branchId, true).orElseThrow(OrderNotFoundException::new);
        var saga = sagas.lockSaga(order.getId());
        if (saga.cancellationInProgress()) {
            return new StartRequest(order, saga, null, null, null);
        }
        String intent = requestedId == null ? null : jdbc.queryForList(
                "SELECT intent FROM saga_operation WHERE operation_id=?", String.class, requestedId)
                .stream().findFirst().orElse(null);
        var requested = "START_ITEM".equals(intent) ? readAttempt(requestedId) : null;
        UUID lastId = order.item(itemId).getStartOperationId();
        var last = lastId == null ? null : lastId.equals(requestedId) && requested != null ? requested : readAttempt(lastId);
        return new StartRequest(order, saga, intent, requested, last);
    }

    @Override
    public DispatchContext loadForDispatch(UUID operationId) {
        UUID orderId = jdbc.queryForObject("SELECT service_order_id FROM saga_operation WHERE operation_id=?", UUID.class, operationId);
        var order = sagas.findInternalOrderForUpdate(orderId).orElseThrow(OrderNotFoundException::new);
        var saga = sagas.lockSaga(orderId);
        return new DispatchContext(order, saga, readAttempt(operationId),
                jdbc.queryForObject("SELECT lease_owner FROM saga_operation WHERE operation_id=?", UUID.class, operationId));
    }

    @Override
    public StartAttempt readAttempt(UUID operationId) {
        return jdbc.queryForObject("""
                SELECT operation_id, service_order_id, item_id, composition_version, budget_revision, state, request, reason
                FROM saga_operation WHERE operation_id=?
                """, (row, index) -> new StartAttempt(row.getObject("operation_id", UUID.class),
                row.getObject("service_order_id", UUID.class), row.getObject("item_id", UUID.class),
                row.getInt("composition_version"), row.getInt("budget_revision"),
                StartState.valueOf(row.getString("state")),
                json.readValue(row.getString("request"), SagaParticipants.Consumption.class), row.getString("reason")),
                operationId);
    }

    @Override
    public StartAttempt registerStart(SagaParticipants.Consumption request, int compositionVersion, int budgetRevision,
                                      StartOutcome outcome) {
        int number = jdbc.queryForObject(
                "SELECT COALESCE(MAX(attempt),0)+1 FROM saga_operation WHERE item_id=?",
                Integer.class, request.serviceOrderItemId());
        jdbc.update("""
                INSERT INTO saga_operation(operation_id,service_order_id,item_id,composition_version,budget_revision,attempt,state,request)
                VALUES (?,?,?,?,?,?,'START_PENDING',CAST(? AS jsonb))
                """, request.operationId(), request.serviceOrderId(), request.serviceOrderItemId(),
                compositionVersion, budgetRevision, number, json.writeValueAsString(request));
        var attempt = readAttempt(request.operationId());
        saveOutcome(attempt, outcome);
        return outcome.state() == StartState.START_PENDING ? attempt : readAttempt(attempt.id());
    }

    @Override
    public Optional<DispatchTicket> claimDispatch(StartAttempt attempt) {
        UUID owner = UUID.randomUUID();
        return jdbc.query("""
                UPDATE saga_operation SET lease_owner=?,lease_until=CURRENT_TIMESTAMP+interval '30 seconds',
                attempts=attempts+1,updated_at=CURRENT_TIMESTAMP WHERE operation_id=?
                AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)
                RETURNING attempts
                """, (row, index) -> new DispatchTicket(attempt, owner, row.getInt("attempts") > 1),
                owner, attempt.id()).stream().findFirst();
    }

    @Override
    public void finishDispatch(DispatchTicket ticket) {
        loadForDispatch(ticket.attempt().id());
        jdbc.update("""
                UPDATE saga_operation SET state=CASE WHEN state='START_PENDING' THEN 'AWAITING_RESULT' ELSE state END,
                reason=CASE WHEN state IN ('START_PENDING','AWAITING_RESULT') AND reason IS NULL THEN 'RESULT_UNKNOWN' ELSE reason END,
                next_attempt_at=CURRENT_TIMESTAMP + LEAST(60,5*attempts)*interval '1 second',
                lease_owner=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE operation_id=? AND lease_owner=?
                """, ticket.attempt().id(), ticket.owner());
    }

    @Override
    public boolean hasOtherPendingStarts(UUID orderId, UUID operationId) {
        return jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM saga_operation
                WHERE service_order_id=? AND operation_id<>? AND state IN ('START_PENDING','AWAITING_RESULT'))
                """, Boolean.class, orderId, operationId);
    }

    @Override
    public void saveOutcome(StartAttempt attempt, StartOutcome outcome) {
        if (outcome.inventoryResult() != null) {
            jdbc.update("UPDATE saga_operation SET result=CAST(? AS jsonb) WHERE operation_id=?",
                    json.writeValueAsString(outcome.inventoryResult()), attempt.id());
        }
        if (outcome.state() != StartState.START_PENDING) {
            boolean updateTime = outcome.inventoryResult() instanceof SagaParticipants.ConsumptionResult
                    || (outcome.state() == StartState.NEEDS_REVIEW && outcome.inventoryResult() == null);
            jdbc.update("""
                    UPDATE saga_operation SET state=?,reason=?,
                    updated_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE updated_at END WHERE operation_id=?
                    """, outcome.state().name(), outcome.reason(), updateTime, attempt.id());
        }
        if (outcome.nextSagaState() == OrderCompletionStore.SagaState.MANUAL_INTERVENTION) {
            jdbc.update("""
                    UPDATE service_order_saga SET return_state=state,state='MANUAL_INTERVENTION',reason=?,version=version+1
                    WHERE service_order_id=?
                    """, outcome.reason(), attempt.orderId());
        } else if (outcome.nextSagaState() != null) {
            sagas.updateSagaState(attempt.orderId(), outcome.nextSagaState());
        }
    }

    @Override
    public void saveLateResult(UUID operationId, SagaParticipants.ConsumptionResult result) {
        jdbc.update("UPDATE saga_operation SET late_result=CAST(? AS jsonb),updated_at=CURRENT_TIMESTAMP WHERE operation_id=?",
                json.writeValueAsString(result), operationId);
    }
}
