package br.com.fiap.numberone.serviceorder.messaging;

import br.com.fiap.numberone.serviceorder.application.events.BillingEvent;
import br.com.fiap.numberone.serviceorder.application.services.ProcessBillingEvent;
import br.com.fiap.numberone.serviceorder.infrastructure.messaging.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import java.nio.file.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;

class SqsBillingConsumerTest {
    @Test void pollsWithContractLeaseAndLetsSqsRedrivePoisonMessages() {
        var sqs = mock(SqsClient.class); var handler = mock(ProcessBillingEvent.class);
        var meter = new SimpleMeterRegistry();
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(call -> {
            ReceiveMessageRequest request = call.getArgument(0);
            assertThat(request.waitTimeSeconds()).isEqualTo(20);
            assertThat(request.visibilityTimeout()).isEqualTo(60);
            assertThat(request.maxNumberOfMessages()).isEqualTo(1);
            return ReceiveMessageResponse.builder().messages(Message.builder().body("{}").build()).build();
        });
        var consumer = new SqsBillingConsumer(sqs, "mock://billing-os", handler, new BillingEventCodec(), meter);
        for (int receive = 1; receive <= 5; receive++) { consumer.poll(); }
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
        verifyNoInteractions(handler);
        assertThat(meter.get("os.billing.failure").tag("stage", "delivery").counter().count()).isEqualTo(5);
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenThrow(new RuntimeException());
        consumer.poll();
        assertThat(meter.get("os.billing.failure").tag("stage", "poll").counter().count()).isEqualTo(1);
    }
    @Test void recordsIdentityDelayAndDuplicateOutcomeThenDeletes() throws Exception {
        var body = Files.readString(Path.of("doc/contracts/saga/v2/examples/OrcamentoEnviado.json"));
        var sqs = mock(SqsClient.class); var handler = mock(ProcessBillingEvent.class); var meter = new SimpleMeterRegistry();
        var codec = spy(new BillingEventCodec());
        var event = new BillingEventCodec().decode(body);
        when(handler.process(event)).thenReturn(BillingEvent.Outcome.DUPLICATE);
        var consumer = new SqsBillingConsumer(sqs, "mock://billing-os", handler, codec, meter);
        var logger = (Logger) LoggerFactory.getLogger(SqsBillingConsumer.class);
        var captured = new ListAppender<ILoggingEvent>();
        captured.start();
        logger.addAppender(captured);
        try {
            consumer.consume(Message.builder().body(body).receiptHandle("receipt").build());
            assertThat(captured.list).hasSize(1);
            assertThat(captured.list.getFirst().getKeyValuePairs()).anySatisfy(kv -> {
                assertThat(kv.key).isEqualTo("event.code");
                assertThat(kv.value).isEqualTo("OS_BILLING_EVENT_PROCESSED");
            }).anySatisfy(kv -> {
                assertThat(kv.key).isEqualTo("correlation_id");
                assertThat(kv.value).isEqualTo(event.correlationId());
            }).anySatisfy(kv -> {
                assertThat(kv.key).isEqualTo("service_order.id");
                assertThat(kv.value).isEqualTo(event.orderId());
            });
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
        var ordered = inOrder(handler, sqs); ordered.verify(handler).process(event);
        verify(codec).decode(body);
        ordered.verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
        assertThat(meter.get("os.billing.processed").tag("outcome", "DUPLICATE").counter().count()).isEqualTo(1);
    }
}
