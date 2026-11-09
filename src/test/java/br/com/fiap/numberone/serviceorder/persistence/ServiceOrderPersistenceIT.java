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
import java.math.BigDecimal;
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
        var tables = jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname=current_schema()", String.class);
        assertThat(tables).containsExactlyInAnyOrder("flyway_schema_history", "cliente", "veiculo", "servico_automotivo",
                "ordem_servico", "item_ordem_servico", "material_previsto", "material_movement_reference", "service_order_composition", "service_order_saga", "inbox_event", "saga_operation", "service_order_payment");
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success=true AND version IS NOT NULL ORDER BY installed_rank",
                String.class)).containsExactly("1", "2", "3", "4", "5");
        var foreignTables = jdbc.queryForList("SELECT DISTINCT ccu.table_name FROM information_schema.table_constraints tc JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name=ccu.constraint_name AND tc.constraint_schema=ccu.constraint_schema WHERE tc.constraint_type='FOREIGN KEY' AND tc.table_schema=current_schema()", String.class);
        assertThat(foreignTables).containsOnly("cliente", "veiculo", "servico_automotivo", "ordem_servico",
                "item_ordem_servico", "material_previsto", "service_order_composition", "saga_operation");
    }

    @Test void directConsumptionRequiresValidQuantityOperationAndMovement() throws Exception {
        var f = diagnosisFixture();
        var m = addMaterial(f.item());
        var operation = UUID.randomUUID();
        var movement = UUID.randomUUID();
        assertThatThrownBy(() -> recordConsumption(m, 2, operation, movement)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> recordConsumption(m, -1, operation, movement)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> recordConsumption(m, 1, null, movement)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> recordConsumption(m, 1, new UUID(0, 0), movement)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> recordConsumption(m, 1, operation, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> recordConsumption(m, 1, operation, new UUID(0, 0))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> recordConsumption(m, 0, null, movement)).isInstanceOf(DataIntegrityViolationException.class);

        recordConsumption(m, 1, operation, movement);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM material_movement_reference WHERE allocation_id=?", m))
                .isInstanceOf(DataIntegrityViolationException.class);
        var material = orders.find(f.order(), BRANCH, false).orElseThrow().item(f.item()).getMaterials().getFirst();
        assertThat(material.getReservedQuantity()).isZero();
        assertThat(material.getReservationId()).isNull();
        assertThat(material.hasConfirmedConsumption()).isTrue();
        assertThat(material.getConsumptionOperationId()).isEqualTo(operation);
        assertThat(material.getMovementIds()).containsExactly(movement);
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
        // Financial projection can arrive before commercial context; it never authorizes execution.
        jdbc.update("UPDATE ordem_servico SET payment_confirmed_at=now() WHERE id=?", f.order());
        expect(409, call("PATCH", "/api/admin/ordens-servico/" + f.order() + "/iniciar", null));
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
        var f = diagnosisFixture();
        var obsolete = orders.find(f.order(), BRANCH, false).orElseThrow().compositionSnapshot();
        var m = addMaterial(f.item());
        var current = orders.find(f.order(), BRANCH, false).orElseThrow();
        var budget = UUID.randomUUID();
        current.registerBudget(budget, 2, current.getCompositionVersion());
        assertThatThrownBy(() -> current.approveBudget(budget, 1, obsolete, new BigDecimal("345.67"), "BRL"))
                .hasMessage("STALE_BUDGET_REVISION");
        assertThatThrownBy(() -> current.approveBudget(budget, 2, obsolete, new BigDecimal("345.67"), "BRL"))
                .hasMessage("BUDGET_COMPOSITION_CONFLICT");
        current.approveBudget(budget, 2, current.compositionSnapshot(), new BigDecimal("345.67"), "BRL");
        orders.save(current);
        assertThat(jdbc.queryForObject("""
                SELECT jsonb_array_length(items->0->'supplies') FROM service_order_composition
                WHERE service_order_id=? AND composition_version=?
                """, Integer.class, f.order(), obsolete.version())).isZero();
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
        assertThatThrownBy(() -> jdbc.update("UPDATE item_ordem_servico SET status='CANCELLED' WHERE id=?", f.item()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE service_order_composition SET items='[]' WHERE service_order_id=?", f.order()))
                .isInstanceOf(DataIntegrityViolationException.class);
        expect(409, call("PATCH", "/api/admin/ordens-servico/" + f.order() + "/diagnostico",
                Map.of("descricaoDiagnosticoFinal", "Bloqueado", "dataHoraPrevista", "2026-11-01T15:00:00")));
        var tracking = expect(200, call("GET", "/api/public/ordens-servico/" + f.order() + "/acompanhamento", null));
        assertThat(tracking.get("estimativa").get("valorEstimado").asString()).isEqualTo("350.00");
        assertThat(tracking.get("orcamentoAprovado").get("totalAmount").asString()).isEqualTo("345.67");
        assertThat(tracking.get("orcamentoAprovado").get("approvedItems").get(0).get("supplies").get(0)
                .get("inventoryItemId").asString()).isEqualTo(MATERIAL);
        assertThat(tracking.get("budgetVersion").asInt()).isEqualTo(2);
        assertThat(tracking.get("compositionVersion").asInt()).isEqualTo(current.getCompositionVersion());
    }

    @Test void approvedRepairWithDirectConsumptionCompletesOverHttpWithoutPayment() throws Exception {
        var f = diagnosisFixture(); var m = addMaterial(f.item()); approveFixture(f);
        var orderRoute = "/api/admin/ordens-servico/" + f.order();
        var itemRoute = "/api/admin/itens-ordem-servico/" + f.item();
        expect(200, call("PATCH", orderRoute + "/iniciar", null));
        expect(409, call("PATCH", itemRoute + "/concluir", null));
        assertThatThrownBy(() -> confirmStartFixture(f)).hasMessage("MATERIAL_CONSUMPTION_PENDING");
        recordConsumption(m, 1, UUID.randomUUID(), UUID.randomUUID());
        confirmStartFixture(f);
        expect(409, call("PATCH", orderRoute + "/concluir", null));
        var item = expect(200, call("PATCH", itemRoute + "/concluir", null));
        assertThat(item.get("insumos").get(0).get("consumoConfirmado").asBoolean()).isTrue();
        assertThat(item.get("insumos").get(0).get("reservationId").isNull()).isTrue();
        var complete = expect(200, call("PATCH", orderRoute + "/concluir", null));
        assertThat(complete.get("status").asString()).isEqualTo("FINALIZADA");
        assertThat(complete.get("executionState").asString()).isEqualTo("COMPLETED");
        assertThat(complete.get("pagamentoConfirmado").asBoolean()).isFalse();
        var delivery = expect(503, call("PATCH", orderRoute + "/entregar", null));
        assertThat(delivery.get("message").asString()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        jdbc.update("UPDATE ordem_servico SET payment_confirmed_at=now() WHERE id=?", f.order());
        expect(503, call("PATCH", orderRoute + "/entregar", null));
        assertThat(expect(200, call("GET", orderRoute, null)).get("dataHoraEntrega").isNull()).isTrue();
        var durations = expect(200, call("GET", orderRoute + "/tempo-medio-execucao-servicos", null));
        assertThat(durations.get("servicosConcluidos").asInt()).isEqualTo(1);
        assertThat(durations.get("tempoMedioExecucaoMinutos").asLong()).isGreaterThanOrEqualTo(1);
    }

    private void approveFixture(Fixture f) {
        var order = orders.find(f.order(), BRANCH, false).orElseThrow();
        var budget = UUID.randomUUID();
        order.registerBudget(budget, 1, order.getCompositionVersion());
        order.approveBudget(budget, 1, order.compositionSnapshot(), new BigDecimal("345.67"), "BRL");
        orders.save(order);
        jdbc.update("UPDATE service_order_saga SET state='READY_FOR_EXECUTION' WHERE service_order_id=?", f.order());
    }

    private void confirmStartFixture(Fixture f) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var order = orders.find(f.order(), BRANCH, true).orElseThrow();
            order.confirmItemStart(f.item(), LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(2));
            orders.save(order);
            jdbc.update("UPDATE service_order_saga SET state='IN_EXECUTION' WHERE service_order_id=?", f.order());
        });
    }

    private void recordConsumption(UUID allocation, int quantity, UUID operation, UUID movement) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("UPDATE material_previsto SET consumed_quantity=?, consumption_operation_id=? WHERE id=?",
                    quantity, operation, allocation);
            if (movement != null) {
                jdbc.update("INSERT INTO material_movement_reference VALUES (?,?)", allocation, movement);
            }
            jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
        });
    }
}
