package br.com.fiap.numberone.serviceorder.infrastructure.persistence.gateways;

import br.com.fiap.numberone.serviceorder.application.gateways.OrderCompletionStore;
import br.com.fiap.numberone.serviceorder.application.gateways.ServiceOrderGateway;
import br.com.fiap.numberone.serviceorder.domain.entities.ServiceOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcOrderCompletionStore implements OrderCompletionStore {
    private final JdbcTemplate jdbc;

    private final ServiceOrderGateway orders;

    public JdbcOrderCompletionStore(JdbcTemplate jdbc, ServiceOrderGateway orders) {
        this.jdbc = jdbc;
        this.orders = orders;
    }

    @Override
    public SagaSnapshot lockSaga(UUID orderId) {
        return jdbc.queryForObject("""
                SELECT state, version, cancellation_in_progress, settlement_pending
                FROM service_order_saga WHERE service_order_id=? FOR UPDATE
                """, (row, index) -> new SagaSnapshot(SagaState.valueOf(row.getString("state")),
                row.getLong("version"), row.getBoolean("cancellation_in_progress"), row.getBoolean("settlement_pending")),
                orderId);
    }

    @Override
    public Optional<ServiceOrder> findInternalOrderForUpdate(UUID orderId) {
        UUID branchId = jdbc.queryForObject("SELECT branch_id FROM ordem_servico WHERE id=?", UUID.class, orderId);
        return orders.find(orderId, branchId, true);
    }

    @Override
    public void updateSagaState(UUID orderId, SagaState state) {
        jdbc.update("""
                UPDATE service_order_saga SET state=?, version=version+1, updated_at=CURRENT_TIMESTAMP
                WHERE service_order_id=?
                """, state.name(), orderId);
    }

    @Override
    public boolean hasPendingStarts(UUID orderId) {
        return jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM saga_operation
                WHERE service_order_id=? AND state IN ('START_PENDING','AWAITING_RESULT'))
                """, Boolean.class, orderId);
    }

    @Override
    public void markSettlementPending(UUID orderId) {
        jdbc.update("UPDATE service_order_saga SET settlement_pending=true WHERE service_order_id=?", orderId);
    }

    @Override
    public void recordSettlementVerification(UUID orderId, SagaState state, Instant verifiedAt) {
        updateSagaState(orderId, state);
        jdbc.update("""
                UPDATE service_order_saga SET settlement_pending=false, settlement_verified_at=? WHERE service_order_id=?
                """, Timestamp.from(verifiedAt), orderId);
    }
}
