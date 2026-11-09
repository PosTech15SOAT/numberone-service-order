package br.com.fiap.numberone.serviceorder.api;

import br.com.fiap.numberone.support.HttpPostgresSupport;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP + PostgreSQL. The material lookup is explicitly a fixture, not Inventory integration. */
class ServiceOrderTransportIT extends HttpPostgresSupport {
    @Test void diagnosisAndMaterialPlanAreLocalAndNeverClaimApprovalOrConsumption() throws Exception {
        var f = diagnosisFixture(); var m = addMaterial(f.item());
        var response = expect(200, call("GET", "/api/admin/ordens-servico/" + f.order(), null));
        assertThat(response.get("status").asString()).isEqualTo("EM_DIAGNOSTICO");
        assertThat(response.get("branchId").asString()).isEqualTo(BRANCH.toString());
        assertThat(response.get("orcamentoAprovado").isNull()).isTrue();
        assertThat(response.get("pagamentoConfirmado").asBoolean()).isFalse();
        assertThat(response.get("estimativa").get("valorEstimado").asString()).isEqualTo("350.00");
        var material = response.get("itensServico").get(0).get("insumos").get(0);
        assertThat(material.get("quantidadePrevista").asInt()).isEqualTo(1);
        assertThat(material.get("quantidadeConsumida").asInt()).isZero();
        assertThat(material.get("quantidadeReservada").asInt()).isZero();
        expect(200, call("GET", "/api/admin/itens-ordem-servico/" + f.item() + "/insumos", null));
        expect(200, call("PUT", "/api/admin/itens-ordem-servico/" + f.item() + "/insumos/" + m,
                Map.of("idMaterial", MATERIAL, "quantidadePrevista", 2)));
        expect(204, call("DELETE", "/api/admin/itens-ordem-servico/" + f.item() + "/insumos/" + m, null));
        expect(204, call("DELETE", "/api/admin/itens-ordem-servico/" + f.item(), null));
        expect(200, call("GET", "/api/admin/ordens-servico", null));
    }

    @Test void approvalStartAndDeliveryCannotBeBypassedThroughLegacyEndpoints() throws Exception {
        var f = diagnosisFixture(); addMaterial(f.item());
        var start = expect(409, call("PATCH", "/api/admin/ordens-servico/" + f.order() + "/iniciar", null));
        assertThat(start.get("message").asString()).isEqualTo("BUDGET_NOT_APPROVED");
        assertThat(start.get("code").asString()).isEqualTo("BUDGET_NOT_APPROVED");
        expect(409, call("PATCH", "/api/admin/itens-ordem-servico/" + f.item() + "/iniciar", null));
        expect(409, call("PATCH", "/api/admin/itens-ordem-servico/" + f.item() + "/concluir", null));
        expect(409, call("PATCH", "/api/admin/ordens-servico/" + f.order() + "/concluir", null));
        expect(409, call("PATCH", "/api/admin/ordens-servico/" + f.order() + "/entregar", null));
    }

    @Test void ownershipAndPermissionAreRequiredForTracking() throws Exception {
        var f = diagnosisFixture(); String route = "/api/public/ordens-servico/" + f.order() + "/acompanhamento";
        expect(200, call("GET", route, null, customer(f.customer())));
        expect(403, call("GET", route, null, customer(UUID.randomUUID())));
        var withoutPermission = new HashMap<>(customer(f.customer())); withoutPermission.put("X-Authenticated-Permissions", "");
        expect(403, call("GET", route, null, withoutPermission));
        expect(401, call("GET", route, null, Map.of()));
        expect(403, call("GET", "/api/admin/clientes", null, customer(f.customer())));
        expect(200, call("GET", route, null));
    }

    @Test void trackingReadsLegacyApprovalAndStartPendingWithSeparateCommercialStatuses() throws Exception {
        var f = diagnosisFixture();
        var allocation = addMaterial(f.item());
        jdbc.update("UPDATE material_previsto SET reserved_quantity=1, reservation_id=? WHERE id=?", UUID.randomUUID(), allocation);
        jdbc.update("UPDATE item_ordem_servico SET start_pending=true WHERE id=?", f.item());
        jdbc.update("""
                UPDATE ordem_servico SET status='APPROVED', approved_budget_id=?, approved_revision=budget_revision,
                    approved_currency='BRL', approved_amount=345.67, approved_items_json='[{"legacy":true}]'
                WHERE id=?
                """, UUID.randomUUID(), f.order());

        var tracking = expect(200, call("GET", "/api/public/ordens-servico/" + f.order() + "/acompanhamento",
                null, customer(f.customer())));
        assertThat(tracking.get("status").asString()).isEqualTo("APROVADA");
        assertThat(tracking.get("executionState").asString()).isEqualTo("READY_FOR_EXECUTION");
        var item = tracking.get("itensServico").get(0);
        assertThat(item.get("status").asString()).isEqualTo("PENDENTE");
        assertThat(item.get("executionState").asString()).isEqualTo("START_PENDING");
        assertThat(item.get("dataHoraInicio").isNull()).isTrue();
        assertThat(item.get("insumos").get(0).get("consumoConfirmado").asBoolean()).isFalse();
        var approved = tracking.get("orcamentoAprovado");
        assertThat(approved.get("compositionVersion").isNull()).isTrue();
        assertThat(approved.get("approvedItems").get(0).get("legacy").asBoolean()).isTrue();
        var refused = expect(409, call("PATCH", "/api/admin/ordens-servico/" + f.order() + "/iniciar", null));
        assertThat(refused.get("message").asString()).isEqualTo("BUDGET_NOT_APPROVED");
    }

    @Test void malformedInactiveAndPartialGatewayContextsAreRejectedWithoutEcho() throws Exception {
        var inactive = new HashMap<>(admin()); inactive.put("X-Authenticated-Status", "INACTIVE");
        expect(401, call("GET", "/api/admin/clientes", null, inactive));
        var invalid = new HashMap<>(admin()); invalid.put("X-Authenticated-Customer-Id", "invalid-sensitive-value");
        var reply = expect(401, call("GET", "/api/admin/clientes", null, invalid));
        assertThat(reply.toString()).doesNotContain("invalid-sensitive-value", "fixture-admin");
        expect(401, call("GET", "/api/admin/clientes", null, Map.of("X-Authenticated-Roles", "ADMIN")));
    }

    @Test void serverAssignsBranchAndScopesAllOrderAndItemRoutes() throws Exception {
        var c = createCustomer(); var v = createVehicle(c);
        var input = new HashMap<String,Object>(orderBody(c, v)); input.put("branchId", UUID.randomUUID());
        var created = expect(201, call("POST", "/api/admin/ordens-servico", input));
        assertThat(created.get("branchId").asString()).isEqualTo(BRANCH.toString());
        var o = id(created); diagnose(o); var i = addItem(o, createService());
        jdbc.update("UPDATE ordem_servico SET branch_id=? WHERE id=?", UUID.randomUUID(), o);
        expect(404, call("GET", "/api/admin/ordens-servico/" + o, null));
        expect(404, call("PATCH", "/api/admin/itens-ordem-servico/" + i + "/iniciar", null));
        expect(404, call("GET", "/api/admin/itens-ordem-servico/" + i + "/insumos", null));
        assertThat(expect(200, call("GET", "/api/admin/ordens-servico", null)).size()).isZero();
    }

    @Test void customerVehicleMismatchAndInactiveCustomerAreRejected() throws Exception {
        var c = createCustomer(); var v = createVehicle(c); var other = createCustomer("11144477735");
        expect(409, call("POST", "/api/admin/ordens-servico", orderBody(other, v)));
        var inactive = new HashMap<>(customerBody("52998224725")); inactive.put("ativo", false);
        expect(200, call("PUT", "/api/admin/clientes/" + c, inactive));
        expect(409, call("POST", "/api/admin/ordens-servico", orderBody(c, v)));
    }

    @Test void nestedMaterialEndpointCannotModifyAnotherItemsAllocation() throws Exception {
        var f = diagnosisFixture(); var allocation = addMaterial(f.item()); var otherItem = addItem(f.order(), f.service());
        String other = "/api/admin/itens-ordem-servico/" + otherItem + "/insumos/" + allocation;
        expect(404, call("PUT", other, Map.of("idMaterial", MATERIAL, "quantidadePrevista", 2)));
        expect(404, call("DELETE", other, null));
        assertThat(expect(200, call("GET", "/api/admin/itens-ordem-servico/" + f.item() + "/insumos", null)).size()).isEqualTo(1);
    }

    @Test void invalidQuantitiesAndUnknownMaterialsAreNotAccepted() throws Exception {
        var f = diagnosisFixture(); String route = "/api/admin/itens-ordem-servico/" + f.item() + "/insumos";
        expect(400, call("POST", route, Map.of("idMaterial", MATERIAL, "quantidadePrevista", 0)));
        expect(400, call("POST", route, Map.of("idMaterial", MATERIAL, "quantidadePrevista", -1)));
        expect(404, call("POST", route, Map.of("idMaterial", UUID.randomUUID(), "quantidadePrevista", 1)));
        addMaterial(f.item());
        expect(409, call("POST", route, Map.of("idMaterial", MATERIAL, "quantidadePrevista", 1)));
    }

    @Test void localCancellationDoesNotPretendToCompensateAndBlocksDelivery() throws Exception {
        var f = diagnosisFixture();
        expect(400, call("PATCH", "/api/admin/ordens-servico/" + f.order() + "/cancelar", null));
        var cancelled = expect(202, call("PATCH", "/api/admin/ordens-servico/" + f.order() + "/cancelar",
                Map.of("scope","FULL","reason","Cancelamento verificado","unusedConfirmed",true,"physicallyRemoved",false,"returnConfirmed",false,
                       "verifiedAt",java.time.Instant.now().toString(),"evidenceReference","fixture-inspection")));
        assertThat(cancelled.get("state").asString()).isEqualTo("COMPENSATING");
        assertThat(jdbc.queryForObject("SELECT status FROM ordem_servico WHERE id=?",String.class,f.order())).isEqualTo("IN_DIAGNOSIS");
        expect(409, call("PATCH", "/api/admin/ordens-servico/" + f.order() + "/entregar", null));
        expect(409, call("PATCH", "/api/admin/ordens-servico/" + f.order() + "/diagnostico",
                Map.of("descricaoDiagnosticoFinal", "Bloqueado", "dataHoraPrevista", "2026-11-01T15:00:00")));
    }
}
