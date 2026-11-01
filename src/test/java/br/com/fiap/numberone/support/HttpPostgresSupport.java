package br.com.fiap.numberone.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class HttpPostgresSupport extends PostgresSupport {
    @Value("${local.server.port}") protected int port;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    protected final HttpClient http = HttpClient.newHttpClient();
    protected static final UUID BRANCH = UUID.fromString("40000000-0000-4000-8000-000000000001");
    protected static final String MATERIAL = "80000000-0000-4000-8000-000000000001";

    @BeforeEach void cleanEphemeralTestDatabase() {
        jdbc.execute("TRUNCATE material_movement_reference, material_previsto, item_ordem_servico, ordem_servico, veiculo, cliente, servico_automotivo CASCADE");
    }

    protected record Reply(int status, JsonNode body) { }
    protected Map<String, String> admin() {
        return Map.of("X-Authenticated-Subject", "fixture-admin", "X-Authenticated-Status", "ACTIVE",
                "X-Authenticated-Roles", "ADMIN", "X-Authenticated-Permissions", "");
    }
    protected Map<String, String> customer(UUID id) {
        return Map.of("X-Authenticated-Subject", "fixture-customer", "X-Authenticated-Status", "ACTIVE",
                "X-Authenticated-Roles", "CUSTOMER", "X-Authenticated-Permissions", "SERVICE_ORDER_TRACK_OWN",
                "X-Authenticated-Customer-Id", id.toString());
    }
    protected Reply call(String method, String path, Object body) throws Exception { return call(method, path, body, admin()); }
    protected Reply call(String method, String path, Object body, Map<String, String> headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("X-Correlation-Id", "test-phase2").header("Content-Type", "application/json");
        headers.forEach(request::header);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), json.readTree(response.body().isBlank() ? "null" : response.body()));
    }
    protected JsonNode expect(int status, Reply reply) {
        // Status only in assertion failures: do not dump request/response personal fields.
        assertThat(reply.status()).as("HTTP status").isEqualTo(status);
        return reply.body();
    }
    protected UUID id(JsonNode response) { return UUID.fromString(response.get("id").asString()); }

    protected Map<String, Object> customerBody(String document) {
        return Map.of("nome", "Cadastro sintetico", "tipoDocumento", "PESSOA_FISICA", "documento", document,
                "email", "fixture@example.invalid", "telefone", "00000000000", "endereco", "Endereco ficticio", "ativo", true);
    }
    protected UUID createCustomer() throws Exception { return createCustomer("52998224725"); }
    protected UUID createCustomer(String document) throws Exception {
        return id(expect(201, call("POST", "/api/admin/clientes", customerBody(document))));
    }
    protected Map<String, Object> vehicleBody(UUID customer, String plate) {
        return Map.of("placa", plate, "marca", "Fixture", "modelo", "Fixture", "ano", 2026, "idCliente", customer);
    }
    protected UUID createVehicle(UUID customer) throws Exception {
        return id(expect(201, call("POST", "/api/admin/veiculos", vehicleBody(customer, "TST1A23"))));
    }
    protected Map<String, Object> serviceBody() {
        return Map.of("codigo", "DEMO-1", "nome", "Servico demonstrativo", "descricao", "Fixture",
                "tipoServico", "REVISAO", "valorBase", "150.00", "tempoEstimadoMinutos", 30);
    }
    protected UUID createService() throws Exception { return id(expect(201, call("POST", "/api/admin/servicos", serviceBody()))); }
    protected Map<String, Object> orderBody(UUID customer, UUID vehicle) {
        return Map.of("idCliente", customer, "idVeiculo", vehicle, "descricaoInicial", "Inspecao",
                "descricaoDiagnostico", "Inspecao prevista", "dataHoraEntrada", "2026-11-01T10:00:00");
    }
    protected UUID createOrder(UUID customer, UUID vehicle) throws Exception {
        return id(expect(201, call("POST", "/api/admin/ordens-servico", orderBody(customer, vehicle))));
    }
    protected JsonNode diagnose(UUID order) throws Exception {
        return expect(200, call("PATCH", "/api/admin/ordens-servico/" + order + "/iniciar-diagnostico",
                Map.of("descricaoDiagnosticoFinal", "Diagnostico ficticio", "dataHoraPrevista", "2026-11-01T14:00:00")));
    }
    protected UUID addItem(UUID order, UUID service) throws Exception {
        return id(expect(201, call("POST", "/api/admin/itens-ordem-servico",
                Map.of("idOrdemServico", order, "idServico", service, "valor", "150.00"))));
    }
    protected UUID addMaterial(UUID item) throws Exception {
        return id(expect(201, call("POST", "/api/admin/itens-ordem-servico/" + item + "/insumos",
                Map.of("idMaterial", MATERIAL, "quantidadePrevista", 1))));
    }
    protected record Fixture(UUID customer, UUID vehicle, UUID service, UUID order, UUID item) { }
    protected Fixture diagnosisFixture() throws Exception {
        var c = createCustomer(); var v = createVehicle(c); var s = createService(); var o = createOrder(c, v);
        diagnose(o); return new Fixture(c, v, s, o, addItem(o, s));
    }
}
