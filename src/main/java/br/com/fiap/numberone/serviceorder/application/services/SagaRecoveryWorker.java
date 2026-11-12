package br.com.fiap.numberone.serviceorder.application.services;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;

@Component
public class SagaRecoveryWorker {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(SagaRecoveryWorker.class);
    private final JdbcTemplate jdbc;
    private final SagaCompensation compensation;
    private final StartServiceOrderItem startItem;
    private final FinishServiceOrder finishOrder;
    public SagaRecoveryWorker(JdbcTemplate jdbc,SagaCompensation compensation,StartServiceOrderItem startItem,
                              FinishServiceOrder finishOrder) {
        this.jdbc=jdbc; this.compensation=compensation; this.startItem=startItem; this.finishOrder=finishOrder;
    }
    public void tick() {
        for (UUID id:jdbc.queryForList("SELECT service_order_id FROM service_order_saga WHERE state='COMPENSATING' AND next_attempt_at<=CURRENT_TIMESTAMP AND (recovery_until IS NULL OR recovery_until<CURRENT_TIMESTAMP) ORDER BY next_attempt_at LIMIT 50",UUID.class)) {
            safely(id,() -> compensation.recover(id));
        }
        for (UUID id:jdbc.queryForList("""
                SELECT p.operation_id FROM saga_operation p JOIN service_order_saga s USING(service_order_id)
                WHERE p.intent='START_ITEM' AND p.state IN ('START_PENDING','AWAITING_RESULT')
                AND p.next_attempt_at<=CURRENT_TIMESTAMP AND (p.lease_until IS NULL OR p.lease_until<CURRENT_TIMESTAMP)
                AND NOT s.cancellation_in_progress AND s.state IN ('READY_FOR_EXECUTION','START_PENDING','IN_EXECUTION')
                ORDER BY p.next_attempt_at LIMIT 50
                """,UUID.class)) {
            safely(id,() -> startItem.recoverStart(id,id));
        }
        for (UUID id:jdbc.queryForList("""
                SELECT service_order_id FROM service_order_saga WHERE settlement_pending AND NOT cancellation_in_progress
                AND state IN ('WAITING_PAYMENT','READY_FOR_DELIVERY') AND next_attempt_at<=CURRENT_TIMESTAMP
                AND (recovery_until IS NULL OR recovery_until<CURRENT_TIMESTAMP) ORDER BY next_attempt_at LIMIT 50
                """,UUID.class)) {
            UUID owner=UUID.randomUUID();
            if (jdbc.update("UPDATE service_order_saga SET recovery_owner=?,recovery_until=CURRENT_TIMESTAMP+interval '30 seconds' WHERE service_order_id=? AND (recovery_until IS NULL OR recovery_until<CURRENT_TIMESTAMP)",owner,id)==1) {
                try { safely(id,() -> finishOrder.refreshSettlement(id,id)); }
                finally { jdbc.update("UPDATE service_order_saga SET recovery_owner=NULL,recovery_until=NULL,next_attempt_at=CURRENT_TIMESTAMP+interval '5 seconds' WHERE service_order_id=? AND recovery_owner=?",id,owner); }
            }
        }
    }
    private void safely(UUID id,Runnable task) {
        try { task.run(); }
        catch (RuntimeException ex) { LOG.warn("Saga recovery failed identity={} type={}",id,ex.getClass().getSimpleName()); }
    }
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name="app.saga.recovery-enabled",havingValue="true",matchIfMissing=true)
    static class Schedule {
        private final SagaRecoveryWorker worker;
        Schedule(SagaRecoveryWorker worker) { this.worker=worker; }
        @Scheduled(fixedDelayString="${app.saga.recovery-delay-ms:5000}")
        public void run() { worker.tick(); }
    }
}
