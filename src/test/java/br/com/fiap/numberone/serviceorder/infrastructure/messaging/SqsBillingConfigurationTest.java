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
        assertThatThrownBy(() -> config.billingSqs("us-east-1", "", "disabled"))
                .hasMessage("BILLING_QUEUE_MODE_REQUIRED");
        try (var client = config.billingSqs("us-east-1", "", "shared")) { assertThat(client).isNotNull(); }
        try (var client = config.billingSqs("us-east-1", "http://localhost:4566", "dedicated")) {
            assertThat(client).isNotNull();
        }
        var sqs = mock(SqsClient.class); var handler = mock(ProcessBillingEvent.class); var codec = new BillingEventCodec();
        var metrics = new SimpleMeterRegistry();
        assertThatThrownBy(() -> config.billingConsumer(sqs, handler, codec, metrics, "")).hasMessage("BILLING_QUEUE_URL_REQUIRED");
        when(sqs.getQueueAttributes(any(Consumer.class))).thenReturn(GetQueueAttributesResponse.builder().attributes(Map.of(
                QueueAttributeName.VISIBILITY_TIMEOUT, "60", QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, "20",
                QueueAttributeName.MESSAGE_RETENTION_PERIOD, "345600", QueueAttributeName.REDRIVE_POLICY,
                "{\"maxReceiveCount\":5,\"deadLetterTargetArn\":\"arn:aws:sqs:us-east-1:000000000000:dedicated-dlq\"}")).build());
        assertThat(config.billingConsumer(sqs, handler, codec, metrics, "mock://dedicated")).isNotNull();
        when(sqs.getQueueAttributes(any(Consumer.class))).thenReturn(GetQueueAttributesResponse.builder().build());
        assertThatThrownBy(() -> config.billingConsumer(sqs, handler, codec, metrics, "mock://dedicated"))
                .hasMessage("BILLING_QUEUE_CONTRACT_MISMATCH");
        when(sqs.getQueueAttributes(any(Consumer.class))).thenReturn(GetQueueAttributesResponse.builder()
                .attributes(Map.of(QueueAttributeName.REDRIVE_POLICY, "{")).build());
        assertThatThrownBy(() -> config.billingConsumer(sqs, handler, codec, metrics, "mock://dedicated"))
                .hasMessage("INVALID_REDRIVE_POLICY");
    }
}
