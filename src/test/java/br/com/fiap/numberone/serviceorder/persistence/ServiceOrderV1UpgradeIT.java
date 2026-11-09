package br.com.fiap.numberone.serviceorder.persistence;

import br.com.fiap.numberone.support.PostgresSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceOrderV1UpgradeIT extends PostgresSupport {
    @Test void upgradingV1PreservesLegacyAuditAndAllowsDirectConsumption() {
        var schema = isolatedSchema();
        var dataSource = postgresDataSource(schema);
        var jdbc = new JdbcTemplate(dataSource);
        var v1 = Flyway.configure().dataSource(dataSource).defaultSchema(schema).target("1").load();
        v1.migrate();
        try {
            var order = seedV1Order(jdbc);
            var item = jdbc.queryForObject("SELECT id FROM item_ordem_servico WHERE id_ordem_servico=?", UUID.class, order);
            var reserveOnly = seedV1Material(jdbc, item, 1, 0);
            var confirmed = seedV1Material(jdbc, item, 1, 1);
            var incomplete = seedV1Material(jdbc, item, 1, 1);
            var planned = seedV1Material(jdbc, item, 0, 0);
            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE material_previsto SET consumed_quantity=1, consumption_operation_id=? WHERE id=?
                    """, UUID.randomUUID(), planned)).isInstanceOf(DataIntegrityViolationException.class);
            var legacyMovement = UUID.randomUUID();
            jdbc.update("INSERT INTO material_movement_reference VALUES (?,?)", confirmed, legacyMovement);
            jdbc.update("""
                    UPDATE ordem_servico SET status='READY_FOR_EXECUTION', approved_budget_id=?, approved_revision=1,
                        approved_currency='BRL', approved_amount=345.67, approved_items_json='[{"legacy":true}]',
                        payment_confirmed_at=now() WHERE id=?
                    """, UUID.randomUUID(), order);
            var prior = jdbc.queryForList("SELECT * FROM material_previsto ORDER BY id");
            var priorApproval = jdbc.queryForMap("""
                    SELECT approved_budget_id, approved_revision, approved_currency, approved_amount,
                           approved_items_json, payment_confirmed_at FROM ordem_servico WHERE id=?
                    """, order);
            var v1Checksum = jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='1'", Integer.class);

            var latest = Flyway.configure().dataSource(dataSource).defaultSchema(schema).load();
            assertThat(latest.migrate().migrationsExecuted).isEqualTo(4);
            latest.validate();
            assertThat(jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='1'", Integer.class))
                    .isEqualTo(v1Checksum);
            assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",
                    String.class)).containsExactly("1", "2", "3", "4", "5");
            assertThat(jdbc.queryForObject("SELECT state FROM service_order_saga WHERE service_order_id=?", String.class, order))
                    .isEqualTo("MANUAL_INTERVENTION");
            assertThat(jdbc.queryForList("SELECT * FROM material_previsto ORDER BY id")).isEqualTo(prior);
            assertThat(jdbc.queryForMap("""
                    SELECT approved_budget_id, approved_revision, approved_currency, approved_amount,
                           approved_items_json, payment_confirmed_at FROM ordem_servico WHERE id=?
                    """, order)).isEqualTo(priorApproval);
            assertThat(jdbc.queryForObject("SELECT status FROM ordem_servico WHERE id=?", String.class, order)).isEqualTo("APPROVED");
            assertThat(jdbc.queryForObject("SELECT approved_composition_version FROM ordem_servico WHERE id=?", Integer.class, order)).isNull();
            assertThat(jdbc.queryForObject("SELECT status FROM item_ordem_servico WHERE id=?", String.class, item)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT start_pending FROM item_ordem_servico WHERE id=?", Boolean.class, item)).isTrue();
            assertThat(jdbc.queryForObject("SELECT movement_id FROM material_movement_reference WHERE allocation_id=?", UUID.class, confirmed))
                    .isEqualTo(legacyMovement);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM material_movement_reference WHERE allocation_id IN (?,?)",
                    Integer.class, reserveOnly, incomplete)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT jsonb_array_length(items->0->'supplies') FROM service_order_composition
                    WHERE service_order_id=? AND composition_version=1
                    """, Integer.class, order)).isEqualTo(4);

            new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status -> {
                jdbc.update("""
                        UPDATE material_previsto SET consumed_quantity=1, consumption_operation_id=? WHERE id=?
                        """, UUID.randomUUID(), planned);
                jdbc.update("INSERT INTO material_movement_reference VALUES (?,?)", planned, UUID.randomUUID());
            });
            assertThat(jdbc.queryForObject("""
                    SELECT consumed_quantity FROM material_previsto
                    WHERE id=? AND reserved_quantity=0 AND reservation_id IS NULL
                    """, Integer.class, planned)).isEqualTo(1);
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private UUID seedV1Order(JdbcTemplate jdbc) {
        var order = seedOrder(jdbc);
        seedItem(jdbc, order);
        return order;
    }

    private UUID seedOrder(JdbcTemplate jdbc) {
        var customer = UUID.randomUUID();
        var vehicle = UUID.randomUUID();
        var order = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cliente (id, nome, tipo_documento, documento, telefone, endereco, email, created_at)
                VALUES (?, 'Fixture', 'PESSOA_FISICA', '52998224725', '00000000000', 'Fixture', 'fixture@example.invalid', now())
                """, customer);
        jdbc.update("""
                INSERT INTO veiculo (id, placa, marca, modelo, ano, id_cliente, created_at)
                VALUES (?, 'TST1A23', 'Fixture', 'Fixture', 2026, ?, now())
                """, vehicle, customer);
        jdbc.update("""
                INSERT INTO ordem_servico (id, branch_id, id_cliente, id_veiculo, attempt_id, budget_revision,
                    initial_description, diagnosis_description, status, entry_date_time, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 1, 'Fixture', 'Fixture', 'IN_DIAGNOSIS', now(), now(), now())
                """, order, UUID.randomUUID(), customer, vehicle, UUID.randomUUID());
        return order;
    }

    private UUID seedItem(JdbcTemplate jdbc, UUID order) {
        var service = UUID.randomUUID();
        var item = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO servico_automotivo (id, codigo, nome, valor_base, tempo_estimado_minutos, ativo, created_at)
                VALUES (?, ?, 'Fixture', 1, 1, true, now())
                """, service, service.toString());
        jdbc.update("""
                INSERT INTO item_ordem_servico (id, id_ordem_servico, id_servico, service_description, labor_unit_price,
                    quantity, estimated_time_minutes, optional, status)
                VALUES (?, ?, ?, 'Fixture', 1, 1, 1, false, 'START_REQUESTED')
                """, item, order, service);
        return item;
    }

    private UUID seedV1Material(JdbcTemplate jdbc, UUID item, int reserved, int consumed) {
        var allocation = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO material_previsto
                    (id, id_item_ordem_servico, material_id, description, reference_unit_price, planned_quantity,
                     reserved_quantity, reservation_id, consumed_quantity, consumption_operation_id)
                VALUES (?, ?, ?, 'Fixture', 1, 1, ?, ?, ?, ?)
                """, allocation, item, UUID.randomUUID(), reserved, reserved == 0 ? null : UUID.randomUUID(),
                consumed, consumed == 0 ? null : UUID.randomUUID());
        return allocation;
    }
}
