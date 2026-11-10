package br.com.fiap.numberone.serviceorder.saga;

import br.com.fiap.numberone.serviceorder.infrastructure.messaging.BillingInboxHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "I1_INVENTORY_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.branch.id=40000000-0000-4000-8000-000000000001",
        "app.security.identity.provider=gateway", "app.security.identity.gateway-trusted=true",
        "app.material-reference.provider=http", "app.saga.recovery-enabled=false",
        "app.billing.sqs.enabled=false", "app.history.enabled=false"
})
class InventoryI1IT {
    private static final String INVENTORY = System.getenv("I1_INVENTORY_URL");
    private static final String TOKEN = System.getenv("OS_TO_INVENTORY_TOKEN");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final String CONSUMPTIONS = "/internal/inventory/consumptions";
    private static final String OPERATIONS = "/internal/inventory/operations/";
    private static final AtomicReference<String> DROP_OPERATION = new AtomicReference<>();
    private static final ConcurrentLinkedQueue<Forwarded> FORWARDED = new ConcurrentLinkedQueue<>();
    private static HttpServer relay;

    @Autowired ObjectMapper json;
    @Autowired BillingInboxHandler billingInbox;
    @Value("${local.server.port}") int port;
    private final String correlation = UUID.randomUUID().toString();
    private final Map<String, String> admin = Map.of("X-Authenticated-Subject", UUID.randomUUID().toString(),
            "X-Authenticated-Status", "ACTIVE", "X-Authenticated-Roles", "ADMIN",
            "X-Authenticated-Permissions", "", "X-Correlation-Id", correlation);

    @DynamicPropertySource
    static void inventory(DynamicPropertyRegistry registry) throws IOException {
        relay = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        relay.createContext("/internal/inventory/", InventoryI1IT::forward);
        relay.start();
        registry.add("app.saga.inventory-url", () -> "http://127.0.0.1:" + relay.getAddress().getPort());
    }

    @AfterAll static void stopRelay() {
        if (relay != null) { relay.stop(0); }
    }

    private static void forward(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            var request = HttpRequest.newBuilder(URI.create(INVENTORY + exchange.getRequestURI()))
                    .timeout(Duration.ofSeconds(4));
            for (String name : new String[]{"Authorization", "X-Correlation-Id", "Content-Type", "Idempotency-Key"}) {
                var value = exchange.getRequestHeaders().getFirst(name);
                if (value != null) { request.header(name, value); }
            }
            var payload = exchange.getRequestBody().readAllBytes();
            var upstream = HTTP.send(request.method(method, HttpRequest.BodyPublishers.ofByteArray(payload)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            FORWARDED.add(new Forwarded(method, path, key, exchange.getRequestHeaders().getFirst("X-Correlation-Id"),
                    upstream.statusCode()));
            String drop = DROP_OPERATION.get();
            if (path.equals(CONSUMPTIONS) && upstream.statusCode() == 200 && key != null
                    && key.equals(drop) && DROP_OPERATION.compareAndSet(drop, null)) {
                // The real Inventory has committed and sent HTTP 200. OS receives a closed connection.
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(upstream.statusCode(), upstream.body().length);
            exchange.getResponseBody().write(upstream.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Test void linksRealMaterialStartsRetriesAfterReplenishmentAndRecoversLostResponse() throws Exception {
        var material = createMaterial("Peca real I1", true);
        var missing = createMaterial("Peca a repor I1", true);
        var inactive = createMaterial("Peca inativa I1", false);
        entry(material, 3);
        var customer = call(201, "POST", "/api/admin/clientes", Map.of(
                "nome", "Cadastro sintetico I1", "tipoDocumento", "PESSOA_FISICA", "documento", "52998224725",
                "email", "fixture@example.invalid", "telefone", "00000000000", "endereco", "Endereco ficticio", "ativo", true));
        var vehicle = call(201, "POST", "/api/admin/veiculos", Map.of("placa", "IOT1A23",
                "marca", "Fixture", "modelo", "Fixture", "ano", 2026, "idCliente", field(customer, "id")));
        var service = call(201, "POST", "/api/admin/servicos", Map.of("codigo", "I1-OS", "nome", "Servico I1",
                "descricao", "Fixture", "tipoServico", "REVISAO", "valorBase", "150.00", "tempoEstimadoMinutos", 30));
        String order = field(call(201, "POST", "/api/admin/ordens-servico", Map.of("idCliente", field(customer, "id"),
                "idVeiculo", field(vehicle, "id"), "descricaoInicial", "Inspecao I1", "descricaoDiagnostico", "Inspecao",
                "dataHoraEntrada", "2026-11-01T10:00:00")), "id");
        String orderPath = "/api/admin/ordens-servico/" + order;
        call(200, "PATCH", orderPath + "/iniciar-diagnostico", Map.of("descricaoDiagnosticoFinal", "Diagnostico I1",
                "dataHoraPrevista", "2026-11-01T14:00:00"));
        String first = addItem(order, service);
        String shortage = addItem(order, service);
        String lost = addItem(order, service);

        assertThat(field(link(404, first, UUID.randomUUID().toString()), "message")).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(field(link(409, first, inactive), "code")).isEqualTo("MATERIAL_NOT_ACTIVE");
        var allocation = link(201, first, material);
        assertThat(field(allocation, "idMaterial")).isEqualTo(material);
        assertThat(field(allocation, "descricao")).isEqualTo("Peca real I1");
        assertThat(field(allocation, "precoUnitarioReferencia")).isEqualTo("20.40");
        assertThat(FORWARDED).contains(new Forwarded("GET", "/internal/inventory/items/" + material, null, correlation, 200));
        link(201, shortage, material);
        link(201, shortage, missing);
        link(201, lost, material);
        int composition = call(200, "GET", orderPath, null).path("compositionVersion").asInt();
        approve(order, composition);
        assertThat(field(call(200, "GET", orderPath, null), "sagaState")).isEqualTo("READY_FOR_EXECUTION");
        assertThat(balance(material)).isEqualTo(3);
        System.out.printf("I1 OS: vinculo real %s, preco 20.40, orcamento aprovado, saldo 3%n", material);

        String started = UUID.randomUUID().toString();
        assertStarted(start(200, first, started), started, 1);
        assertThat(balance(material)).isEqualTo(2);
        assertThat(FORWARDED).contains(new Forwarded("POST", CONSUMPTIONS, started, correlation, 200));

        String rejected = UUID.randomUUID().toString();
        assertThat(field(start(409, shortage, rejected), "code")).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(field(operation(rejected), "result")).isEqualTo("REJECTED");
        assertThat(operation(rejected).path("movements").size()).isZero();
        assertThat(balance(material)).isEqualTo(2);
        assertThat(balance(missing)).isZero();
        entry(missing, 1);
        start(409, shortage, rejected);
        String retried = UUID.randomUUID().toString();
        assertStarted(start(200, shortage, retried), retried, 2);
        assertThat(field(operation(rejected), "result")).isEqualTo("REJECTED");
        assertThat(balance(material)).isEqualTo(1);
        assertThat(balance(missing)).isZero();
        System.out.printf("I1 OS: inicio %s, recusa %s, reposicao e nova tentativa %s%n", started, rejected, retried);

        String lostOperation = UUID.randomUUID().toString();
        DROP_OPERATION.set(lostOperation);
        assertThat(field(start(202, lost, lostOperation), "operationState")).isEqualTo("AWAITING_RESULT");
        assertThat(DROP_OPERATION.get()).isNull();
        var persisted = operation(lostOperation);
        assertThat(field(persisted, "result")).isEqualTo("CONSUMED");
        assertThat(balance(material)).isZero();

        var recovered = start(200, lost, lostOperation);
        assertStarted(recovered, lostOperation, 1);
        var movements = recovered.path("insumos").get(0).path("movementIds");
        assertThat(movements).isEqualTo(persisted.path("movements"));
        assertThat(FORWARDED).contains(new Forwarded("GET", OPERATIONS + lostOperation, null, correlation, 200));
        assertThat(FORWARDED.stream().filter(req -> lostOperation.equals(req.key()))).hasSize(1);
        assertThat(balance(material)).isZero();
        System.out.printf("I1 OS: resposta CT-05 perdida %s, CT-08 recuperou movimentos %s, um unico POST%n",
                lostOperation, movements);
    }

    private void approve(String order, int composition) throws IOException {
        String budget = UUID.randomUUID().toString();
        for (String type : new String[]{"OrcamentoEnviado", "OrcamentoAprovado"}) {
            var event = (ObjectNode) json.readTree(Files.readString(Path.of("doc/contracts/saga/v2/examples", type + ".json")));
            event.put("eventId", UUID.randomUUID().toString());
            event.put("correlationId", correlation);
            var payload = (ObjectNode) event.path("payload");
            payload.put("serviceOrderId", order).put("budgetId", budget).put("budgetVersion", 1)
                    .put("compositionVersion", composition);
            billingInbox.handle(event.toString());
        }
    }

    private String createMaterial(String description, boolean active) throws Exception {
        return field(inventory(201, "POST", "/api/admin/itens", Map.of("codigo", "I1-" + UUID.randomUUID(),
                "nome", "Peca I1", "descricao", description, "tipoItem", "PECA", "unidadeMedida", "UNIDADE",
                "quantidadeEstoque", 0, "estoqueMinimo", 0, "custoUnitario", "10.00", "precoVenda", "20.40", "ativo", active),
                admin), "id");
    }

    private void entry(String material, int quantity) throws Exception {
        inventory(201, "POST", "/api/admin/estoque/entrada", Map.of("idItemEstoque", material,
                "origemMovimentacao", "COMPRA", "quantidade", quantity), admin);
    }

    private int balance(String material) throws Exception {
        return inventory(200, "GET", "/api/admin/itens/" + material, null, admin).path("quantidadeEstoque").asInt();
    }

    private JsonNode operation(String key) throws Exception {
        return inventory(200, "GET", OPERATIONS + key, null,
                Map.of("Authorization", "Bearer " + TOKEN, "X-Correlation-Id", correlation));
    }

    private String addItem(String order, JsonNode service) throws Exception {
        return field(call(201, "POST", "/api/admin/itens-ordem-servico",
                Map.of("idOrdemServico", order, "idServico", field(service, "id"), "valor", "150.00")), "id");
    }

    private JsonNode link(int status, String item, String material) throws Exception {
        return call(status, "POST", "/api/admin/itens-ordem-servico/" + item + "/insumos",
                Map.of("idMaterial", material, "quantidadePrevista", 1));
    }

    private JsonNode start(int status, String item, String key) throws Exception {
        var headers = new java.util.HashMap<>(admin);
        headers.put("Idempotency-Key", key);
        return request(status, "PATCH", osUrl() + "/api/admin/itens-ordem-servico/" + item + "/iniciar", null, headers);
    }

    private static void assertStarted(JsonNode body, String operation, int materials) {
        assertThat(field(body, "operationState")).isEqualTo("CONFIRMED");
        assertThat(field(body, "startOperationId")).isEqualTo(operation);
        assertThat(body.path("insumos").size()).isEqualTo(materials);
        for (var material : body.path("insumos")) {
            assertThat(material.path("consumoConfirmado").asBoolean()).isTrue();
        }
    }

    private JsonNode call(int status, String method, String path, Object body) throws Exception {
        return request(status, method, osUrl() + path, body, admin);
    }

    private String osUrl() { return "http://127.0.0.1:" + port; }

    private JsonNode inventory(int status, String method, String path, Object body, Map<String, String> headers) throws Exception {
        return request(status, method, INVENTORY + path, body, headers);
    }

    private JsonNode request(int status, String method, String url, Object body, Map<String, String> headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15));
        headers.forEach(request::header);
        var payload = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            request.header("Content-Type", "application/json");
            payload = HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        }
        var response = HTTP.send(request.method(method, payload).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s: %s", method, url, response.body()).isEqualTo(status);
        return json.readTree(response.body());
    }

    private static String field(JsonNode body, String name) { return body.path(name).asString(); }

    private record Forwarded(String method, String path, String key, String correlation, int status) { }
}
