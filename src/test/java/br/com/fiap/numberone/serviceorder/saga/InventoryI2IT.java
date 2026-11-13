package br.com.fiap.numberone.serviceorder.saga;

import br.com.fiap.numberone.serviceorder.infrastructure.messaging.BillingEventCodec;
import br.com.fiap.numberone.serviceorder.application.services.ProcessBillingEvent;
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

@EnabledIfEnvironmentVariable(named = "I2_INVENTORY_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.branch.id=40000000-0000-4000-8000-000000000001",
        "app.security.identity.provider=gateway", "app.security.identity.gateway-trusted=true",
        "app.material-reference.provider=http", "app.saga.recovery-enabled=false",
        "app.billing.sqs.enabled=false", "app.history.enabled=false"
})
class InventoryI2IT {
    private static final String INVENTORY = System.getenv("I2_INVENTORY_URL");
    private static final String TOKEN = System.getenv("OS_TO_INVENTORY_TOKEN");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final String CONSUMPTIONS = "/internal/inventory/consumptions";
    private static final String OPERATIONS = "/internal/inventory/operations/";
    private static final AtomicReference<String> DROP_OPERATION = new AtomicReference<>();
    private static final ConcurrentLinkedQueue<Forwarded> FORWARDED = new ConcurrentLinkedQueue<>();
    private static HttpServer relay;

    @Autowired ObjectMapper json;
    @Autowired ProcessBillingEvent billingInbox;
    @Autowired BillingEventCodec codec;
    @Value("${local.server.port}") int port;
    private final String correlation = UUID.randomUUID().toString();
    private final Map<String, String> admin = Map.of("X-Authenticated-Subject", UUID.randomUUID().toString(),
            "X-Authenticated-Status", "ACTIVE", "X-Authenticated-Roles", "ADMIN",
            "X-Authenticated-Permissions", "", "X-Correlation-Id", correlation);

    @DynamicPropertySource
    static void inventory(DynamicPropertyRegistry registry) throws IOException {
        relay = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        relay.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        relay.createContext("/internal/inventory/", InventoryI2IT::forward);
        relay.start();
        registry.add("app.saga.inventory-url", () -> "http://127.0.0.1:" + relay.getAddress().getPort());
        billing = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        billing.createContext("/internal/budgets/", exchange -> {
            try (exchange) {
                BILLING_CALLS.incrementAndGet();
                billingCheck.accept(exchange);
                var mapper = new ObjectMapper();
                var request = mapper.readTree(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                assertThat(exchange.getRequestHeaders().getFirst("Idempotency-Key")).isEqualTo(request.path("operationId").asString());
                String budget = exchange.getRequestURI().getPath().split("/")[3];
                var response = mapper.writeValueAsBytes(Map.of("operationId", request.path("operationId").asString(),
                        "budgetId", budget, "budgetVersion", request.path("budgetVersion").asInt(), "result", "CANCELLED"));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (Throwable ex) { billingFailure = ex; }
        });
        billing.start();
        registry.add("app.saga.billing-url", () -> "http://127.0.0.1:" + billing.getAddress().getPort());
        registry.add("app.saga.billing-token", () -> "i2-billing");
    }

    @AfterAll static void stopRelay() {
        if (relay != null) { relay.stop(0); }
        if (billing != null) { billing.stop(0); }
    }

    private static void forward(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            if (path.equals(CONSUMPTIONS) && key != null && key.equals(SKIP_CONSUMPTION.get())) {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(503, -1);
                return;
            }
            if (path.equals(CONSUMPTIONS + "/" + BLOCK_REVERSAL.get() + "/reversal")) {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(503, -1);
                return;
            }
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
            String dropReversal = DROP_REVERSAL.get();
            if (path.equals(CONSUMPTIONS + "/" + dropReversal + "/reversal") && upstream.statusCode() == 200
                    && DROP_REVERSAL.compareAndSet(dropReversal, null)) {
                return;
            }
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

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired br.com.fiap.numberone.serviceorder.application.services.SagaRecoveryWorker worker;
    private static final AtomicReference<String> SKIP_CONSUMPTION = new AtomicReference<>();
    private static final AtomicReference<String> DROP_REVERSAL = new AtomicReference<>();
    private static final AtomicReference<String> BLOCK_REVERSAL = new AtomicReference<>();
    private static final java.util.concurrent.atomic.AtomicInteger BILLING_CALLS = new java.util.concurrent.atomic.AtomicInteger();
    private static volatile java.util.function.Consumer<HttpExchange> billingCheck;
    private static volatile Throwable billingFailure;
    private static HttpServer billing;

    @Test void realStockResolvesEveryAttemptBeforeCommercialCancellationAndRecoversLostReversal() throws Exception {
        var material = createMaterial("Peca real I2", true);
        var missing = createMaterial("Peca sem saldo I2", true);
        entry(material, 5);
        var customer = call(201, "POST", "/api/admin/clientes", Map.of(
                "nome", "Cadastro sintetico I2", "tipoDocumento", "PESSOA_FISICA", "documento", "52998224725",
                "email", "fixture@example.invalid", "telefone", "00000000000", "endereco", "Endereco ficticio", "ativo", true));
        var vehicle = call(201, "POST", "/api/admin/veiculos", Map.of("placa", "IOT2A23",
                "marca", "Fixture", "modelo", "Fixture", "ano", 2026, "idCliente", field(customer, "id")));
        var service = call(201, "POST", "/api/admin/servicos", Map.of("codigo", "I2-OS", "nome", "Servico I2",
                "descricao", "Fixture", "tipoServico", "REVISAO", "valorBase", "150.00", "tempoEstimadoMinutos", 30));
        String order = field(call(201, "POST", "/api/admin/ordens-servico", Map.of("idCliente", field(customer, "id"),
                "idVeiculo", field(vehicle, "id"), "descricaoInicial", "Inspecao I2", "descricaoDiagnostico", "Inspecao",
                "dataHoraEntrada", "2026-11-01T10:00:00")), "id");
        String orderPath = "/api/admin/ordens-servico/" + order;
        call(200, "PATCH", orderPath + "/iniciar-diagnostico", Map.of("descricaoDiagnosticoFinal", "Diagnostico I2",
                "dataHoraPrevista", "2026-11-01T14:00:00"));
        String refusedItem = addItem(order, service), closedItem = addItem(order, service), returnedItem = addItem(order, service);
        link(201, refusedItem, missing); link(201, closedItem, material); link(201, returnedItem, material);
        int composition = call(200, "GET", orderPath, null).path("compositionVersion").asInt();
        approve(order, composition);
        String refused = UUID.randomUUID().toString(), closed = UUID.randomUUID().toString(), returned = UUID.randomUUID().toString();
        start(409, refusedItem, refused);
        SKIP_CONSUMPTION.set(closed);
        start(202, closedItem, closed); // transport fails before Inventory receives this attempt
        DROP_OPERATION.set(returned);
        start(202, returnedItem, returned); // Inventory commits, OS has not authorized use
        assertThat(balance(material)).isEqualTo(4);
        assertThat(field(operation(returned), "result")).isEqualTo("CONSUMED");
        var originalMovements = operation(returned).path("movements");
        assertThat(field(operation(refused), "result")).isEqualTo("REJECTED");

        billingCheck = exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer i2-billing");
            assertThat(exchange.getRequestHeaders().getFirst("X-Correlation-Id")).isEqualTo(correlation);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM saga_operation WHERE service_order_id=? AND intent='RESOLVE_STOCK' AND state<>'SUCCEEDED'",
                    Integer.class, UUID.fromString(order))).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM saga_operation WHERE service_order_id=? AND intent='RESOLVE_STOCK'",
                    Integer.class, UUID.fromString(order))).isEqualTo(3);
            try {
                assertThat(field(operation(closed), "result")).isEqualTo("CLOSED");
                assertThat(field(operation(returned), "result")).isEqualTo("REVERSED");
                assertThat(field(operation(refused), "result")).isEqualTo("REJECTED");
                assertThat(balance(material)).isEqualTo(5);
            } catch (Exception ex) { throw new RuntimeException(ex); }
        };
        var cancelled = call(202, "PATCH", orderPath + "/cancelar", Map.of("scope", "FULL", "reason", "Cancelamento sintetico I2",
                "unusedConfirmed", true, "physicallyRemoved", true, "returnConfirmed", true,
                "verifiedAt", java.time.Instant.now().toString(), "evidenceReference", "I2: material conferido e devolvido antes do uso"));
        assertThat(field(cancelled, "state")).isEqualTo("COMPENSATING");
        String closedCompensation = compensationId(closed), returnedCompensation = compensationId(returned);
        BLOCK_REVERSAL.set(closed);
        DROP_REVERSAL.set(returned);
        boolean sawLostReply = false;
        // All intents share one transaction timestamp; their UUID order is deliberately not assumed.
        for (int step = 0; step < 8; step++) {
            ready(order); worker.tick();
            assertThat(BILLING_CALLS.get()).isZero();
            if (operationState(returnedCompensation).equals("AWAITING_RESULT")) {
                sawLostReply = true;
                assertThat(DROP_REVERSAL.get()).isNull();
                assertThat(field(operation(returned), "result")).isEqualTo("REVERSED");
                assertThat(balance(material)).isEqualTo(5);
            }
            if (operationState(closedCompensation).equals("AWAITING_RESULT")) { break; }
        }
        assertThat(operationState(closedCompensation)).isEqualTo("AWAITING_RESULT");
        assertThat(field(call(200, "GET", orderPath + "/compensacao", null), "state")).isEqualTo("COMPENSATING");
        BLOCK_REVERSAL.set(null);
        for (int step = 0; step < 8 && resolvedStock(order) < 3; step++) {
            ready(order); worker.tick();
            assertThat(BILLING_CALLS.get()).isZero();
            if (operationState(returnedCompensation).equals("AWAITING_RESULT")) {
                sawLostReply = true;
                assertThat(DROP_REVERSAL.get()).isNull();
                assertThat(field(operation(returned), "result")).isEqualTo("REVERSED");
                assertThat(balance(material)).isEqualTo(5);
            }
        }
        assertThat(sawLostReply).isTrue();
        assertThat(resolvedStock(order)).isEqualTo(3);
        assertThat(field(operation(closed), "result")).isEqualTo("CLOSED");
        assertThat(BILLING_CALLS.get()).isZero();
        ready(order); worker.tick(); // all stock is resolved; OS now sends CT-09 to simulated Billing
        assertThat(billingFailure).isNull();
        assertThat(BILLING_CALLS.get()).isEqualTo(1);
        var status = call(200, "GET", orderPath + "/compensacao", null);
        assertThat(field(status, "state")).isEqualTo("COMPENSATED");
        assertThat(field(call(200, "GET", orderPath, null), "status")).isEqualTo("CANCELADA");
        assertThat(FORWARDED.stream().filter(r -> r.path().equals(CONSUMPTIONS + "/" + returned + "/reversal"))).hasSize(1);
        assertThat(FORWARDED.stream().filter(r -> r.path().equals(CONSUMPTIONS + "/" + refused + "/reversal"))).isEmpty();
        assertThat(FORWARDED).contains(new Forwarded("POST", CONSUMPTIONS + "/" + returned + "/reversal", returnedCompensation, correlation, 200));
        assertThat(FORWARDED).contains(new Forwarded("GET", OPERATIONS + returned, null, correlation, 200));
        for (String id : new String[]{closed, returned}) {
            var storedRequest = jdbc.queryForObject("SELECT request::text FROM saga_operation WHERE original_operation_id=? AND intent='RESOLVE_STOCK'",
                    String.class, UUID.fromString(id));
            String key = compensationId(id);
            var replay = inventory(200, "POST", CONSUMPTIONS + "/" + id + "/reversal", json.readTree(storedRequest),
                    Map.of("Authorization", "Bearer " + TOKEN, "X-Correlation-Id", correlation, "Idempotency-Key", key));
            assertThat(replay).isEqualTo(operation(id));
        }
        var late = jdbc.queryForObject("SELECT request::text FROM saga_operation WHERE operation_id=?", String.class, UUID.fromString(closed));
        assertThat(field(inventory(409, "POST", CONSUMPTIONS, json.readTree(late), Map.of("Authorization", "Bearer " + TOKEN,
                "X-Correlation-Id", correlation, "Idempotency-Key", closed)), "code")).isEqualTo("ATTEMPT_CLOSED");
        assertThat(balance(material)).isEqualTo(5); assertThat(balance(missing)).isZero();
        var evidence = Map.of("order", order, "material", material, "balances", java.util.List.of(5,4,5,5),
                "closed", operation(closed), "reversed", operation(returned), "rejected", operation(refused),
                "originalMovements", originalMovements, "osStatus", status, "httpTrace", FORWARDED, "billingCalls", BILLING_CALLS.get());
        Files.writeString(Path.of("target/inventory-i2-evidence.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
        System.out.printf("I2 OS order=%s closed=%s/%s reversed=%s/%s rejected=%s balances=[5,4,5,5] Billing calls=1 AFTER all 3 stock attempts%n",
                order, closed, closedCompensation, returned, returnedCompensation, refused);
    }

    private String operationState(String id) {
        return jdbc.queryForObject("SELECT state FROM saga_operation WHERE operation_id=?", String.class, UUID.fromString(id));
    }
    private int resolvedStock(String order) {
        return jdbc.queryForObject("SELECT count(*) FROM saga_operation WHERE service_order_id=? AND intent='RESOLVE_STOCK' AND state='SUCCEEDED'",
                Integer.class, UUID.fromString(order));
    }
    private String compensationId(String original) {
        return jdbc.queryForObject("SELECT operation_id FROM saga_operation WHERE original_operation_id=? AND intent='RESOLVE_STOCK'",
                UUID.class, UUID.fromString(original)).toString();
    }
    private void ready(String order) {
        jdbc.update("UPDATE service_order_saga SET next_attempt_at=CURRENT_TIMESTAMP WHERE service_order_id=?", UUID.fromString(order));
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
            processBillingEvent(event.toString());
        }
    }

    private String createMaterial(String description, boolean active) throws Exception {
        return field(inventory(201, "POST", "/api/admin/itens", Map.of("codigo", "I2-" + UUID.randomUUID(),
                "nome", "Peca I2", "descricao", description, "tipoItem", "PECA", "unidadeMedida", "UNIDADE",
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

    private void processBillingEvent(String body) {
        billingInbox.process(codec.decode(body));
    }
}
