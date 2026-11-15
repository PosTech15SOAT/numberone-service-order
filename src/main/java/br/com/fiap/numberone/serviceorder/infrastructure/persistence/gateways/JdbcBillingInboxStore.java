package br.com.fiap.numberone.serviceorder.infrastructure.persistence.gateways;

import br.com.fiap.numberone.serviceorder.application.events.BillingEvent;
import br.com.fiap.numberone.serviceorder.application.gateways.BillingInboxStore;
import br.com.fiap.numberone.serviceorder.application.gateways.OrderCompletionStore;
import br.com.fiap.numberone.serviceorder.application.gateways.ServiceOrderGateway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcBillingInboxStore implements BillingInboxStore {
    private final JdbcTemplate jdbc;
    private final ServiceOrderGateway orders;
    private final OrderCompletionStore sagas;

    public JdbcBillingInboxStore(JdbcTemplate jdbc, ServiceOrderGateway orders, OrderCompletionStore sagas) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.sagas = sagas;
    }

    @Override
    public Optional<String> findProcessedHashForUpdate(UUID eventId) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", eventId.toString());
        return jdbc.queryForList("SELECT content_hash FROM inbox_event WHERE event_id=?", String.class, eventId)
                .stream().findFirst();
    }

    @Override
    public EventContext begin(BillingEvent event) {
        if (event.payload() instanceof BillingEvent.Payment payment) {
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 1))", payment.id().toString());
        }
        // Lock the order and Saga before inserting the row with a foreign key to the order.
        var branch = jdbc.queryForObject("SELECT branch_id FROM ordem_servico WHERE id=? FOR UPDATE", UUID.class, event.orderId());
        var order = orders.find(event.orderId(), branch, true).orElseThrow();
        var saga = sagas.lockSaga(event.orderId());
        jdbc.update("""
                INSERT INTO inbox_event(event_id, content_hash, event_type, service_order_id, correlation_id, occurred_at, outcome)
                VALUES (?, ?, ?, ?, ?, ?, 'PROCESSING')
                """, event.id(), event.hash(), event.kind().wireName(), event.orderId(),
                event.correlationId(), Timestamp.from(event.occurredAt()));
        return new EventContext(order, saga);
    }

    @Override
    public void complete(UUID eventId, BillingEvent.Outcome outcome) {
        jdbc.update("UPDATE inbox_event SET outcome=? WHERE event_id=?", outcome.name(), eventId);
    }

    @Override
    public Optional<RecordedPayment> findPayment(UUID paymentId) {
        return jdbc.query("""
                SELECT service_order_id, payment_version, status FROM service_order_payment WHERE payment_id=?
                """, (row, index) -> new RecordedPayment(row.getObject("service_order_id", UUID.class),
                row.getInt("payment_version"), row.getString("status")), paymentId).stream().findFirst();
    }

    @Override
    public LocalDateTime savePaymentAndFindLatestPaid(BillingEvent event, BillingEvent.Payment payment) {
        jdbc.update("""
                INSERT INTO service_order_payment(payment_id,service_order_id,payment_version,status,occurred_at)
                VALUES (?,?,?,?,?)
                ON CONFLICT(payment_id) DO UPDATE SET payment_version=EXCLUDED.payment_version,
                status=EXCLUDED.status, occurred_at=EXCLUDED.occurred_at
                """, payment.id(), event.orderId(), payment.version(), payment.status(), Timestamp.from(event.occurredAt()));
        var paid = jdbc.queryForObject("""
                SELECT max(occurred_at) FROM service_order_payment WHERE service_order_id=? AND status='PAID'
                """, Timestamp.class, event.orderId());
        return paid == null ? null : paid.toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime();
    }

    @Override
    public void scheduleSettlementCheck(UUID orderId) {
        jdbc.update("""
                UPDATE service_order_saga SET settlement_pending=true, version=version+1, updated_at=CURRENT_TIMESTAMP
                WHERE service_order_id=?
                """, orderId);
    }
}
