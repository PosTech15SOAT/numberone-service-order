package br.com.fiap.numberone.serviceorder.messaging;

import br.com.fiap.numberone.serviceorder.infrastructure.messaging.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import java.nio.file.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;

class SqsBillingConsumerTest {
    @Test void pollsWithContractLeaseAndLetsSqsRedrivePoisonMessages() {
        var sqs = mock(SqsClient.class); var handler = mock(BillingInboxHandler.class);
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
        var sqs = mock(SqsClient.class); var handler = mock(BillingInboxHandler.class); var meter = new SimpleMeterRegistry();
        when(handler.handle(body)).thenReturn(BillingInboxHandler.Outcome.DUPLICATE);
        var consumer = new SqsBillingConsumer(sqs, "mock://billing-os", handler, new BillingEventCodec(), meter);
        consumer.consume(Message.builder().body(body).receiptHandle("receipt").build());
        var ordered = inOrder(handler, sqs); ordered.verify(handler).handle(body);
        ordered.verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
        assertThat(meter.get("os.billing.processed").tag("outcome", "DUPLICATE").counter().count()).isEqualTo(1);
    }
}
