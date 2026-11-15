package br.com.fiap.numberone.serviceorder.api;

import br.com.fiap.numberone.support.HttpPostgresSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IntegerQuantityTransportIT extends HttpPostgresSupport {
    @Test void fractionalServiceQuantityIsRejectedWithoutChangingComposition() throws Exception {
        var fixture = diagnosisFixture();
        Integer version = jdbc.queryForObject("SELECT composition_version FROM ordem_servico WHERE id=?",
                Integer.class, fixture.order());
        expect(400, call("POST", "/api/admin/itens-ordem-servico", Map.of(
                "idOrdemServico", fixture.order(), "idServico", fixture.service(),
                "valor", "100.00", "quantidade", new BigDecimal("1.9"))));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM item_ordem_servico WHERE id_ordem_servico=?",
                Integer.class, fixture.order())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT composition_version FROM ordem_servico WHERE id=?",
                Integer.class, fixture.order())).isEqualTo(version);
        assertThat(expect(201, call("POST", "/api/admin/itens-ordem-servico", Map.of(
                "idOrdemServico", fixture.order(), "idServico", fixture.service(),
                "valor", "100.00", "quantidade", 2))).get("quantidade").asInt()).isEqualTo(2);
    }

    @Test void fractionalMaterialQuantityIsRejectedWithoutCreatingAllocation() throws Exception {
        var fixture = diagnosisFixture();
        String path = "/api/admin/itens-ordem-servico/" + fixture.item() + "/insumos";
        expect(400, call("POST", path, Map.of("idMaterial", MATERIAL,
                "quantidadePrevista", new BigDecimal("1.9"))));
        assertThat(expect(200, call("GET", path, null)).size()).isZero();
        assertThat(expect(201, call("POST", path, Map.of("idMaterial", MATERIAL,
                "quantidadePrevista", 2))).get("quantidadePrevista").asInt()).isEqualTo(2);
    }
}
