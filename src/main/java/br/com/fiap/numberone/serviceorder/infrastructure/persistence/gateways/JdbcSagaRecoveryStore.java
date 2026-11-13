package br.com.fiap.numberone.serviceorder.infrastructure.persistence.gateways;

import br.com.fiap.numberone.serviceorder.application.gateways.SagaRecoveryStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcSagaRecoveryStore implements SagaRecoveryStore {
    private final JdbcTemplate jdbc;

    public JdbcSagaRecoveryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> dueCompensations() {
        return jdbc.queryForList("""
                SELECT service_order_id FROM service_order_saga
                WHERE state='COMPENSATING' AND next_attempt_at<=CURRENT_TIMESTAMP
                  AND (recovery_until IS NULL OR recovery_until<CURRENT_TIMESTAMP)
                ORDER BY next_attempt_at LIMIT 50
                """, UUID.class);
    }

    @Override
    public List<UUID> dueStarts() {
        return jdbc.queryForList("""
                SELECT p.operation_id FROM saga_operation p JOIN service_order_saga s USING(service_order_id)
                WHERE p.intent='START_ITEM' AND p.state IN ('START_PENDING','AWAITING_RESULT')
                  AND p.next_attempt_at<=CURRENT_TIMESTAMP AND (p.lease_until IS NULL OR p.lease_until<CURRENT_TIMESTAMP)
                  AND NOT s.cancellation_in_progress AND s.state IN ('READY_FOR_EXECUTION','START_PENDING','IN_EXECUTION')
                ORDER BY p.next_attempt_at LIMIT 50
                """, UUID.class);
    }

    @Override
    public List<UUID> dueSettlementChecks() {
        return jdbc.queryForList("""
                SELECT service_order_id FROM service_order_saga WHERE settlement_pending AND NOT cancellation_in_progress
                  AND state IN ('WAITING_PAYMENT','READY_FOR_DELIVERY') AND next_attempt_at<=CURRENT_TIMESTAMP
                  AND (recovery_until IS NULL OR recovery_until<CURRENT_TIMESTAMP)
                ORDER BY next_attempt_at LIMIT 50
                """, UUID.class);
    }

    @Override
    public Optional<UUID> claimSettlementCheck(UUID orderId) {
        UUID owner = UUID.randomUUID();
        int updated = jdbc.update("""
                UPDATE service_order_saga SET recovery_owner=?,recovery_until=CURRENT_TIMESTAMP+interval '30 seconds'
                WHERE service_order_id=? AND (recovery_until IS NULL OR recovery_until<CURRENT_TIMESTAMP)
                """, owner, orderId);
        return updated == 1 ? Optional.of(owner) : Optional.empty();
    }

    @Override
    public void finishSettlementCheck(UUID orderId, UUID owner) {
        jdbc.update("""
                UPDATE service_order_saga SET recovery_owner=NULL,recovery_until=NULL,
                next_attempt_at=CURRENT_TIMESTAMP+interval '5 seconds'
                WHERE service_order_id=? AND recovery_owner=?
                """, orderId, owner);
    }
}
