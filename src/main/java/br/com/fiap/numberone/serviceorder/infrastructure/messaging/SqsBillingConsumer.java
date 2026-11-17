package br.com.fiap.numberone.serviceorder.infrastructure.messaging;

import br.com.fiap.numberone.serviceorder.application.events.BillingEvent;
import br.com.fiap.numberone.serviceorder.application.services.ProcessBillingEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** A single delivery per poll bounds processing below the visibility lease. */
public class SqsBillingConsumer {
    private static final Logger log = LoggerFactory.getLogger(SqsBillingConsumer.class);
    private final SqsClient sqs;
    private final String queue;
    private final ProcessBillingEvent handler;
    private final BillingEventCodec codec;
    private final MeterRegistry metrics;
    public SqsBillingConsumer(SqsClient sqs, String queue, ProcessBillingEvent handler,
                             BillingEventCodec codec, MeterRegistry metrics) {
        this.sqs = sqs; this.queue = queue; this.handler = handler; this.codec = codec; this.metrics = metrics;
    }
    @Scheduled(fixedDelayString = "${app.billing.sqs.poll-delay-ms:1000}")
    public void poll() {
        try {
            var response = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queue)
                    .waitTimeSeconds(20).visibilityTimeout(60).maxNumberOfMessages(1)
                    .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT).build());
            for (var message : response.messages()) { consume(message); }
        } catch (RuntimeException ex) {
            metrics.counter("os.billing.failure", "stage", "poll").increment();
            safely(() -> log.atWarn().addKeyValue("event.code", "OS_BILLING_POLL_FAILED")
                    .addKeyValue("error.type", ex.getClass().getName()).log("Falha ao consultar eventos de Billing na SQS"));
        }
    }
    private static void safely(Runnable logging) {
        try { logging.run(); } catch (RuntimeException ignored) { /* Não altera o processamento. */ }
    }
    public void consume(Message message) {
        BillingEvent identity = null;
        var leaseFailed = new AtomicBoolean(false);
        var heartbeat = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().factory());
        var renewal = heartbeat.scheduleAtFixedRate(() -> {
            try {
                sqs.changeMessageVisibility(ChangeMessageVisibilityRequest.builder().queueUrl(queue)
                        .receiptHandle(message.receiptHandle()).visibilityTimeout(60).build());
            } catch (RuntimeException ex) {
                leaseFailed.set(true);
                metrics.counter("os.billing.failure", "stage", "visibility").increment();
            }
        }, 20, 20, TimeUnit.SECONDS);
        try {
            var event = codec.decode(message.body());
            identity = event;
            var outcome = handler.process(event); // REQUIRES_NEW commits before returning.
            metrics.counter("os.billing.processed", "outcome", outcome.name()).increment();
            metrics.summary("os.billing.event.delay.seconds").record(Math.max(0, Duration.between(event.occurredAt(), Instant.now()).toSeconds()));
            safely(() -> log.atInfo().addKeyValue("event.code", "OS_BILLING_EVENT_PROCESSED")
                    .addKeyValue("event.id", event.id()).addKeyValue("correlation_id", event.correlationId())
                    .addKeyValue("service_order.id", event.orderId()).addKeyValue("saga.id", event.orderId())
                    .addKeyValue("event.outcome", outcome.name()).log("Evento de Billing processado"));
            if (!leaseFailed.get()) {
                sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(queue).receiptHandle(message.receiptHandle()).build());
            }
        } catch (RuntimeException ex) {
            metrics.counter("os.billing.failure", "stage", "delivery").increment();
            // Do not delete or copy to DLQ manually. The original body/ID is redelivered
            // after visibility expiry; platform RedrivePolicy moves it after five receives.
            var reason = java.util.Set.of("EVENT_ID_COLLISION", "HANDLER_NOT_AVAILABLE_PHASE3", "BUDGET_COMPOSITION_CONFLICT",
                    "SAGA_STATE_REQUIRES_RECONCILIATION").contains(String.valueOf(ex.getMessage())) ? ex.getMessage() : ex.getClass().getSimpleName();
            var eventIdentity = identity;
            safely(() -> log.atWarn().addKeyValue("event.code", "OS_BILLING_EVENT_UNACKNOWLEDGED")
                    .addKeyValue("event.id", eventIdentity == null ? null : eventIdentity.id())
                    .addKeyValue("correlation_id", eventIdentity == null ? null : eventIdentity.correlationId())
                    .addKeyValue("service_order.id", eventIdentity == null ? null : eventIdentity.orderId())
                    .addKeyValue("saga.id", eventIdentity == null ? null : eventIdentity.orderId())
                    .addKeyValue("attempt.number", message.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT))
                    .addKeyValue("error.code", reason).log("Entrega de Billing não confirmada"));
        } finally {
            renewal.cancel(true);
            heartbeat.shutdownNow();
        }
    }
}
