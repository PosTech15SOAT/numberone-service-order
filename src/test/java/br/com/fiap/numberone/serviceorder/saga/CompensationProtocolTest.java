package br.com.fiap.numberone.serviceorder.saga;

import br.com.fiap.numberone.serviceorder.application.gateways.*;
import br.com.fiap.numberone.serviceorder.infrastructure.integration.HttpInventorySagaGateway;
import br.com.fiap.numberone.serviceorder.infrastructure.integration.HttpBillingSagaGateway;
import br.com.fiap.numberone.serviceorder.infrastructure.integration.ParticipantHttpClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Reject malformed confirmations: HTTP 200 alone never resolves an effect. */
class CompensationProtocolTest {
    private HttpServer server;
    private InventorySagaGateway inventory;
    private BillingSagaGateway billing;
    private volatile int status=200;
    private volatile String body;
    private final ObjectMapper json=new ObjectMapper();
    private final UUID operation=UUID.randomUUID(),compensation=UUID.randomUUID(),order=UUID.randomUUID(),budget=UUID.randomUUID();
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e -> { try { byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes); } finally { e.close(); } });server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        var http = new ParticipantHttpClient(json, base, "fixture");
        inventory = new HttpInventorySagaGateway(json, http);
        billing = new HttpBillingSagaGateway(json, http);
    }
    @AfterEach void stop() { server.stop(0); }
    private InventorySagaGateway.Reversal request() { return new InventorySagaGateway.Reversal(compensation,"CANCELLED_BEFORE_USE",true,new InventorySagaGateway.Evidence(UUID.randomUUID(),Instant.now(),"fixture")); }
    @Test void inventoryCannotConfirmWrongIdentityMissingInverseOrClosedWithMovement() {
        for(var result:List.of(
                new InventorySagaGateway.InventoryOperation(UUID.randomUUID(),compensation,"REVERSED",List.of(UUID.randomUUID()),null),
                new InventorySagaGateway.InventoryOperation(operation,compensation,"REVERSED",List.of(),null),
                new InventorySagaGateway.InventoryOperation(operation,compensation,"CLOSED",List.of(UUID.randomUUID()),null),
                new InventorySagaGateway.InventoryOperation(operation,UUID.randomUUID(),"CLOSED",List.of(),null),
                new InventorySagaGateway.InventoryOperation(operation,compensation,"REVERSED",List.of(new UUID(0,0)),null),
                new InventorySagaGateway.InventoryOperation(operation,null,"REVERSED",List.of(UUID.randomUUID()),null))) {
            body=json.writeValueAsString(result);assertThatThrownBy(() -> inventory.reverse(operation,request(),order)).isInstanceOf(SagaDependencyException.class);
        }
        body="{}";assertThatThrownBy(() -> inventory.inventoryOperation(operation,order)).isInstanceOf(SagaDependencyException.class);
    }
    @Test void unknownAndPendingRemainUnconfirmedAndConflictsAreBusinessFailures() {
        status=404;body=json.writeValueAsString(Map.of("operationId",operation,"result","UNKNOWN"));
        assertThat(inventory.inventoryOperation(operation,order).result()).isEqualTo("UNKNOWN");
        status=202;body=json.writeValueAsString(Map.of("operationId",operation,"result","PENDING"));
        assertThat(inventory.inventoryOperation(operation,order).result()).isEqualTo("PENDING");
        status=409;body="{\"code\":\"MATERIAL_USE_NOT_RESOLVED\"}";
        assertThatThrownBy(() -> inventory.reverse(operation,request(),order)).isInstanceOf(SagaConflictException.class).hasMessage("MATERIAL_USE_NOT_RESOLVED");
        body="{\"code\":\"UNRECOGNIZED\"}";assertThatThrownBy(() -> inventory.reverse(operation,request(),order)).isInstanceOf(SagaDependencyException.class);
    }
    @Test void billingMustMatchOperationBudgetAndRevisionAnd404DoesNotCloseContext() {
        var request=new BillingSagaGateway.Cancellation(operation,2,"SERVICE_ORDER_CANCELLED");
        for(var result:List.of(
                new BillingSagaGateway.CancellationResult(operation,null,budget,1,"CANCELLED"),
                new BillingSagaGateway.CancellationResult(operation,null,UUID.randomUUID(),2,"CANCELLED"),
                new BillingSagaGateway.CancellationResult(UUID.randomUUID(),null,budget,2,"CANCELLED"),
                new BillingSagaGateway.CancellationResult(operation,null,budget,2,"PENDING"))) {
            body=json.writeValueAsString(result);assertThatThrownBy(() -> billing.cancelBudget(budget,request,order)).isInstanceOf(SagaDependencyException.class);
        }
        status=404;body="{}";assertThatThrownBy(() -> billing.closeBudgetContext(order,new BillingSagaGateway.ContextCancellation(operation,"SERVICE_ORDER_CANCELLED"),order)).isInstanceOf(SagaDependencyException.class);
        status=409;body="{\"code\":\"BUDGET_VERSION_CONFLICT\"}";
        assertThatThrownBy(() -> billing.cancelBudget(budget,request,order)).isInstanceOf(SagaConflictException.class);
    }
    @Test void contextCancellationRequiresCompleteMatchingConfirmation() {
        var request=new BillingSagaGateway.ContextCancellation(operation,"SERVICE_ORDER_CANCELLED");
        for(var result:List.of(
                new BillingSagaGateway.CancellationResult(operation,UUID.randomUUID(),null,null,"NO_BUDGET"),
                new BillingSagaGateway.CancellationResult(operation,order,budget,1,"NO_BUDGET"),
                new BillingSagaGateway.CancellationResult(operation,order,null,null,"CANCELLED"))) {
            body=json.writeValueAsString(result);assertThatThrownBy(() -> billing.closeBudgetContext(order,request,order)).isInstanceOf(SagaDependencyException.class);
        }
        body=json.writeValueAsString(new BillingSagaGateway.CancellationResult(operation,order,budget,1,"CANCELLED"));
        assertThat(billing.closeBudgetContext(order,request,order).result()).isEqualTo("CANCELLED");
    }
}
