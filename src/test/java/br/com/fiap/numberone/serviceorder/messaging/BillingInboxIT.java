package br.com.fiap.numberone.serviceorder.messaging;

import br.com.fiap.numberone.serviceorder.application.events.BillingEvent;
import br.com.fiap.numberone.serviceorder.application.services.ProcessBillingEvent;
import br.com.fiap.numberone.support.HttpPostgresSupport;
import br.com.fiap.numberone.serviceorder.infrastructure.messaging.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** PostgreSQL real; producer fixture + SQS SDK mock, no AWS/emulator or live Billing. */
class BillingInboxIT extends HttpPostgresSupport {
    @Autowired ProcessBillingEvent handler;
    @Autowired BillingEventCodec codec;
    @Autowired PlatformTransactionManager manager;
    @Autowired br.com.fiap.numberone.serviceorder.application.services.ServiceOrderService orderService;

    private String event(UUID order) throws Exception {
        var tree = json.readTree(Files.readString(Path.of("doc/contracts/saga/v2/examples/OrcamentoEnviado.json")));
        ((tools.jackson.databind.node.ObjectNode) tree.get("payload")).put("serviceOrderId", order.toString());
        return json.writeValueAsString(tree);
    }
    private String changed(String body, String field, Object value) {
        var tree = (tools.jackson.databind.node.ObjectNode) json.readTree(body);
        var payload = (tools.jackson.databind.node.ObjectNode) tree.get("payload");
        if (value instanceof Integer n) { payload.put(field, n); } else { payload.put(field, value.toString()); }
        return json.writeValueAsString(tree);
    }
    private String state(UUID order) { return jdbc.queryForObject("SELECT state FROM service_order_saga WHERE service_order_id=?", String.class, order); }
    private int inboxCount() { return jdbc.queryForObject("SELECT count(*) FROM inbox_event", Integer.class); }
    private Message delivery(String body) { return Message.builder().body(body).receiptHandle("fixture-receipt").build(); }

    @Test void openingCreatesTechnicalSagaWithCommercialReceived() throws Exception {
        var customer = createCustomer(); var order = createOrder(customer, createVehicle(customer));
        assertThat(state(order)).isEqualTo("IN_DIAGNOSIS");
        assertThat(jdbc.queryForObject("SELECT status FROM ordem_servico WHERE id=?", String.class, order)).isEqualTo("RECEIVED");
    }
    @Test void orderAndSagaRollBackTogether() throws Exception {
        var customer = createCustomer(); var vehicle = createVehicle(customer);
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            var order = orderService.create(customer, vehicle, "Fixture", "Fixture", null,
                    java.time.LocalDateTime.of(2026, 11, 1, 10, 0));
            assertThat(state(order.getId())).isEqualTo("IN_DIAGNOSIS");
            tx.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ordem_servico", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM service_order_saga", Integer.class)).isZero();
    }
    @Test void sentBudgetCommitsInboxAndTransitionThenAcknowledges() throws Exception {
        var f = diagnosisFixture(); var body = event(f.order());
        var sqs = mock(SqsClient.class);
        when(sqs.deleteMessage(any(DeleteMessageRequest.class))).thenAnswer(invocation -> {
            assertThat(state(f.order())).isEqualTo("WAITING_APPROVAL");
            assertThat(inboxCount()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT outcome FROM inbox_event", String.class)).isEqualTo("APPLIED");
            return DeleteMessageResponse.builder().build();
        });
        new SqsBillingConsumer(sqs, "mock://billing-os", handler, codec, new SimpleMeterRegistry()).consume(delivery(body));
        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
        assertThat(jdbc.queryForObject("SELECT current_budget_id FROM ordem_servico WHERE id=?", UUID.class, f.order()))
                .isEqualTo(((BillingEvent.Budget) codec.decode(body).payload()).id());
    }
    @Test void identicalDuplicateDoesNotReapplyAndKeyOrderIsIrrelevant() throws Exception {
        var f = diagnosisFixture(); var body = event(f.order());
        assertThat(processBillingEvent(body)).isEqualTo(BillingEvent.Outcome.APPLIED);
        assertThat(processBillingEvent(" \n" + body + "\n")).isEqualTo(BillingEvent.Outcome.DUPLICATE);
        assertThat(inboxCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM service_order_saga WHERE service_order_id=?", Long.class, f.order())).isEqualTo(1);
    }
    @Test void collisionIsRejectedAndOriginalEffectRemains() throws Exception {
        var f = diagnosisFixture(); var body = event(f.order()); processBillingEvent(body);
        assertThatThrownBy(() -> processBillingEvent(changed(body, "budgetId", UUID.randomUUID())))
                .hasMessage("EVENT_ID_COLLISION");
        assertThat(inboxCount()).isEqualTo(1); assertThat(state(f.order())).isEqualTo("WAITING_APPROVAL");
        var sqs = mock(SqsClient.class);
        new SqsBillingConsumer(sqs, "mock://billing-os", handler, codec, new SimpleMeterRegistry())
                .consume(delivery(changed(body, "budgetId", UUID.randomUUID())));
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }
    @Test void commitFailureRollsBackInboxOrderAndSagaAndAllowsRedelivery() throws Exception {
        var f = diagnosisFixture(); var body = event(f.order());
        jdbc.execute("CREATE FUNCTION phase3_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'TEST_COMMIT_FAILURE'; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER phase3_fail_at_commit AFTER INSERT ON inbox_event DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION phase3_fail()");
        try {
            var sqs = mock(SqsClient.class);
            new SqsBillingConsumer(sqs, "mock://billing-os", handler, codec, new SimpleMeterRegistry()).consume(delivery(body));
            verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
            assertThat(inboxCount()).isZero(); assertThat(state(f.order())).isEqualTo("IN_DIAGNOSIS");
            assertThat(jdbc.queryForObject("SELECT current_budget_id FROM ordem_servico WHERE id=?", UUID.class, f.order())).isNull();
        } finally {
            jdbc.execute("DROP TRIGGER phase3_fail_at_commit ON inbox_event"); jdbc.execute("DROP FUNCTION phase3_fail()");
        }
        assertThat(processBillingEvent(body)).isEqualTo(BillingEvent.Outcome.APPLIED);
    }
    @Test void lostAckAfterCommitRedeliversWithoutRepeatingEffect() throws Exception {
        var f = diagnosisFixture(); var body = event(f.order()); var sqs = mock(SqsClient.class);
        when(sqs.deleteMessage(any(DeleteMessageRequest.class))).thenThrow(new RuntimeException("ACK_LOST"))
                .thenReturn(DeleteMessageResponse.builder().build());
        var consumer = new SqsBillingConsumer(sqs, "mock://billing-os", handler, codec, new SimpleMeterRegistry());
        consumer.consume(delivery(body)); consumer.consume(delivery(body));
        verify(sqs, times(2)).deleteMessage(any(DeleteMessageRequest.class));
        assertThat(inboxCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM service_order_saga WHERE service_order_id=?", Long.class, f.order())).isEqualTo(1);
    }
    @Test void futureCompositionRollsBackAndStaleRevisionIsAudited() throws Exception {
        var f = diagnosisFixture(); var body = event(f.order());
        assertThatThrownBy(() -> processBillingEvent(changed(body, "compositionVersion", 4))).isInstanceOf(RuntimeException.class);
        assertThat(inboxCount()).isZero();
        assertThat(processBillingEvent(changed(body, "compositionVersion", 2))).isEqualTo(BillingEvent.Outcome.OBSOLETE);
        assertThat(state(f.order())).isEqualTo("IN_DIAGNOSIS");
    }
    @Test void unavailableBudgetContextAndInvalidPayloadAreNotAcknowledged() throws Exception {
        var f = diagnosisFixture(); var sqs = mock(SqsClient.class);
        var consumer = new SqsBillingConsumer(sqs, "mock://billing-os", handler, codec, new SimpleMeterRegistry());
        consumer.consume(delivery("{\"eventType\":\"unknown\"}"));
        var approval = Files.readString(Path.of("doc/contracts/saga/v2/examples/OrcamentoAprovado.json"))
                .replace("00000001-0000-4000-8000-000000000001", f.order().toString());
        consumer.consume(delivery(approval));
        assertThat(inboxCount()).isZero(); verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }
    @Test void parallelDuplicateHasExactlyOneEffect() throws Exception {
        var f = diagnosisFixture(); var body = event(f.order());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> processBillingEvent(body)); var two = pool.submit(() -> processBillingEvent(body));
            assertThat(java.util.List.of(one.get(), two.get())).containsExactlyInAnyOrder(
                    BillingEvent.Outcome.APPLIED, BillingEvent.Outcome.DUPLICATE);
        }
        assertThat(inboxCount()).isEqualTo(1);
    }
    @Test void sameFactUnderNewIdIsIgnoredAndSameRevisionConflictRetries() throws Exception {
        var f = diagnosisFixture(); var body = event(f.order()); processBillingEvent(body);
        var replay = body.replace("66000000-0000-4000-8000-000000000001", UUID.randomUUID().toString());
        assertThat(processBillingEvent(replay)).isEqualTo(BillingEvent.Outcome.IGNORED);
        assertThatThrownBy(() -> processBillingEvent(changed(replay.replace(codec.decode(replay).id().toString(), UUID.randomUUID().toString()),
                "budgetId", UUID.randomUUID()))).hasMessage("BUDGET_COMPOSITION_CONFLICT");
        assertThat(inboxCount()).isEqualTo(2);
        assertThat(state(f.order())).isEqualTo("WAITING_APPROVAL");
    }
    @Test void parallelDifferentIdsForSameFactSerializeWithoutLockUpgradeDeadlock() throws Exception {
        var f = diagnosisFixture(); var body = event(f.order());
        var other = body.replace("66000000-0000-4000-8000-000000000001", UUID.randomUUID().toString());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> processBillingEvent(body)); var two = pool.submit(() -> processBillingEvent(other));
            assertThat(java.util.List.of(one.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    two.get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(
                    BillingEvent.Outcome.APPLIED, BillingEvent.Outcome.IGNORED);
        }
        assertThat(inboxCount()).isEqualTo(2);
    }
    @Test void terminalOrderDoesNotReopenAndUnreconciledSagaDoesNotAdvance() throws Exception {
        var f = diagnosisFixture(); var body = event(f.order());
        jdbc.update("UPDATE service_order_saga SET state='MANUAL_INTERVENTION' WHERE service_order_id=?", f.order());
        assertThatThrownBy(() -> processBillingEvent(body)).hasMessage("SAGA_STATE_REQUIRES_RECONCILIATION");
        assertThat(inboxCount()).isZero();
        // Seed a historical terminal order; public cancellation now uses C1-C6.
        jdbc.update("UPDATE ordem_servico SET status='CANCELLED' WHERE id=?",f.order());
        assertThat(processBillingEvent(body)).isEqualTo(BillingEvent.Outcome.IGNORED);
        assertThat(jdbc.queryForObject("SELECT status FROM ordem_servico WHERE id=?", String.class, f.order())).isEqualTo("CANCELLED");
    }

    private BillingEvent.Outcome processBillingEvent(String body) {
        return handler.process(codec.decode(body));
    }
}
