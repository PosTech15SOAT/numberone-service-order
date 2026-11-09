package br.com.fiap.numberone.serviceorder.saga;

import br.com.fiap.numberone.support.HttpPostgresSupport;
import br.com.fiap.numberone.serviceorder.application.services.*;
import br.com.fiap.numberone.serviceorder.application.gateways.*;
import br.com.fiap.numberone.serviceorder.infrastructure.messaging.BillingInboxHandler;
import br.com.fiap.numberone.shared.application.gateways.BranchScope;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.net.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** OS and PostgreSQL are real; participant HTTP fixtures have durable, serialized ledgers. */
class SagaCompensationIT extends HttpPostgresSupport {
    private record Response(int status,String body) { }
    private static volatile java.util.function.Function<HttpExchange,Response> responder;
    private static final HttpServer SERVER=server();
    private static final List<Map<String,Object>> TRACE=new CopyOnWriteArrayList<>();
    private static final List<Map<String,Object>> EVIDENCE=new CopyOnWriteArrayList<>();
    private static HttpServer server() {
        try {
            var s=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            s.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            s.createContext("/",e -> { try {
                var r=responder.apply(e); var bytes=r.body().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                e.getResponseHeaders().set("Content-Type","application/json");
                e.sendResponseHeaders(r.status(),bytes.length); e.getResponseBody().write(bytes);
            } finally { e.close(); } }); s.start(); return s;
        } catch(Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource static void participants(DynamicPropertyRegistry p) {
        p.add("app.saga.billing-url",() -> "http://127.0.0.1:"+SERVER.getAddress().getPort());
        p.add("app.saga.inventory-url",() -> "http://127.0.0.1:"+SERVER.getAddress().getPort());
        p.add("app.saga.billing-token",() -> "fixture-billing");
        p.add("app.saga.inventory-token",() -> "fixture-inventory");
    }
    @Autowired SagaCompensation compensation;
    @Autowired SagaRecoveryWorker worker;
    @Autowired SagaWorkflow workflow;
    @Autowired BillingInboxHandler inbox;
    @Autowired SagaParticipants participants;
    @Autowired ServiceOrderGateway orders;
    @Autowired BranchScope branch;
    @Autowired PlatformTransactionManager manager;
    private volatile Throwable fixtureFailure;
    private volatile Runnable beforeDebit,afterDebit,beforeBilling;
    private volatile boolean lostConsumption,lostReversal,billingUnavailable,unknownQuery;
    private volatile String reversalConflict,billingConflict;
    private volatile boolean reversalUnavailable,pendingQuery;
    private volatile int unavailableBillingStatus;
    private final AtomicInteger consumptions=new AtomicInteger(),reversals=new AtomicInteger(),billings=new AtomicInteger(),queries=new AtomicInteger();
    private final Map<UUID,Object> requests=new ConcurrentHashMap<>();

    @BeforeEach void fixtures() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS fixture_stock_balance (id integer PRIMARY KEY,balance integer NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS fixture_stock_operation (id uuid PRIMARY KEY,request jsonb,result jsonb NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS fixture_stock_movement (id uuid PRIMARY KEY,operation_id uuid NOT NULL,quantity integer NOT NULL,original_movement uuid UNIQUE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS fixture_budget_operation (id uuid PRIMARY KEY,request jsonb,result jsonb NOT NULL)");
        jdbc.execute("TRUNCATE fixture_stock_operation,fixture_stock_balance,fixture_budget_operation,fixture_stock_movement");
        jdbc.update("INSERT INTO fixture_stock_balance VALUES(1,5)");
        fixtureFailure=null; beforeDebit=() -> {}; afterDebit=() -> {}; beforeBilling=() -> {};
        lostConsumption=false;lostReversal=false;billingUnavailable=false;unknownQuery=false;reversalConflict=null;billingConflict=null;
        reversalUnavailable=false;pendingQuery=false;unavailableBillingStatus=503;
        consumptions.set(0);reversals.set(0);billings.set(0);queries.set(0);requests.clear();
        responder=e -> { try { return respond(e); } catch(Throwable ex) { fixtureFailure=ex; return new Response(500,"{}"); } };
    }
    @AfterEach void fixtureAssertions() {
        try { assertThat(fixtureFailure).isNull(); }
        finally { jdbc.execute("DROP TABLE IF EXISTS fixture_stock_movement,fixture_stock_operation,fixture_stock_balance,fixture_budget_operation"); }
    }
    @AfterAll static void evidence() throws Exception {
        var mapper=new tools.jackson.databind.ObjectMapper();
        Files.writeString(Path.of("target/phase5-contract-trace.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(TRACE));
        Files.writeString(Path.of("target/phase5-evidence.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(EVIDENCE));
    }
    private ObjectNode fixture(String name) throws Exception { return (ObjectNode)json.readTree(Files.readString(Path.of("doc/contracts/saga/v2/http-examples",name))); }
    private void trace(String file,String schema,JsonNode body) { TRACE.add(Map.of("file",file,"schema",schema,"body",body)); }
    private JsonNode stored(UUID id) {
        var rows=jdbc.queryForList("SELECT result::text FROM fixture_stock_operation WHERE id=?",String.class,id);
        return rows.isEmpty() ? null : json.readTree(rows.getFirst());
    }
    private Response respond(HttpExchange e) throws Exception {
        String path=e.getRequestURI().getPath(); UUID.fromString(e.getRequestHeaders().getFirst("X-Correlation-Id"));
        boolean inventory=path.startsWith("/internal/inventory/");
        assertThat(e.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer fixture-"+(inventory ? "inventory" : "billing"));
        if (path.contains("/operations/")) {
            queries.incrementAndGet(); UUID id=UUID.fromString(path.substring(path.lastIndexOf('/')+1)); var result=stored(id);
            if(pendingQuery) { return new Response(202,json.writeValueAsString(Map.of("operationId",id,"result","PENDING"))); }
            if (result==null || unknownQuery && queries.get()==1) {
                var unknown=fixture("unknown.json");unknown.put("operationId",id.toString());trace("inventory.openapi.yaml","Unknown",unknown);return new Response(404,unknown.toString());
            }
            trace("inventory.openapi.yaml","InventoryOperation",result);return new Response(200,result.toString());
        }
        var request=json.readTree(new String(e.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        UUID key=UUID.fromString(e.getRequestHeaders().getFirst("Idempotency-Key"));
        if (inventory && path.endsWith("/consumptions")) {
            consumptions.incrementAndGet();assertThat(request.get("operationId").asString()).isEqualTo(key.toString());
            trace("inventory.openapi.yaml","ConsumptionRequest",request);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM saga_operation WHERE operation_id=?",Integer.class,key)).isEqualTo(1);
            beforeDebit.run();
            var result=new TransactionTemplate(manager).execute(t -> {
                jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,71))",key.toString());
                var existing=stored(key); if(existing!=null) { return existing; }
                int remaining=jdbc.queryForObject("SELECT balance FROM fixture_stock_balance WHERE id=1 FOR UPDATE",Integer.class);
                ObjectNode reply;
                try { reply=fixture(remaining>0 ? "consumed-direct.json" : "insufficient-stock-direct.json"); }
                catch(Exception ex) { throw new RuntimeException(ex); }
                reply.put("operationId",key.toString());
                if(remaining>0) {
                    UUID movement=UUID.randomUUID();reply.putArray("movements").add(movement.toString());
                    jdbc.update("INSERT INTO fixture_stock_movement VALUES(?,?,-1,NULL)",movement,key);
                    jdbc.update("UPDATE fixture_stock_balance SET balance=balance-1 WHERE id=1");
                }
                jdbc.update("INSERT INTO fixture_stock_operation VALUES(?,CAST(? AS jsonb),CAST(? AS jsonb))",key,request.toString(),reply.toString());return reply;
            });
            afterDebit.run();
            if(lostConsumption) { Thread.sleep(5300); }
            if(Set.of("CLOSED","REVERSED").contains(result.get("result").asString())) {
                return new Response(409,json.writeValueAsString(Map.of("code",result.get("result").asString().equals("CLOSED") ? "ATTEMPT_CLOSED" : "ATTEMPT_REVERSED","operationId",key)));
            }
            trace("inventory.openapi.yaml",result.get("result").asString().equals("CONSUMED") ? "Consumed" : "Rejected",result);
            return new Response(result.get("result").asString().equals("REJECTED") ? 409 : 200,result.toString());
        }
        if(inventory && path.endsWith("/reversal")) {
            reversals.incrementAndGet(); UUID original=UUID.fromString(path.split("/")[4]);
            assertThat(request.get("compensationId").asString()).isEqualTo(key.toString());
            assertThat(jdbc.queryForObject("SELECT request::text FROM saga_operation WHERE operation_id=?",String.class,key)).satisfies(value -> assertThat(json.readTree(value)).isEqualTo(request));
            trace("inventory.openapi.yaml","ReversalRequest",request);
            if(reversalConflict!=null) { return conflict(reversalConflict); }
            if(reversalUnavailable) { return new Response(503,"{}"); }
            var result=new TransactionTemplate(manager).execute(t -> {
                jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,71))",original.toString());
                var old=stored(original); if(old!=null && Set.of("CLOSED","REVERSED").contains(old.get("result").asString())) { return old; }
                boolean consumed=old!=null && old.get("result").asString().equals("CONSUMED");
                ObjectNode reply;
                try { reply=fixture(consumed ? "reversed-direct.json" : "closed.json"); } catch(Exception ex) { throw new RuntimeException(ex); }
                reply.put("operationId",original.toString());reply.put("compensationId",key.toString());
                if(consumed) {
                    UUID inverse=UUID.randomUUID();reply.putArray("movements").add(inverse.toString());
                    jdbc.update("INSERT INTO fixture_stock_movement VALUES(?,?,1,?)",inverse,original,UUID.fromString(old.get("movements").get(0).asString()));
                    jdbc.update("UPDATE fixture_stock_balance SET balance=balance+1 WHERE id=1");
                }
                jdbc.update("INSERT INTO fixture_stock_operation(id,result) VALUES(?,CAST(? AS jsonb)) ON CONFLICT(id) DO UPDATE SET result=excluded.result",original,reply.toString());return reply;
            });
            if(lostReversal) { return new Response(503,"{}"); }
            trace("inventory.openapi.yaml",result.get("result").asString().equals("CLOSED") ? "Closed" : "Reversed",result);return new Response(200,result.toString());
        }
        billings.incrementAndGet();beforeBilling.run();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM saga_operation WHERE intent='RESOLVE_STOCK' AND state<>'SUCCEEDED'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT state FROM saga_operation WHERE operation_id=?",String.class,key)).isIn("PENDING","AWAITING_RESULT","SUCCEEDED");
        if(billingConflict!=null) { return conflict(billingConflict); }
        boolean context=path.contains("/service-orders/");
        trace("billing.openapi.yaml",context ? "ContextCancellationRequest" : "CancellationRequest",request);
        var result=new TransactionTemplate(manager).execute(t -> {
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,72))",key.toString());
            var rows=jdbc.queryForList("SELECT result::text FROM fixture_budget_operation WHERE id=?",String.class,key);
            if(!rows.isEmpty()) { return json.readTree(rows.getFirst()); }
            var reply=json.createObjectNode().put("operationId",key.toString());
            if(context) { reply.put("serviceOrderId",path.split("/")[4]);reply.put("result","NO_BUDGET"); }
            else { reply.put("budgetId",path.split("/")[3]);reply.put("budgetVersion",request.get("budgetVersion").asInt());reply.put("result","CANCELLED"); }
            jdbc.update("INSERT INTO fixture_budget_operation VALUES(?,CAST(? AS jsonb),CAST(? AS jsonb))",key,request.toString(),reply.toString());return reply;
        });
        if(billingUnavailable) { return new Response(unavailableBillingStatus,"{}"); }
        trace("billing.openapi.yaml",context ? "ClosedBudgetContext" : "Cancelled",result);return new Response(200,result.toString());
    }
    private Response conflict(String code) { return new Response(409,json.writeValueAsString(Map.of("code",code,"message","fixture conflict","correlationId",UUID.randomUUID()))); }
    private String saga(UUID order) { return jdbc.queryForObject("SELECT state FROM service_order_saga WHERE service_order_id=?",String.class,order); }
    private int balance() { return jdbc.queryForObject("SELECT balance FROM fixture_stock_balance WHERE id=1",Integer.class); }
    private String op(UUID id) { return jdbc.queryForObject("SELECT state FROM saga_operation WHERE operation_id=?",String.class,id); }
    private Fixture approved() throws Exception {
        var f=diagnosisFixture();addMaterial(f.item()); UUID budget=UUID.randomUUID();
        for(String type:List.of("OrcamentoEnviado","OrcamentoAprovado")) {
            var event=(ObjectNode)json.readTree(Files.readString(Path.of("doc/contracts/saga/v2/examples",type+".json")));
            event.put("eventId",UUID.randomUUID().toString());var p=(ObjectNode)event.get("payload");p.put("serviceOrderId",f.order().toString());
            p.put("budgetId",budget.toString());p.put("budgetVersion",1);p.put("compositionVersion",jdbc.queryForObject("SELECT composition_version FROM ordem_servico WHERE id=?",Integer.class,f.order()));inbox.handle(event.toString());
        }
        return f;
    }
    private Reply start(Fixture f,UUID key) throws Exception { var h=new HashMap<>(admin());h.put("Idempotency-Key",key.toString());return call("PATCH","/api/admin/itens-ordem-servico/"+f.item()+"/iniciar",null,h); }
    private Map<String,Object> cancellation() { return Map.of("scope","FULL","reason","Conferencia de cancelamento","unusedConfirmed",true,"physicallyRemoved",false,"returnConfirmed",false,"verifiedAt",Instant.now().toString(),"evidenceReference","inspection-fixture"); }
    private Reply cancel(Fixture f,Object body) throws Exception { return call("PATCH","/api/admin/ordens-servico/"+f.order()+"/cancelar",body); }
    private void ready(UUID id) { jdbc.update("UPDATE service_order_saga SET next_attempt_at=CURRENT_TIMESTAMP WHERE service_order_id=?",id);jdbc.update("UPDATE saga_operation SET next_attempt_at=CURRENT_TIMESTAMP WHERE service_order_id=?",id); }
    private void pending(Fixture f,UUID id) { pending(f.order(),f.item(),id); }
    private void pending(UUID order,UUID item,UUID id) {
        var request=new SagaParticipants.Consumption(id,order,item,UUID.fromString(admin().get("X-Authenticated-Subject")),List.of(new br.com.fiap.numberone.serviceorder.domain.entities.CompositionSnapshot.Material(UUID.fromString(MATERIAL),1)));
        // Crash immediately after durable intent, before sending CT-05.
        jdbc.update("INSERT INTO saga_operation(operation_id,service_order_id,item_id,composition_version,budget_revision,attempt,state,request) SELECT ?,id,?,composition_version,budget_revision,1,'START_PENDING',CAST(? AS jsonb) FROM ordem_servico WHERE id=?",id,item,json.writeValueAsString(request),order);
        jdbc.update("UPDATE item_ordem_servico SET start_pending=true,start_operation_id=? WHERE id=?",id,item);
        jdbc.update("UPDATE service_order_saga SET state='START_PENDING' WHERE service_order_id=?",order);requests.put(id,request);
    }
    private void evidence(String scenario,Fixture f,List<Integer> balances) {
        EVIDENCE.add(Map.of("scenario",scenario,"balances",balances,"saga",saga(f.order()),
                "operations",jdbc.queryForList("SELECT operation_id,original_operation_id,intent,state,request::text,result::text,late_result::text,attempts FROM saga_operation WHERE service_order_id=? ORDER BY created_at",f.order()),
                "inventory",jdbc.queryForList("SELECT id,result::text FROM fixture_stock_operation"),
                "movements",jdbc.queryForList("SELECT * FROM fixture_stock_movement ORDER BY quantity")));
    }

    @Test void closeWinsRaceAndDelayedConsumptionCannotDebit() throws Exception {
        var f=approved();var id=UUID.randomUUID();pending(f,id);
        expect(202,cancel(f,cancellation()));compensation.recover(f.order());
        assertThat(balance()).isEqualTo(5);assertThat(stored(id).get("result").asString()).isEqualTo("CLOSED");
        assertThatThrownBy(() -> participants.consume((SagaParticipants.Consumption)requests.get(id),UUID.randomUUID())).isInstanceOf(SagaDependencyException.class);
        assertThat(balance()).isEqualTo(5);expect(409,start(f,id));
        compensation.recover(f.order());assertThat(saga(f.order())).isEqualTo("COMPENSATED");assertThat(op(id)).isEqualTo("SKIPPED");
        evidence("CLOSED antes de CT-05",f,List.of(5,5,5));
    }
    @Test void consumptionWinsAndLateResponseNeverStartsCancelledItem() throws Exception {
        var f=approved();var id=UUID.randomUUID();var debited=new CountDownLatch(1);var reply=new CountDownLatch(1);
        afterDebit=() -> { debited.countDown();await(reply); };
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var start=pool.submit(() -> start(f,id));assertThat(debited.await(5,TimeUnit.SECONDS)).isTrue();assertThat(balance()).isEqualTo(4);
            expect(202,cancel(f,cancellation()));compensation.recover(f.order());assertThat(balance()).isEqualTo(5);
            compensation.recover(f.order());assertThat(saga(f.order())).isEqualTo("COMPENSATED");reply.countDown();expect(202,start.get(10,TimeUnit.SECONDS));
        } finally { reply.countDown(); }
        assertThat(jdbc.queryForObject("SELECT start_date_time FROM item_ordem_servico WHERE id=?",java.sql.Timestamp.class,f.item())).isNull();
        assertThat(op(id)).isEqualTo("SKIPPED");assertThat(jdbc.queryForObject("SELECT late_result->>'result' FROM saga_operation WHERE operation_id=?",String.class,id)).isEqualTo("CONSUMED");
        evidence("CT-05 antes de CT-08B e resposta tardia",f,List.of(5,4,5));
    }
    @Test void realTimeoutAfterDebitReconcilesAndRepeatedReversalRestoresOnce() throws Exception {
        var f=approved();var id=UUID.randomUUID();lostConsumption=true;expect(202,start(f,id));lostConsumption=false;
        assertThat(balance()).isEqualTo(4);assertThat(op(id)).isEqualTo("AWAITING_RESULT");
        var body=new HashMap<>(cancellation());body.put("physicallyRemoved",true);body.put("returnConfirmed",true);
        expect(202,cancel(f,body));lostReversal=true;compensation.recover(f.order());assertThat(balance()).isEqualTo(5);assertThat(saga(f.order())).isEqualTo("COMPENSATING");
        UUID reversal=jdbc.queryForObject("SELECT operation_id FROM saga_operation WHERE original_operation_id=?",UUID.class,id);
        var request=json.readValue(jdbc.queryForObject("SELECT request::text FROM saga_operation WHERE operation_id=?",String.class,reversal),SagaParticipants.Reversal.class);
        assertThat(request.reason()).isEqualTo("VERIFIED_RETURN");lostReversal=false;
        participants.reverse(id,request,UUID.randomUUID());participants.reverse(id,request,UUID.randomUUID());assertThat(balance()).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fixture_stock_movement WHERE quantity=1",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sum(quantity) FROM fixture_stock_movement",Integer.class)).isZero();
        ready(f.order());compensation.recover(f.order());compensation.recover(f.order());
        assertThat(saga(f.order())).isEqualTo("COMPENSATED");assertThat(consumptions.get()).isEqualTo(1);
        evidence("Timeout apos baixa, resposta perdida da reversao e retry",f,List.of(5,4,5,5));
    }
    @Test void freshWorkerResumesAfterStockAndBillingTimeoutWithSameOperation() throws Exception {
        var f=approved();var id=UUID.randomUUID();pending(f,id);expect(202,cancel(f,cancellation()));compensation.recover(f.order());
        assertThat(billings.get()).isZero();assertThat(saga(f.order())).isEqualTo("COMPENSATING");
        billingUnavailable=true;restartProcess();assertThat(saga(f.order())).isEqualTo("COMPENSATING");
        UUID billing=jdbc.queryForObject("SELECT operation_id FROM saga_operation WHERE intent='CANCEL_BUDGET'",UUID.class);
        assertThat(op(billing)).isEqualTo("AWAITING_RESULT");assertThat(jdbc.queryForObject("SELECT status FROM ordem_servico WHERE id=?",String.class,f.order())).isEqualTo("APPROVED");
        billingUnavailable=false;ready(f.order());restartProcess();
        assertThat(op(billing)).isEqualTo("SUCCEEDED");assertThat(jdbc.queryForObject("SELECT count(*) FROM fixture_budget_operation",Integer.class)).isEqualTo(1);
        assertThat(saga(f.order())).isEqualTo("COMPENSATED");evidence("Retomada entre C3/CT-09 e resposta Billing perdida",f,List.of(5,5));
    }
    @Test void unknownStockCannotFinishUntilCt08bAndNoBudgetNeedsCt11() throws Exception {
        var f=diagnosisFixture();var request=cancellation();expect(202,cancel(f,request));billingUnavailable=true;
        compensation.recover(f.order());assertThat(saga(f.order())).isEqualTo("COMPENSATING");assertThat(billings.get()).isEqualTo(1);
        billingUnavailable=false;ready(f.order());compensation.recover(f.order());assertThat(saga(f.order())).isEqualTo("COMPENSATED");
        assertThat(jdbc.queryForObject("SELECT result->>'result' FROM saga_operation WHERE intent='CLOSE_BUDGET_CONTEXT'",String.class)).isEqualTo("NO_BUDGET");
        expect(200,cancel(f,request));evidence("CT-11 fecha contexto sem orcamento",f,List.of(5,5));
    }
    @Test void terminalRefusalSkipsReversalAndUnknownQueryNeverMeansResolved() throws Exception {
        var f=approved();jdbc.update("UPDATE fixture_stock_balance SET balance=0");var id=UUID.randomUUID();expect(409,start(f,id));
        expect(202,cancel(f,cancellation()));compensation.recover(f.order());assertThat(reversals.get()).isZero();assertThat(op(id)).isEqualTo("REJECTED");
        compensation.recover(f.order());assertThat(saga(f.order())).isEqualTo("COMPENSATED");
    }
    @Test void partialUsedUnreturnedOrStartedOrdersRequireOperationalReview() throws Exception {
        var f=approved();var body=new HashMap<>(cancellation());body.put("physicallyRemoved",true);
        expect(409,cancel(f,body));assertThat(saga(f.order())).isEqualTo("MANUAL_INTERVENTION");worker.tick();assertThat(reversals.get()).isZero();assertThat(billings.get()).isZero();
        expect(409,start(f,UUID.randomUUID()));
        expect(409,call("POST","/api/admin/ordens-servico/"+f.order()+"/compensacao/retomar",Map.of("reason","Nao autoriza uso fisico")));
        evidence("Devolucao fisica nao conferida",f,List.of(5,5));
    }
    @Test void firstConfirmedStartWinsCancellationRaceAndPreservesConsumedBalance() throws Exception {
        var f=approved();expect(200,start(f,UUID.randomUUID()));expect(409,cancel(f,cancellation()));worker.tick();
        assertThat(balance()).isEqualTo(4);assertThat(saga(f.order())).isEqualTo("MANUAL_INTERVENTION");assertThat(billings.get()).isZero();
        evidence("Inicio confirmado fora da janela",f,List.of(5,4,4));
    }
    @Test void businessConflictIsAuditedAndExplicitResumeReusesRequest() throws Exception {
        var f=approved();var id=UUID.randomUUID();pending(f,id);expect(202,cancel(f,cancellation()));
        reversalConflict="MATERIAL_USE_NOT_RESOLVED";compensation.recover(f.order());assertThat(saga(f.order())).isEqualTo("MANUAL_INTERVENTION");
        assertThat(billings.get()).isZero();UUID reversal=jdbc.queryForObject("SELECT operation_id FROM saga_operation WHERE original_operation_id=?",UUID.class,id);
        assertThat(op(reversal)).isEqualTo("NEEDS_REVIEW");reversalConflict=null;
        expect(202,call("POST","/api/admin/ordens-servico/"+f.order()+"/compensacao/retomar",Map.of("reason","Inventory confirmou evidencia original valida")));
        compensation.recover(f.order());billingConflict="FINANCIAL_EFFECT_PENDING";compensation.recover(f.order());assertThat(saga(f.order())).isEqualTo("MANUAL_INTERVENTION");
        assertThat(jdbc.queryForObject("SELECT jsonb_array_length(review_decisions) FROM service_order_saga WHERE service_order_id=?",Integer.class,f.order())).isEqualTo(1);
        evidence("Conflito financeiro exige revisao",f,List.of(5,5));
    }
    @Test void leaseExcludesConcurrentWorkersAndExpiredOwnerCannotOverwriteNewResult() throws Exception {
        var f=diagnosisFixture();expect(202,cancel(f,cancellation()));var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        beforeBilling=() -> { entered.countDown();await(release); };
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=pool.submit(() -> compensation.recover(f.order()));assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            compensation.recover(f.order());assertThat(billings.get()).isEqualTo(1);
            jdbc.update("UPDATE service_order_saga SET recovery_until=CURRENT_TIMESTAMP-interval '1 second' WHERE service_order_id=?",f.order());
            beforeBilling=() -> {};compensation.recover(f.order());assertThat(saga(f.order())).isEqualTo("COMPENSATED");release.countDown();first.get(10,TimeUnit.SECONDS);
            assertThat(saga(f.order())).isEqualTo("COMPENSATED");assertThat(jdbc.queryForObject("SELECT count(*) FROM fixture_budget_operation",Integer.class)).isEqualTo(1);
        } finally { release.countDown(); }
    }
    @Test void activeRecoveryQueriesOriginalIdAndRetriesUnknownWithSameId() throws Exception {
        var f=approved();var id=UUID.randomUUID();pending(f,id);
        jdbc.update("UPDATE saga_operation SET attempts=1,state='AWAITING_RESULT' WHERE operation_id=?",id);
        worker.tick();assertThat(queries.get()).isEqualTo(1);assertThat(consumptions.get()).isEqualTo(1);
        assertThat(op(id)).isEqualTo("CONFIRMED");assertThat(balance()).isEqualTo(4);worker.tick();assertThat(consumptions.get()).isEqualTo(1);
    }
    @Test void activeRecoveryFindsDebitWithoutResendingAndPendingNeverConfirms() throws Exception {
        var f=approved();var id=UUID.randomUUID();pending(f,id);
        participants.consume((SagaParticipants.Consumption)requests.get(id),UUID.randomUUID());
        pendingQuery=true;worker.tick();assertThat(op(id)).isEqualTo("AWAITING_RESULT");assertThat(consumptions.get()).isEqualTo(1);
        pendingQuery=false;ready(f.order());worker.tick();assertThat(op(id)).isEqualTo("CONFIRMED");assertThat(consumptions.get()).isEqualTo(1);assertThat(balance()).isEqualTo(4);
    }
    @Test void unknownPlusUnavailableCloseAndBilling404KeepEffectsPending() throws Exception {
        var f=approved();var id=UUID.randomUUID();pending(f,id);expect(202,cancel(f,cancellation()));
        reversalUnavailable=true;compensation.recover(f.order());assertThat(saga(f.order())).isEqualTo("COMPENSATING");assertThat(billings.get()).isZero();
        assertThat(op(id)).isEqualTo("START_PENDING");assertThat(balance()).isEqualTo(5);worker.tick();assertThat(consumptions.get()).isZero();
        reversalUnavailable=false;ready(f.order());compensation.recover(f.order());billingUnavailable=true;unavailableBillingStatus=404;
        compensation.recover(f.order());assertThat(saga(f.order())).isEqualTo("COMPENSATING");
        billingUnavailable=false;ready(f.order());compensation.recover(f.order());assertThat(saga(f.order())).isEqualTo("COMPENSATED");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"PARTIAL","USED"})
    void partialAndPhysicalUseAreNeverAutomatic(String mode) throws Exception {
        var f=approved();var body=new HashMap<>(cancellation());
        if(mode.equals("PARTIAL")) { body.put("scope","PARTIAL"); } else { body.put("unusedConfirmed",false); }
        expect(409,cancel(f,body));assertThat(saga(f.order())).isEqualTo("MANUAL_INTERVENTION");worker.tick();
        assertThat(balance()).isEqualTo(5);assertThat(billings.get()).isZero();assertThat(reversals.get()).isZero();
    }
    @Test void everyAffectedAttemptMustResolveBeforeBilling() throws Exception {
        var f=diagnosisFixture();addMaterial(f.item());var second=addItem(f.order(),f.service());addMaterial(second);
        // Pending intents are enough to require closure even when no response was ever observed.
        pending(f,UUID.randomUUID());pending(f.order(),second,UUID.randomUUID());
        expect(202,cancel(f,cancellation()));compensation.recover(f.order());assertThat(billings.get()).isZero();
        compensation.recover(f.order());assertThat(billings.get()).isZero();compensation.recover(f.order());
        assertThat(saga(f.order())).isEqualTo("COMPENSATED");assertThat(reversals.get()).isEqualTo(2);assertThat(balance()).isEqualTo(5);
    }
    @Test void c1FailureRollsBackBlockAndIntentsTogether() throws Exception {
        var f=approved();pending(f,UUID.randomUUID());
        jdbc.execute("CREATE FUNCTION phase5_fail_intent() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.intent='RESOLVE_STOCK' THEN RAISE EXCEPTION 'TEST_COMMIT_FAILURE'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER phase5_fail_intent AFTER INSERT ON saga_operation DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION phase5_fail_intent()");
        try {
            assertThat(cancel(f,cancellation()).status()).isGreaterThanOrEqualTo(400);assertThat(saga(f.order())).isEqualTo("START_PENDING");
            assertThat(jdbc.queryForObject("SELECT cancellation_in_progress FROM service_order_saga WHERE service_order_id=?",Boolean.class,f.order())).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM saga_operation WHERE intent='RESOLVE_STOCK'",Integer.class)).isZero();
        } finally { jdbc.execute("DROP TRIGGER phase5_fail_intent ON saga_operation");jdbc.execute("DROP FUNCTION phase5_fail_intent()"); }
    }
    @Test void refusalWinningBetweenQueryAndReversalIsReconciledWithoutReturningStock() throws Exception {
        var f=approved();jdbc.update("UPDATE fixture_stock_balance SET balance=0");var id=UUID.randomUUID();expect(409,start(f,id));
        expect(202,cancel(f,cancellation()));unknownQuery=true;reversalConflict="OPERATION_ALREADY_REJECTED";compensation.recover(f.order());
        assertThat(queries.get()).isEqualTo(2);assertThat(saga(f.order())).isEqualTo("COMPENSATING");assertThat(balance()).isZero();
        compensation.recover(f.order());assertThat(saga(f.order())).isEqualTo("COMPENSATED");
    }
    @Test void cancellationAuditAndImmutableRequestsAreVisibleAndCannotBeReplaced() throws Exception {
        var f=approved();pending(f,UUID.randomUUID());var request=cancellation();expect(202,cancel(f,request));expect(202,cancel(f,request));
        trace("os-phase5.openapi.yaml","Verification",json.valueToTree(request));
        var changed=new HashMap<>(request);changed.put("reason","Outro motivo");expect(409,cancel(f,changed));
        assertThatThrownBy(() -> jdbc.update("UPDATE saga_operation SET request='{}'::jsonb WHERE intent='RESOLVE_STOCK'"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        compensation.recover(f.order());compensation.recover(f.order());
        var status=expect(200,call("GET","/api/admin/ordens-servico/"+f.order()+"/compensacao",null));
        trace("os-phase5.openapi.yaml","Status",status);
        assertThat(status.get("cancellation").get("author").asString()).isEqualTo(admin().get("X-Authenticated-Subject"));
        assertThat(status.get("operations").findValues("result")).isNotEmpty();
        expect(403,call("GET","/api/admin/ordens-servico/"+f.order()+"/compensacao",null,customer(f.customer())));
        UUID billing=jdbc.queryForObject("SELECT operation_id FROM saga_operation WHERE intent='CANCEL_BUDGET'",UUID.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE saga_operation SET budget_id=? WHERE operation_id=?",UUID.randomUUID(),billing))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        UUID other=createOrder(f.customer(),f.vehicle());diagnose(other);UUID otherItem=addItem(other,f.service());
        var headers=new HashMap<>(admin());headers.put("Idempotency-Key",billing.toString());
        var conflict=expect(409,call("PATCH","/api/admin/itens-ordem-servico/"+otherItem+"/iniciar",null,headers));
        assertThat(conflict.get("message").asString()).isEqualTo("IDEMPOTENCY_CONFLICT");
    }
    @Test void c6RollbackDoesNotLeaveCompensatedSagaOrLoseBillingIdentity() throws Exception {
        var f=diagnosisFixture();expect(202,cancel(f,cancellation()));
        jdbc.execute("CREATE FUNCTION phase5_fail_finish() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status='CANCELLED' THEN RAISE EXCEPTION 'TEST_COMMIT_FAILURE'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER phase5_fail_finish AFTER UPDATE ON ordem_servico DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION phase5_fail_finish()");
        try { assertThatThrownBy(() -> compensation.recover(f.order())).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("DROP TRIGGER phase5_fail_finish ON ordem_servico");jdbc.execute("DROP FUNCTION phase5_fail_finish()"); }
        assertThat(saga(f.order())).isEqualTo("COMPENSATING");assertThat(jdbc.queryForObject("SELECT status FROM ordem_servico WHERE id=?",String.class,f.order())).isEqualTo("IN_DIAGNOSIS");
        jdbc.update("UPDATE service_order_saga SET recovery_until=CURRENT_TIMESTAMP-interval '1 second' WHERE service_order_id=?",f.order());compensation.recover(f.order());
        assertThat(saga(f.order())).isEqualTo("COMPENSATED");assertThat(jdbc.queryForObject("SELECT count(*) FROM fixture_budget_operation",Integer.class)).isEqualTo(1);
    }
    private static void await(CountDownLatch latch) { try { if(!latch.await(10,TimeUnit.SECONDS)) { throw new AssertionError("latch timeout"); } } catch(InterruptedException ex) { throw new RuntimeException(ex); } }
    private void restartProcess() throws Exception {
        var ds=jdbc.getDataSource().unwrap(com.zaxxer.hikari.HikariDataSource.class);
        var log=Path.of("target/recovery-process-"+UUID.randomUUID()+".log");
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),RecoveryProcess.class.getName(),
                "--server.port=0","--spring.datasource.url="+ds.getJdbcUrl(),"--spring.datasource.username="+ds.getUsername(),
                "--spring.datasource.password="+ds.getPassword(),"--app.branch.id="+BRANCH,
                "--app.security.identity.gateway-trusted=true","--app.material-reference.provider=fixture",
                "--app.saga.recovery-enabled=false","--app.billing.sqs.enabled=false","--management.statsd.metrics.export.enabled=false",
                "--app.saga.billing-url=http://127.0.0.1:"+SERVER.getAddress().getPort(),"--app.saga.billing-token=fixture-billing",
                "--app.saga.inventory-url=http://127.0.0.1:"+SERVER.getAddress().getPort(),"--app.saga.inventory-token=fixture-inventory")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(45,TimeUnit.SECONDS)).as("fresh JVM recovery completed: %s",log).isTrue();
            assertThat(process.exitValue()).as("fresh JVM recovery exit: %s",log).isZero();
        } finally { if(process.isAlive()) { process.destroyForcibly(); } }
    }
}
