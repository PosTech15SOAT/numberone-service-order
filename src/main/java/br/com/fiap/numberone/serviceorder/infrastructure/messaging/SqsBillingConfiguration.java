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
import java.nio.file.Path;
import java.time.Duration;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.billing.sqs.enabled", havingValue = "true")
public class SqsBillingConfiguration {
    @Bean(destroyMethod = "close")
    BillingQueue billingQueue(@Value("${app.billing.sqs.region}") String region,
                              @Value("${app.billing.sqs.endpoint:}") String endpoint,
                              @Value("${app.billing.sqs.mode:disabled}") String mode,
                              @Value("${app.billing.sqs.queue-url}") String queueUrl,
                              @Value("${app.billing.sqs.bridge-url:}") String bridgeUrl,
                              @Value("${app.billing.sqs.bridge-token-file}") String tokenFile) {
        if (!java.util.Set.of("dedicated", "shared").contains(mode)) { throw new IllegalStateException("BILLING_QUEUE_MODE_REQUIRED"); }
        if (!bridgeUrl.isBlank()) {
            if (!"dedicated".equals(mode)) { throw new IllegalStateException("BILLING_QUEUE_BRIDGE_REQUIRES_DEDICATED_MODE"); }
            return new HttpBillingQueue(URI.create(bridgeUrl), Path.of(tokenFile));
        }
        if (queueUrl.isBlank()) { throw new IllegalStateException("BILLING_QUEUE_URL_REQUIRED"); }
        var builder = SqsClient.builder().region(Region.of(region))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(35)).apiCallAttemptTimeout(Duration.ofSeconds(25)));
        // Default credentials provider chain: no embedded keys, including for AWS Academy.
        if (!endpoint.isBlank()) { builder.endpointOverride(URI.create(endpoint)); }
        return new SqsBillingQueue(builder.build(), queueUrl);
    }
    @Bean
    SqsBillingConsumer billingConsumer(BillingQueue queue, ProcessBillingEvent handler, BillingEventCodec codec,
                                      MeterRegistry metrics) {
        var attributes = queue.attributes();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            var redrive = json.readTree(attributes.getOrDefault(QueueAttributeName.REDRIVE_POLICY.toString(), "{}"));
            if (!"60".equals(attributes.get(QueueAttributeName.VISIBILITY_TIMEOUT.toString()))
                    || !"20".equals(attributes.get(QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS.toString()))
                    || !"345600".equals(attributes.get(QueueAttributeName.MESSAGE_RETENTION_PERIOD.toString()))
                    || redrive.path("maxReceiveCount").asInt() != 5
                    || redrive.path("deadLetterTargetArn").asText().isBlank()
                    || "true".equals(attributes.get(QueueAttributeName.FIFO_QUEUE.toString()))) {
                throw new IllegalStateException("BILLING_QUEUE_CONTRACT_MISMATCH");
            }
        } catch (java.io.IOException ex) { throw new IllegalStateException("INVALID_REDRIVE_POLICY", ex); }
        return new SqsBillingConsumer(queue, handler, codec, metrics);
    }
}
