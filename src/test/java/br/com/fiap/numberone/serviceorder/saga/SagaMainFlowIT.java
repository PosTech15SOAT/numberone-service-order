package br.com.fiap.numberone.serviceorder.saga;

import br.com.fiap.numberone.support.HttpPostgresSupport;
import br.com.fiap.numberone.serviceorder.infrastructure.messaging.BillingInboxHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** PostgreSQL + real HTTP OS/CT-05/07/10. Billing and Inventory are explicitly simulated. */
class SagaMainFlowIT extends HttpPostgresSupport {
    private static final HttpServer PARTICIPANTS = server();
    private static volatile java.util.function.Function<com.sun.net.httpserver.HttpExchange, Response> responder;
    private record Response(int status, String body) { }
    private static HttpServer server() {
        try {
            var s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            s.createContext("/", exchange -> {
                try {
                    var r = responder.apply(exchange); var bytes = r.body().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(r.status(), bytes.length); exchange.getResponseBody().write(bytes);
                } finally { exchange.close(); }
            });
            s.start(); return s;
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource static void participants(DynamicPropertyRegistry p) {
        p.add("app.saga.billing-url", () -> "http://127.0.0.1:" + PARTICIPANTS.getAddress().getPort());
        p.add("app.saga.inventory-url", () -> "http://127.0.0.1:" + PARTICIPANTS.getAddress().getPort());
        p.add("app.saga.billing-token", () -> "fixture-os-billing");
        p.add("app.saga.inventory-token", () -> "fixture-os-inventory");
        p.add("app.saga.billing-inbound-token", () -> "fixture-billing-os");
    }
    @Autowired BillingInboxHandler inbox;
    @Autowired br.com.fiap.numberone.serviceorder.application.services.SagaRecoveryWorker recovery;
    private final Map<UUID,JsonNode> inventory = new ConcurrentHashMap<>();
    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private static final List<Map<String,Object>> TRACE = new CopyOnWriteArrayList<>();
    private final AtomicInteger settlements = new AtomicInteger();
    private final AtomicInteger budgetLookups = new AtomicInteger();
    private volatile String stockMode, settlementMode;
    private volatile ObjectNode currentBudget;
    private volatile Runnable duringConsume, duringSettlement, duringBudget;
    private volatile Throwable serverFailure;

    @BeforeEach void configureParticipants() {
        inventory.clear(); requests.clear(); settlements.set(0); budgetLookups.set(0);
        stockMode = "CONSUMED"; settlementMode = "UNSETTLED"; currentBudget = null;
        duringConsume = () -> {}; duringSettlement = () -> {}; duringBudget = () -> {}; serverFailure = null;
        responder = exchange -> {
            try {
                UUID.fromString(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/internal/inventory/consumptions")) {
                    assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                    assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer fixture-os-inventory");
                    var request = json.readTree(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                    var operation = UUID.fromString(request.get("operationId").asString()); requests.add(request);
                    assertThat(exchange.getRequestHeaders().getFirst("Idempotency-Key")).isEqualTo(operation.toString());
                    assertThat(jdbc.queryForObject("SELECT state FROM saga_operation WHERE operation_id=?", String.class, operation))
                            .isIn("START_PENDING", "AWAITING_RESULT"); // Visible from a separate DB connection before REST.
                    assertThat(request.get("responsibleUserId").asString()).isEqualTo(admin().get("X-Authenticated-Subject"));
                    trace("inventory.openapi.yaml", "ConsumptionRequest", request);
                    duringConsume.run();
                    if (stockMode.equals("UNKNOWN")) { return new Response(404, "{\"result\":\"UNKNOWN\"}"); }
                    if (stockMode.equals("PENDING")) { return new Response(202, "{\"result\":\"PENDING\"}"); }
                    boolean reject = stockMode.equals("REJECTED");
                    var result = inventory.computeIfAbsent(operation, op -> {
                        var body = fixture(reject ? "insufficient-stock-direct.json" : "consumed-direct.json");
                        body.put("operationId", operation.toString()); return body;
                    });
                    if (stockMode.equals("LOST_RESPONSE")) { return new Response(503, "{}"); }
                    trace("inventory.openapi.yaml", reject ? "Rejected" : "Consumed", result);
                    return new Response(result.path("result").asString().equals("REJECTED") ? 409 : 200, result.toString());
                }
                if (path.startsWith("/internal/inventory/operations/")) {
                    var op=UUID.fromString(path.substring(path.lastIndexOf('/')+1));
                    var found=inventory.get(op);
                    return found==null ? new Response(404,json.writeValueAsString(Map.of("operationId",op,"result","UNKNOWN"))) : new Response(200,found.toString());
                }
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer fixture-os-billing");
                if (path.endsWith("/current")) {
                    budgetLookups.incrementAndGet(); duringBudget.run();
                    if (currentBudget != null) { trace("billing.openapi.yaml", "CurrentBudget", currentBudget); }
                    return currentBudget == null ? new Response(404, "{\"code\":\"BUDGET_NOT_FOUND\"}") : new Response(200, currentBudget.toString());
                }
                if (path.endsWith("/settlement")) {
                    settlements.incrementAndGet(); duringSettlement.run();
                    if (settlementMode.equals("UNAVAILABLE")) { return new Response(503, "{}"); }
                    if (settlementMode.equals("TIMEOUT")) { Thread.sleep(5500); }
                    var id = path.split("/")[4];
                    var body = fixture(settlementMode.equals("PAID") ? "settled.json" : "unsettled.json");
                    body.put("serviceOrderId", id); body.put("verifiedAt", Instant.now().toString());
                    trace("billing.openapi.yaml", "Settlement", body);
                    return new Response(200, body.toString());
                }
                return new Response(404, "{}");
            } catch (Throwable ex) { serverFailure = ex; return new Response(500, "{}"); }
        };
    }
    @AfterEach void verifyParticipantAssertions() { assertThat(serverFailure).isNull(); }
    @AfterAll static void evidence() throws Exception {
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/phase4-contract-trace.json"), new tools.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(TRACE));
    }
    private ObjectNode fixture(String name) {
        try { return (ObjectNode) json.readTree(Files.readString(Path.of("doc/contracts/saga/v2/http-examples", name))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private void trace(String file, String schema, JsonNode body) { TRACE.add(Map.of("file", file, "schema", schema, "body", body)); }
    private int composition(UUID order) { return jdbc.queryForObject("SELECT composition_version FROM ordem_servico WHERE id=?", Integer.class, order); }
    private String saga(UUID order) { return jdbc.queryForObject("SELECT state FROM service_order_saga WHERE service_order_id=?", String.class, order); }
    private String event(String type, UUID order, UUID budget, int revision, int composition) throws Exception {
        var e = (ObjectNode) json.readTree(Files.readString(Path.of("doc/contracts/saga/v2/examples", type + ".json")));
        e.put("eventId", UUID.randomUUID().toString());
        var p = (ObjectNode)e.get("payload"); p.put("serviceOrderId", order.toString());
        p.put("budgetId", budget.toString()); p.put("budgetVersion", revision); p.put("compositionVersion", composition);
        return e.toString();
    }
    private UUID approve(UUID order) throws Exception {
        UUID budget = UUID.randomUUID();
        inbox.handle(event("OrcamentoEnviado", order, budget, 1, composition(order)));
        inbox.handle(event("OrcamentoAprovado", order, budget, 1, composition(order)));
        assertThat(saga(order)).isEqualTo("READY_FOR_EXECUTION"); return budget;
    }
    private Reply start(UUID item, UUID key) throws Exception {
        var headers = new HashMap<>(admin()); if (key != null) { headers.put("Idempotency-Key", key.toString()); }
        return call("PATCH", "/api/admin/itens-ordem-servico/" + item + "/iniciar", null, headers);
    }
    private JsonNode order(UUID id) throws Exception { return expect(200, call("GET", "/api/admin/ordens-servico/" + id, null)); }
    private Reply action(UUID order, String action) throws Exception { return call("PATCH", "/api/admin/ordens-servico/" + order + "/" + action, null); }
    private void completeItem(UUID item) throws Exception { expect(200, call("PATCH", "/api/admin/itens-ordem-servico/" + item + "/concluir", null)); }
    private void complete(Fixture f) throws Exception {
        approve(f.order()); expect(200, start(f.item(), null)); completeItem(f.item()); expect(200, action(f.order(), "concluir"));
    }
    private JsonNode internal(UUID order, String suffix, int status) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/internal/service-orders/" + order + suffix))
                .header("Authorization", "Bearer fixture-billing-os").header("X-Correlation-Id", UUID.randomUUID().toString()).GET().build();
        var r = http.send(req, HttpResponse.BodyHandlers.ofString()); assertThat(r.statusCode()).isEqualTo(status);
        return json.readTree(r.body());
    }
    private String payment(UUID order, UUID payment, int version, boolean refunded) throws Exception {
        var e = (ObjectNode)json.readTree(Files.readString(Path.of("doc/contracts/saga/v2/examples", refunded ? "PagamentoAtualizado.json" : "PagamentoConfirmado.json")));
        e.put("eventId", UUID.randomUUID().toString()); var p = (ObjectNode)e.get("payload");
        p.put("serviceOrderId", order.toString()); p.put("paymentId", payment.toString()); p.put("paymentVersion", version); return e.toString();
    }

    @Test void mainFlowTwoMaterialItemsAndOneWithoutMaterialsFinishesBeforeEmployeePayment() throws Exception {
        var f = diagnosisFixture(); addMaterial(f.item()); var second = addItem(f.order(), f.service()); addMaterial(second);
        var noMaterials = addItem(f.order(), f.service());
        trace("os-internal.openapi.yaml", "QuotationContext", internal(f.order(), "/quotation-context", 200));
        approve(f.order()); trace("os-api.openapi.yaml", "ReadyOrder", expect(200, action(f.order(), "iniciar")));
        assertThat(requests).isEmpty();
        var first = expect(200, start(f.item(), UUID.randomUUID())); trace("os-api.openapi.yaml", "StartedItem", first);
        assertThat(saga(f.order())).isEqualTo("IN_EXECUTION");
        duringConsume = () -> assertThat(saga(f.order())).isEqualTo("IN_EXECUTION");
        expect(200, start(second, UUID.randomUUID())); expect(200, start(noMaterials, null));
        assertThat(requests).hasSize(2); assertThat(inventory).hasSize(2);
        expect(200, start(f.item(), null)); assertThat(requests).hasSize(2);
        for (var item : List.of(f.item(), second, noMaterials)) { completeItem(item); }
        var completed = expect(200, action(f.order(), "concluir"));
        assertThat(completed.get("status").asString()).isEqualTo("FINALIZADA");
        assertThat(completed.get("pagamentoConfirmado").asBoolean()).isFalse(); assertThat(saga(f.order())).isEqualTo("WAITING_PAYMENT");
        var ct02 = internal(f.order(), "", 200); trace("os-internal.openapi.yaml", "ServiceOrderState", ct02);
        assertThat(ct02.get("status").asString()).isEqualTo("COMPLETED");
        expect(409, action(f.order(), "entregar"));
        // Simulated employee payment in Billing AFTER CT-02A says COMPLETED.
        settlementMode = "PAID";
        inbox.handle(payment(f.order(), UUID.randomUUID(), 1, false));
        assertThat(saga(f.order())).isEqualTo("READY_FOR_DELIVERY");
        int before = settlements.get();
        var delivered = expect(200, action(f.order(), "entregar")); trace("os-api.openapi.yaml", "DeliveredOrder", delivered);
        assertThat(delivered.get("status").asString()).isEqualTo("ENTREGUE"); assertThat(saga(f.order())).isEqualTo("COMPLETED");
        assertThat(settlements.get()).isEqualTo(before + 1);
        expect(200, action(f.order(), "entregar")); assertThat(settlements.get()).isEqualTo(before + 1);
    }
    @Test void lostConsumptionResponseRemainsPendingAndRepeatRecoversSameOperationWithoutSecondDebit() throws Exception {
        var f = diagnosisFixture(); addMaterial(f.item()); approve(f.order()); stockMode = "LOST_RESPONSE";
        UUID key = UUID.randomUUID(); var pending = expect(202, start(f.item(), key)); trace("os-api.openapi.yaml", "PendingItem", pending);
        assertThat(pending.get("dataHoraInicio").isNull()).isTrue();
        assertThat(saga(f.order())).isEqualTo("START_PENDING");
        expect(409, call("PATCH", "/api/admin/itens-ordem-servico/" + f.item() + "/concluir", null));
        expect(409, action(f.order(), "concluir"));
        assertThat(expect(200, call("GET", "/api/public/ordens-servico/" + f.order() + "/acompanhamento", null))
                .get("itensServico").get(0).get("startOperationId").asString()).isEqualTo(key.toString());
        expect(200, start(f.item(), UUID.randomUUID())); // CT-08 recovers the durable debit.
        stockMode = "CONSUMED"; var confirmed = expect(200, start(f.item(), null));
        assertThat(confirmed.get("startOperationId").asString()).isEqualTo(key.toString());
        assertThat(inventory).hasSize(1); assertThat(requests.stream().map(n -> n.get("operationId").asString()).distinct()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM saga_operation", Integer.class)).isEqualTo(1);
    }
    @Test void unknownAndPendingNeverStartAndInsufficientStockNeedsExplicitNewAttempt() throws Exception {
        var f = diagnosisFixture(); addMaterial(f.item()); approve(f.order());
        stockMode = "UNKNOWN"; UUID key = UUID.randomUUID(); expect(202, start(f.item(), key));
        stockMode = "PENDING"; expect(202, start(f.item(), null));
        stockMode = "REJECTED"; expect(409, start(f.item(), key));
        assertThat(saga(f.order())).isEqualTo("READY_FOR_EXECUTION");
        var item = order(f.order()).get("itensServico").get(0);
        assertThat(item.get("status").asString()).isEqualTo("AGUARDANDO_PECAS_E_INSUMOS");
        stockMode = "CONSUMED"; int calls = requests.size();
        expect(409, start(f.item(), null)); expect(409, start(f.item(), key)); assertThat(requests).hasSize(calls);
        var next = expect(200, start(f.item(), UUID.randomUUID()));
        assertThat(next.get("startOperationId").asString()).isNotEqualTo(key.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM saga_operation", Integer.class)).isEqualTo(2);
    }
    @Test void obsoleteApprovalCannotAuthorizeChangedCompositionAndOutOfOrderDecisionRecoversThroughCt10() throws Exception {
        var f = diagnosisFixture(); int old = composition(f.order()); addMaterial(f.item()); UUID budget = UUID.randomUUID();
        assertThat(inbox.handle(event("OrcamentoAprovado", f.order(), budget, 1, old))).isEqualTo(BillingInboxHandler.Outcome.OBSOLETE);
        assertThat(budgetLookups.get()).isZero(); expect(409, start(f.item(), null));
        var approval = event("OrcamentoAprovado", f.order(), budget, 1, composition(f.order()));
        assertThatThrownBy(() -> inbox.handle(approval)).hasMessage("DEPENDENCY_UNAVAILABLE");
        currentBudget = fixture("current-budget.json"); currentBudget.put("serviceOrderId", f.order().toString());
        currentBudget.put("budgetId", budget.toString()); currentBudget.put("budgetVersion", 1);
        currentBudget.put("compositionVersion", composition(f.order()));
        currentBudget.put("amount", json.readTree(approval).get("payload").get("approvedAmount").asString());
        inbox.handle(approval); assertThat(saga(f.order())).isEqualTo("READY_FOR_EXECUTION");
        assertThat(inbox.handle(approval)).isEqualTo(BillingInboxHandler.Outcome.DUPLICATE);
        assertThat(inbox.handle(event("OrcamentoEnviado", f.order(), budget, 1, composition(f.order())))).isEqualTo(BillingInboxHandler.Outcome.IGNORED);
        assertThatThrownBy(() -> inbox.handle(event("OrcamentoRejeitado", f.order(), budget, 1, composition(f.order())))).hasMessage("BUDGET_DECISION_CONFLICT");
    }
    @Test void rejectedRevisionCanBeReplacedButOldApprovalNeverRegressesCurrentRevision() throws Exception {
        var f = diagnosisFixture(); UUID first = UUID.randomUUID(), next = UUID.randomUUID(); int c = composition(f.order());
        inbox.handle(event("OrcamentoEnviado", f.order(), first, 1, c));
        inbox.handle(event("OrcamentoRejeitado", f.order(), first, 1, c)); assertThat(saga(f.order())).isEqualTo("WAITING_REVISION");
        inbox.handle(event("OrcamentoEnviado", f.order(), next, 2, c));
        assertThat(inbox.handle(event("OrcamentoAprovado", f.order(), first, 1, c))).isEqualTo(BillingInboxHandler.Outcome.OBSOLETE);
        expect(409, start(f.item(), null));
        inbox.handle(event("OrcamentoAprovado", f.order(), next, 2, c)); expect(200, start(f.item(), null));
    }
    @Test void newerCurrentDecisionCanOvertakePreviousRejectionOnStandardQueue() throws Exception {
        var f = diagnosisFixture(); UUID first = UUID.randomUUID(), next = UUID.randomUUID(); int c = composition(f.order());
        inbox.handle(event("OrcamentoEnviado", f.order(), first, 1, c));
        currentBudget = fixture("current-budget.json"); currentBudget.put("serviceOrderId", f.order().toString());
        currentBudget.put("budgetId", next.toString()); currentBudget.put("budgetVersion", 2); currentBudget.put("compositionVersion", c);
        var approval = event("OrcamentoAprovado", f.order(), next, 2, c);
        currentBudget.put("amount", json.readTree(approval).get("payload").get("approvedAmount").asString());
        inbox.handle(approval); assertThat(saga(f.order())).isEqualTo("READY_FOR_EXECUTION");
        assertThat(inbox.handle(event("OrcamentoRejeitado", f.order(), first, 1, c))).isEqualTo(BillingInboxHandler.Outcome.OBSOLETE);
        expect(200, start(f.item(), null));
    }
    @Test void deliveryUsesOfficialSettlementEvenBeforeFinancialEventAndRefundDoesNotEraseDelivery() throws Exception {
        var f = diagnosisFixture(); complete(f); settlementMode = "PAID";
        expect(200, action(f.order(), "entregar")); assertThat(order(f.order()).get("pagamentoConfirmado").asBoolean()).isFalse();
        UUID payment = UUID.randomUUID(); inbox.handle(payment(f.order(), payment, 2, true));
        assertThat(inbox.handle(payment(f.order(), payment, 1, false))).isEqualTo(BillingInboxHandler.Outcome.OBSOLETE);
        var o = order(f.order()); assertThat(o.get("status").asString()).isEqualTo("ENTREGUE");
        assertThat(o.get("payments").get(0).get("paymentStatus").asString()).isEqualTo("REFUNDED");
        assertThat(saga(f.order())).isEqualTo("COMPLETED");
    }
    @Test void unavailableTimeoutAndFalseSettlementBlockDeliveryEvenWithPaidProjection() throws Exception {
        var f = diagnosisFixture(); complete(f); settlementMode = "UNAVAILABLE";
        inbox.handle(payment(f.order(), UUID.randomUUID(), 1, false));
        assertThat(order(f.order()).get("settlementPending").asBoolean()).isTrue();
        expect(503, action(f.order(), "entregar"));
        settlementMode = "UNSETTLED"; expect(409, action(f.order(), "entregar"));
        settlementMode = "TIMEOUT"; expect(503, action(f.order(), "entregar"));
        assertThat(order(f.order()).get("status").asString()).isEqualTo("FINALIZADA");
        assertThat(order(f.order()).get("dataHoraEntrega").isNull()).isTrue();
    }
    @Test void cancellationDuringConsumptionRecordsFactButDoesNotConfirmStart() throws Exception {
        var f = diagnosisFixture(); addMaterial(f.item()); approve(f.order());
        duringConsume = () -> jdbc.update("UPDATE service_order_saga SET cancellation_in_progress=true,state='COMPENSATING',version=version+1 WHERE service_order_id=?", f.order());
        expect(202, start(f.item(), UUID.randomUUID()));
        assertThat(jdbc.queryForObject("SELECT late_result->>'result' FROM saga_operation", String.class)).isEqualTo("CONSUMED");
        assertThat(order(f.order()).get("itensServico").get(0).get("dataHoraInicio").isNull()).isTrue();
        int calls = requests.size(); expect(409, start(f.item(), null)); assertThat(requests).hasSize(calls);
        assertThat(internal(f.order(), "", 200).get("cancellationInProgress").asBoolean()).isTrue();
    }
    @Test void recoveryOfPaymentVerificationPreservesCompletedRepairAndBlocksUnpaidDelivery() throws Exception {
        var f=diagnosisFixture();complete(f);settlementMode="UNAVAILABLE";
        inbox.handle(payment(f.order(),UUID.randomUUID(),1,false));
        assertThat(jdbc.queryForObject("SELECT settlement_pending FROM service_order_saga WHERE service_order_id=?",Boolean.class,f.order())).isTrue();
        settlementMode="UNSETTLED";recovery.tick();
        assertThat(saga(f.order())).isEqualTo("WAITING_PAYMENT");
        assertThat(order(f.order()).get("status").asString()).isEqualTo("FINALIZADA");
        expect(409,action(f.order(),"entregar"));
        assertThat(jdbc.queryForObject("SELECT settlement_pending FROM service_order_saga WHERE service_order_id=?",Boolean.class,f.order())).isFalse();
    }
    @Test void cancellationOrLocalVersionChangeDuringSettlementPreventsDeliveryCommit() throws Exception {
        var f = diagnosisFixture(); complete(f); settlementMode = "PAID";
        duringSettlement = () -> jdbc.update("UPDATE ordem_servico SET version=version+1 WHERE id=?", f.order());
        expect(409, action(f.order(), "entregar"));
        duringSettlement = () -> jdbc.update("UPDATE service_order_saga SET cancellation_in_progress=true,version=version+1 WHERE service_order_id=?", f.order());
        expect(409, action(f.order(), "entregar")); assertThat(order(f.order()).get("status").asString()).isEqualTo("FINALIZADA");
    }
    @Test void simultaneousStartReusesOneDurableAttemptAndForeignKeyCannotBeReusedForAnotherItem() throws Exception {
        var f = diagnosisFixture(); addMaterial(f.item()); UUID second = addItem(f.order(), f.service()); approve(f.order());
        UUID key = UUID.randomUUID();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> start(f.item(), key)); var b = pool.submit(() -> start(f.item(), key));
            assertThat(a.get(15, TimeUnit.SECONDS).status()).isIn(200, 202); assertThat(b.get(15, TimeUnit.SECONDS).status()).isIn(200, 202);
        }
        assertThat(inventory).hasSize(1); assertThat(jdbc.queryForObject("SELECT count(*) FROM saga_operation", Integer.class)).isEqualTo(1);
        expect(409, start(second, key));
    }
    @Test void internalRoutesRequirePairTokenAndExactHistoricalComposition() throws Exception {
        var f = diagnosisFixture(); int version = composition(f.order()); var old = internal(f.order(), "/quotation-context?compositionVersion=" + version, 200);
        addMaterial(f.item()); assertThat(internal(f.order(), "/quotation-context?compositionVersion=" + version, 200)).isEqualTo(old);
        internal(f.order(), "/quotation-context?compositionVersion=999", 404);
        expect(401, call("GET", "/internal/service-orders/" + f.order(), null));
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/internal/auth/customer-lookup"))
                .header("Authorization", "Bearer fixture-billing-os").header("X-Correlation-Id", UUID.randomUUID().toString()).POST(HttpRequest.BodyPublishers.noBody()).build();
        assertThat(http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }
    @Test void currentBudgetRecoveryRevalidatesLocalVersionAndReplaysAfterRollback() throws Exception {
        var f = diagnosisFixture(); UUID budget = UUID.randomUUID();
        var approval = event("OrcamentoAprovado", f.order(), budget, 1, composition(f.order()));
        currentBudget = fixture("current-budget.json"); currentBudget.put("serviceOrderId", f.order().toString());
        currentBudget.put("budgetId", budget.toString()); currentBudget.put("compositionVersion", composition(f.order()));
        currentBudget.put("amount", json.readTree(approval).get("payload").get("approvedAmount").asString());
        duringBudget = () -> jdbc.update("UPDATE ordem_servico SET version=version+1 WHERE id=?", f.order());
        assertThatThrownBy(() -> inbox.handle(approval)).hasMessage("BUDGET_CONTEXT_CHANGED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inbox_event", Integer.class)).isZero();
        assertThat(saga(f.order())).isEqualTo("IN_DIAGNOSIS");
        duringBudget = () -> {}; inbox.handle(approval); assertThat(saga(f.order())).isEqualTo("READY_FOR_EXECUTION");
    }
    @Test void financialProjectionCanPrecedeApprovalAndRefundRequiresAnotherOfficialCheck() throws Exception {
        var f = diagnosisFixture(); UUID paid = UUID.randomUUID();
        inbox.handle(payment(f.order(), paid, 1, false));
        assertThat(order(f.order()).get("pagamentoConfirmado").asBoolean()).isTrue();
        assertThat(saga(f.order())).isEqualTo("IN_DIAGNOSIS"); expect(409, start(f.item(), null));
        complete(f); settlementMode = "PAID";
        var another = payment(f.order(), UUID.randomUUID(), 1, false); inbox.handle(another);
        assertThat(saga(f.order())).isEqualTo("READY_FOR_DELIVERY");
        settlementMode = "UNSETTLED"; inbox.handle(payment(f.order(), paid, 2, true));
        assertThat(saga(f.order())).isEqualTo("WAITING_PAYMENT"); expect(409, action(f.order(), "entregar"));
        assertThat(order(f.order()).get("payments").size()).isEqualTo(2);
    }
    @Test void deliveryAndSagaRollbackTogetherWhenCommitFails() throws Exception {
        var f = diagnosisFixture(); complete(f); settlementMode = "PAID";
        jdbc.execute("CREATE FUNCTION phase4_fail_delivery() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status='DELIVERED' THEN RAISE EXCEPTION 'TEST_COMMIT_FAILURE'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER phase4_fail_delivery_at_commit AFTER UPDATE ON ordem_servico DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION phase4_fail_delivery()");
        try {
            assertThat(action(f.order(), "entregar").status()).isGreaterThanOrEqualTo(400);
            assertThat(order(f.order()).get("status").asString()).isEqualTo("FINALIZADA");
            assertThat(saga(f.order())).isEqualTo("WAITING_PAYMENT");
        } finally {
            jdbc.execute("DROP TRIGGER phase4_fail_delivery_at_commit ON ordem_servico"); jdbc.execute("DROP FUNCTION phase4_fail_delivery()");
        }
        expect(200, action(f.order(), "entregar")); assertThat(saga(f.order())).isEqualTo("COMPLETED");
    }
    @Test void failedIntentCommitNeverCallsInventory() throws Exception {
        var f = diagnosisFixture(); addMaterial(f.item()); approve(f.order());
        jdbc.execute("CREATE FUNCTION phase4_fail_intent() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'TEST_COMMIT_FAILURE'; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER phase4_fail_intent_at_commit AFTER INSERT ON saga_operation DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION phase4_fail_intent()");
        try {
            assertThat(start(f.item(), UUID.randomUUID()).status()).isGreaterThanOrEqualTo(400);
            assertThat(requests).isEmpty(); assertThat(jdbc.queryForObject("SELECT count(*) FROM saga_operation", Integer.class)).isZero();
            assertThat(saga(f.order())).isEqualTo("READY_FOR_EXECUTION");
            assertThat(order(f.order()).get("itensServico").get(0).get("executionState").asString()).isEqualTo("PENDING");
        } finally {
            jdbc.execute("DROP TRIGGER phase4_fail_intent_at_commit ON saga_operation"); jdbc.execute("DROP FUNCTION phase4_fail_intent()");
        }
    }
}
