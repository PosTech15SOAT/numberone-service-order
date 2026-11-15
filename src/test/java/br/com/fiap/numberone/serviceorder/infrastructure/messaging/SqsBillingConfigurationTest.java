package br.com.fiap.numberone.serviceorder.infrastructure.messaging;

import br.com.fiap.numberone.serviceorder.application.services.ProcessBillingEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import java.util.Map;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class SqsBillingConfigurationTest {
    @Test void requiresExplicitModeAndValidatesPlatformRedriveConfiguration() {
        var config = new SqsBillingConfiguration();
        assertThatThrownBy(() -> config.billingQueue("us-east-1", "", "disabled", "", "", "unused"))
                .hasMessage("BILLING_QUEUE_MODE_REQUIRED");
        assertThatThrownBy(() -> config.billingQueue("us-east-1", "", "dedicated", "", "", "unused"))
                .hasMessage("BILLING_QUEUE_URL_REQUIRED");
        try (var queue = config.billingQueue("us-east-1", "", "shared", "mock://dedicated", "", "unused")) {
            assertThat(queue).isInstanceOf(SqsBillingQueue.class);
        }
        try (var queue = config.billingQueue("us-east-1", "http://localhost:4566", "dedicated",
                "mock://dedicated", "", "unused")) {
            assertThat(queue).isInstanceOf(SqsBillingQueue.class);
        }
        try (var queue = config.billingQueue("us-east-1", "", "dedicated", "", "https://bridge.test", "unused")) {
            assertThat(queue).isInstanceOf(HttpBillingQueue.class);
        }
        var sqs = mock(SqsClient.class); var handler = mock(ProcessBillingEvent.class); var codec = new BillingEventCodec();
        var queue = new SqsBillingQueue(sqs, "mock://dedicated");
        var metrics = new SimpleMeterRegistry();
        when(sqs.getQueueAttributes(any(Consumer.class))).thenReturn(GetQueueAttributesResponse.builder().attributes(Map.of(
                QueueAttributeName.VISIBILITY_TIMEOUT, "60", QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, "20",
                QueueAttributeName.MESSAGE_RETENTION_PERIOD, "345600", QueueAttributeName.REDRIVE_POLICY,
                "{\"maxReceiveCount\":5,\"deadLetterTargetArn\":\"arn:aws:sqs:us-east-1:000000000000:dedicated-dlq\"}")).build());
        assertThat(config.billingConsumer(queue, handler, codec, metrics)).isNotNull();
        when(sqs.getQueueAttributes(any(Consumer.class))).thenReturn(GetQueueAttributesResponse.builder().build());
        assertThatThrownBy(() -> config.billingConsumer(queue, handler, codec, metrics))
                .hasMessage("BILLING_QUEUE_CONTRACT_MISMATCH");
        when(sqs.getQueueAttributes(any(Consumer.class))).thenReturn(GetQueueAttributesResponse.builder()
                .attributes(Map.of(QueueAttributeName.REDRIVE_POLICY, "{")).build());
        assertThatThrownBy(() -> config.billingConsumer(queue, handler, codec, metrics))
                .hasMessage("INVALID_REDRIVE_POLICY");
    }
}
