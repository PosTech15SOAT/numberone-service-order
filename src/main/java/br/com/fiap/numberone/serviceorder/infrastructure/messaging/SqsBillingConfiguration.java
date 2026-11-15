package br.com.fiap.numberone.serviceorder.infrastructure.messaging;

import br.com.fiap.numberone.serviceorder.application.services.ProcessBillingEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import java.net.URI;
import java.time.Duration;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.billing.sqs.enabled", havingValue = "true")
public class SqsBillingConfiguration {
    @Bean(destroyMethod = "close")
    SqsClient billingSqs(@Value("${app.billing.sqs.region}") String region,
                        @Value("${app.billing.sqs.endpoint:}") String endpoint,
                        @Value("${app.billing.sqs.mode:disabled}") String mode) {
        if (!java.util.Set.of("dedicated", "shared").contains(mode)) { throw new IllegalStateException("BILLING_QUEUE_MODE_REQUIRED"); }
        var builder = SqsClient.builder().region(Region.of(region))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(35)).apiCallAttemptTimeout(Duration.ofSeconds(25)));
        // Default credentials provider chain: no embedded keys, including for AWS Academy.
        if (!endpoint.isBlank()) { builder.endpointOverride(URI.create(endpoint)); }
        return builder.build();
    }
    @Bean
    SqsBillingConsumer billingConsumer(SqsClient sqs, ProcessBillingEvent handler, BillingEventCodec codec,
                                      MeterRegistry metrics, @Value("${app.billing.sqs.queue-url}") String url) {
        if (url.isBlank()) { throw new IllegalStateException("BILLING_QUEUE_URL_REQUIRED"); }
        var attributes = sqs.getQueueAttributes(b -> b.queueUrl(url).attributeNames(QueueAttributeName.ALL)).attributes();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            var redrive = json.readTree(attributes.getOrDefault(QueueAttributeName.REDRIVE_POLICY, "{}"));
            if (!"60".equals(attributes.get(QueueAttributeName.VISIBILITY_TIMEOUT))
                    || !"20".equals(attributes.get(QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS))
                    || !"345600".equals(attributes.get(QueueAttributeName.MESSAGE_RETENTION_PERIOD))
                    || redrive.path("maxReceiveCount").asInt() != 5
                    || redrive.path("deadLetterTargetArn").asText().isBlank()
                    || "true".equals(attributes.get(QueueAttributeName.FIFO_QUEUE))) {
                throw new IllegalStateException("BILLING_QUEUE_CONTRACT_MISMATCH");
            }
        } catch (java.io.IOException ex) { throw new IllegalStateException("INVALID_REDRIVE_POLICY", ex); }
        return new SqsBillingConsumer(sqs, url, handler, codec, metrics);
    }
}
