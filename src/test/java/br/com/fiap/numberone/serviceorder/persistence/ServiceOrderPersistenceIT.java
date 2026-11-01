package br.com.fiap.numberone.serviceorder.persistence;

import br.com.fiap.numberone.support.HttpPostgresSupport;
import br.com.fiap.numberone.serviceorder.application.gateways.ServiceOrderGateway;
import br.com.fiap.numberone.serviceorder.infrastructure.persistence.repositories.ServiceOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/** Assertions exercise real PostgreSQL constraints and locks. Confirmations below are test data only. */
class ServiceOrderPersistenceIT extends HttpPostgresSupport {
    @Autowired ServiceOrderGateway orders;
    @Autowired ServiceOrderRepository repository;
    @Autowired PlatformTransactionManager transactions;

    @Test void flywayCreatesOnlyOwnedTablesAndNoExternalForeignKeys() {
        var tables = jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public'", String.class);
        assertThat(tables).containsExactlyInAnyOrder("flyway_schema_history", "cliente", "veiculo", "servico_automotivo",
                "ordem_servico", "item_ordem_servico", "material_previsto", "material_movement_reference");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success=true AND version='1'", Integer.class)).isEqualTo(1);
        var foreignTables = jdbc.queryForList("SELECT DISTINCT ccu.table_name FROM information_schema.table_constraints tc JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name=ccu.constraint_name AND tc.constraint_schema=ccu.constraint_schema WHERE tc.constraint_type='FOREIGN KEY' AND tc.table_schema='public'", String.class);
        assertThat(foreignTables).containsOnly("cliente", "veiculo", "servico_automotivo", "ordem_servico", "item_ordem_servico", "material_previsto");
    }

    @Test void materialCountersAndVehicleOwnershipAreDatabaseConstraints() throws Exception {
        var f = diagnosisFixture(); var m = addMaterial(f.item()); var other = createCustomer("11144477735");
        assertThatThrownBy(() -> jdbc.update("UPDATE ordem_servico SET id_cliente=? WHERE id=?", other, f.order()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE material_previsto SET planned_quantity=0 WHERE id=?", m))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE material_previsto SET consumed_quantity=1 WHERE id=?", m))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE item_ordem_servico SET status='COMPLETED' WHERE id=?", f.item()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE ordem_servico SET payment_confirmed_at=now() WHERE id=?", f.order()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void staleAggregateCannotOverwriteACommittedDiagnosis() throws Exception {
        var f = diagnosisFixture();
        var stale = orders.find(f.order(), BRANCH, false).orElseThrow();
        var current = orders.find(f.order(), BRANCH, false).orElseThrow();
        current.diagnose("Revisao atual", LocalDateTime.of(2026, 11, 1, 15, 0), null);
        var saved = orders.save(current);
        assertThat(saved.getVersion()).isGreaterThan(stale.getVersion());
        stale.diagnose("Revisao obsoleta", LocalDateTime.of(2026, 11, 1, 16, 0), null);
        assertThatThrownBy(() -> orders.save(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(orders.find(f.order(), BRANCH, false).orElseThrow().getFinalDiagnosisDescription()).isEqualTo("Revisao atual");
    }

    @Test void parentWriteLockSerializesConcurrentTransitionsInPostgres() throws Exception {
        var f = diagnosisFixture(); var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var holding = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                repository.lockById(f.order(), BRANCH).orElseThrow(); locked.countDown();
                try { if (!release.await(15, TimeUnit.SECONDS)) { throw new IllegalStateException("Lock test timed out"); } }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                return null;
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
                    jdbc.execute("SET LOCAL lock_timeout='250ms'");
                    return repository.lockById(f.order(), BRANCH);
                })).isInstanceOf(PessimisticLockingFailureException.class);
            } finally { release.countDown(); }
            holding.get(5, TimeUnit.SECONDS);
        }
    }

    @Test void approvedSnapshotAndCompositionAreImmutableInApiAndDatabase() throws Exception {
        var f = diagnosisFixture(); var m = addMaterial(f.item()); approveFixture(f);
        var unapproved = createOrder(f.customer(), f.vehicle()); diagnose(unapproved);
        var unapprovedItem = addItem(unapproved, f.service());
        assertThatThrownBy(() -> jdbc.update("UPDATE item_ordem_servico SET id_ordem_servico=? WHERE id=?", unapproved, f.item()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE material_previsto SET id_item_ordem_servico=? WHERE id=?", unapprovedItem, m))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE ordem_servico SET final_diagnosis_description='changed' WHERE id=?", f.order()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE ordem_servico SET approved_amount=1 WHERE id=?", f.order()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE material_previsto SET planned_quantity=2 WHERE id=?", m))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM item_ordem_servico WHERE id=?", f.item()))
                .isInstanceOf(DataIntegrityViolationException.class);
        expect(409, call("PATCH", "/api/admin/ordens-servico/" + f.order() + "/diagnostico",
                Map.of("descricaoDiagnosticoFinal", "Bloqueado", "dataHoraPrevista", "2026-11-01T15:00:00")));
        var tracking = expect(200, call("GET", "/api/public/ordens-servico/" + f.order() + "/acompanhamento", null));
        assertThat(tracking.get("estimativa").get("valorEstimado").asString()).isEqualTo("350.00");
        assertThat(tracking.get("orcamentoAprovado").get("totalAmount").asString()).isEqualTo("345.67");
    }

    @Test void confirmedFixtureCanCompleteLocallyButPendingConsumptionCannot() throws Exception {
        var f = diagnosisFixture(); var m = addMaterial(f.item()); approveFixture(f);
        jdbc.update("UPDATE ordem_servico SET status='READY_FOR_EXECUTION',payment_confirmed_at=now() WHERE id=?", f.order());
        var blocked = expect(409, call("PATCH", "/api/admin/itens-ordem-servico/" + f.item() + "/iniciar", null));
        assertThat(blocked.get("message").asString()).isEqualTo("EXECUTION_INTEGRATION_NOT_AVAILABLE");
        jdbc.update("UPDATE ordem_servico SET status='IN_PROGRESS' WHERE id=?", f.order());
        jdbc.update("UPDATE item_ordem_servico SET status='IN_PROGRESS',start_date_time=now()-interval '1 minute' WHERE id=?", f.item());
        expect(409, call("PATCH", "/api/admin/itens-ordem-servico/" + f.item() + "/concluir", null));
        jdbc.update("UPDATE material_previsto SET reserved_quantity=1,consumed_quantity=1,reservation_id=?,consumption_operation_id=? WHERE id=?", UUID.randomUUID(), UUID.randomUUID(), m);
        jdbc.update("INSERT INTO material_movement_reference(allocation_id,movement_id) VALUES (?,?)", m, UUID.randomUUID());
        expect(200, call("PATCH", "/api/admin/itens-ordem-servico/" + f.item() + "/concluir", null));
        expect(200, call("PATCH", "/api/admin/ordens-servico/" + f.order() + "/concluir", null));
        var delivered = expect(200, call("PATCH", "/api/admin/ordens-servico/" + f.order() + "/entregar", null));
        assertThat(delivered.get("status").asString()).isEqualTo("ENTREGUE");
        var durations = expect(200, call("GET", "/api/admin/ordens-servico/" + f.order() + "/tempo-medio-execucao-servicos", null));
        assertThat(durations.get("servicosConcluidos").asInt()).isEqualTo(1);
        assertThat(durations.get("tempoMedioExecucaoMinutos").asLong()).isGreaterThanOrEqualTo(1);
    }

    private void approveFixture(Fixture f) {
        // Test preparation, NOT a Billing event or live integration. No production confirmation endpoint exists.
        String composition = json.writeValueAsString(List.of(Map.of("serviceOrderItemId", f.item(),
                "automotiveServiceId", f.service(), "description", "Fixture", "quantity", 1,
                "laborUnitPrice", "145.67", "materials", List.of(Map.of("materialId", MATERIAL, "quantity", 1, "approvedUnitPrice", "200.00")))));
        jdbc.update("UPDATE ordem_servico SET status='APPROVED',approved_budget_id=?,approved_revision=budget_revision,approved_currency='BRL',approved_amount=345.67,approved_items_json=CAST(? AS jsonb) WHERE id=?", UUID.randomUUID(), composition, f.order());
    }
}
