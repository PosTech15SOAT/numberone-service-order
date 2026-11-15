package br.com.fiap.numberone.serviceorder.api;

import br.com.fiap.numberone.support.HttpPostgresSupport;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class CatalogTransportIT extends HttpPostgresSupport {
    @Test void customerCrudValidationAndNormalizedDocumentUniqueness() throws Exception {
        var c = createCustomer();
        expect(200, call("GET", "/api/admin/clientes/" + c, null));
        assertThat(expect(200, call("GET", "/api/admin/clientes", null)).size()).isEqualTo(1);
        expect(409, call("POST", "/api/admin/clientes", customerBody("529.982.247-25")));
        expect(422, call("POST", "/api/admin/clientes", customerBody("00000000000")));
        var invalid = new HashMap<>(customerBody("52998224725")); invalid.put("email", "invalid");
        expect(400, call("POST", "/api/admin/clientes", invalid));
        expect(200, call("PUT", "/api/admin/clientes/" + c, customerBody("52998224725")));
        expect(204, call("DELETE", "/api/admin/clientes/" + c, null));
        expect(404, call("GET", "/api/admin/clientes/" + c, null));
    }

    @Test void vehicleCrudValidatesOwnerPlateAndYearAndPreservesForeignKey() throws Exception {
        var c = createCustomer(); var v = createVehicle(c);
        expect(200, call("GET", "/api/admin/veiculos/" + v, null));
        assertThat(expect(200, call("GET", "/api/admin/veiculos", null)).size()).isEqualTo(1);
        expect(422, call("POST", "/api/admin/veiculos", vehicleBody(c, "tst1a23")));
        expect(404, call("POST", "/api/admin/veiculos", vehicleBody(UUID.randomUUID(), "TST2A34")));
        var invalid = new HashMap<>(vehicleBody(c, "TST2A34")); invalid.put("ano", 1700);
        expect(400, call("POST", "/api/admin/veiculos", invalid));
        expect(200, call("PUT", "/api/admin/veiculos/" + v, vehicleBody(c, "TST2A34")));
        expect(409, call("DELETE", "/api/admin/clientes/" + c, null));
        expect(204, call("DELETE", "/api/admin/veiculos/" + v, null));
        expect(404, call("GET", "/api/admin/veiculos/" + v, null));
        expect(204, call("DELETE", "/api/admin/clientes/" + c, null));
    }

    @Test void serviceCatalogSupportsUpdatesAndActiveLifecycle() throws Exception {
        var s = createService();
        expect(200, call("GET", "/api/admin/servicos/" + s, null));
        assertThat(expect(200, call("GET", "/api/admin/servicos", null)).size()).isEqualTo(1);
        expect(422, call("POST", "/api/admin/servicos", serviceBody()));
        var changed = new HashMap<>(serviceBody()); changed.put("valorBase", "175.00");
        expect(200, call("PUT", "/api/admin/servicos/" + s, changed));
        expect(204, call("PATCH", "/api/admin/servicos/" + s + "/inativar", null));
        assertThat(expect(200, call("GET", "/api/admin/servicos", null)).size()).isZero();
        expect(422, call("PATCH", "/api/admin/servicos/" + s + "/inativar", null));
        expect(204, call("PATCH", "/api/admin/servicos/" + s + "/ativar", null));
        expect(422, call("PATCH", "/api/admin/servicos/" + s + "/ativar", null));
        expect(404, call("GET", "/api/admin/servicos/" + UUID.randomUUID(), null));
        var invalid = new HashMap<>(serviceBody()); invalid.put("valorBase", "-1.00");
        expect(400, call("POST", "/api/admin/servicos", invalid));
    }

    @Test void orderReferencesPreventDeletingOrReassigningItsVehicle() throws Exception {
        var f = diagnosisFixture(); var other = createCustomer("11144477735");
        var estimate = expect(200, call("GET", "/api/admin/ordens-servico/" + f.order() + "/calcular-tempo-estimado", null));
        assertThat(estimate.get("tempoEstimadoTotalMinutos").asInt()).isEqualTo(30);
        var duration = expect(200, call("GET", "/api/admin/ordens-servico/" + f.order() + "/tempo-medio-execucao-servicos", null));
        assertThat(duration.get("servicosPendentes").asInt()).isEqualTo(1);
        expect(409, call("DELETE", "/api/admin/veiculos/" + f.vehicle(), null));
        expect(409, call("PUT", "/api/admin/veiculos/" + f.vehicle(), vehicleBody(other, "TST1A23")));
        var vehicle = expect(200, call("GET", "/api/admin/veiculos/" + f.vehicle(), null));
        assertThat(vehicle.get("idCliente").asString()).isEqualTo(f.customer().toString());
        expect(204, call("PATCH", "/api/admin/servicos/" + f.service() + "/inativar", null));
        expect(409, call("POST", "/api/admin/itens-ordem-servico",
                Map.of("idOrdemServico", f.order(), "idServico", f.service(), "valor", "150.00")));
    }
}
