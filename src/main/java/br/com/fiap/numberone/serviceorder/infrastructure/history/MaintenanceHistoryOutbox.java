package br.com.fiap.numberone.serviceorder.infrastructure.history;

import br.com.fiap.numberone.serviceorder.application.gateways.MaintenanceHistoryGateway;
import br.com.fiap.numberone.serviceorder.domain.valueobjects.MaintenanceSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MaintenanceHistoryOutbox implements MaintenanceHistoryGateway {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public MaintenanceHistoryOutbox(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(MaintenanceSnapshot snapshot) {
        jdbc.update("""
                INSERT INTO maintenance_history_outbox
                    (service_order_id, history_version, vehicle_id, branch_id, completed_at, history_key, snapshot)
                VALUES (?,?,?,?,?,?,?)
                """, snapshot.serviceOrderId(), snapshot.historyVersion(), snapshot.vehicleId(), snapshot.branchId(),
                Timestamp.from(Instant.parse(snapshot.completedAt())), snapshot.historyKey(), json.writeValueAsString(snapshot));
    }

    record Claim(UUID orderId, int version, UUID vehicleId, UUID branchId, String key, String snapshot, UUID owner) { }

    // A single committed statement claims one row. An expired claim can be replayed by another worker.
    Optional<Claim> claim() {
        UUID owner = UUID.randomUUID();
        return jdbc.query("""
                WITH due AS (
                    SELECT service_order_id, history_version FROM maintenance_history_outbox
                    WHERE state='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP
                      AND (lease_until IS NULL OR lease_until<=CURRENT_TIMESTAMP)
                    ORDER BY next_attempt_at, completed_at, service_order_id, history_version
                    LIMIT 1 FOR UPDATE SKIP LOCKED
                )
                UPDATE maintenance_history_outbox AS outbox
                SET lease_owner=?, lease_until=CURRENT_TIMESTAMP+interval '30 seconds', attempts=attempts+1
                FROM due
                WHERE (outbox.service_order_id, outbox.history_version)=(due.service_order_id, due.history_version)
                RETURNING outbox.*
                """, (rs, row) -> new Claim(rs.getObject("service_order_id", UUID.class),
                rs.getInt("history_version"), rs.getObject("vehicle_id", UUID.class), rs.getObject("branch_id", UUID.class),
                rs.getString("history_key"), rs.getString("snapshot"), owner), owner).stream().findFirst();
    }

    boolean finish(Claim claim, String state, String error) {
        return jdbc.update("""
                UPDATE maintenance_history_outbox SET state=?, last_error=?, lease_owner=NULL, lease_until=NULL,
                    projected_at=CASE WHEN ?='PROJECTED' THEN CURRENT_TIMESTAMP END,
                    next_attempt_at=CURRENT_TIMESTAMP + LEAST(300, 5::bigint * attempts)*interval '1 second'
                WHERE service_order_id=? AND history_version=? AND lease_owner=?
                """, state, error, state, claim.orderId(), claim.version(), claim.owner()) == 1;
    }

    long count(String state) {
        return jdbc.queryForObject("SELECT count(*) FROM maintenance_history_outbox WHERE state=?", Long.class, state);
    }

    double oldestPendingSeconds() {
        return jdbc.queryForObject("""
                SELECT COALESCE(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP - min(completed_at)),0)
                FROM maintenance_history_outbox WHERE state='PENDING'
                """, Double.class);
    }
}
