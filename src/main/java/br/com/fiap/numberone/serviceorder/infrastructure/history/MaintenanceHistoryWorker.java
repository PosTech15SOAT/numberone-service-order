package br.com.fiap.numberone.serviceorder.infrastructure.history;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.core.exception.SdkException;

public class MaintenanceHistoryWorker {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceHistoryWorker.class);
    private final MaintenanceHistoryOutbox outbox;
    private final DynamoMaintenanceHistory history;
    private final MeterRegistry metrics;
    private final boolean automatic;

    MaintenanceHistoryWorker(MaintenanceHistoryOutbox outbox, DynamoMaintenanceHistory history,
                             MeterRegistry metrics, boolean automatic) {
        this.outbox = outbox;
        this.history = history;
        this.metrics = metrics;
        this.automatic = automatic;
        Gauge.builder("os.history.pending", outbox, db -> db.count("PENDING")).register(metrics);
        Gauge.builder("os.history.collisions", outbox, db -> db.count("COLLISION")).register(metrics);
        Gauge.builder("os.history.oldest.pending.seconds", outbox, MaintenanceHistoryOutbox::oldestPendingSeconds)
                .register(metrics);
    }

    @Scheduled(fixedDelayString = "${app.history.poll-delay-ms}")
    void poll() {
        if (automatic) {
            tick();
        }
    }

    public void tick() {
        outbox.claim().ifPresent(this::project);
    }

    private void project(MaintenanceHistoryOutbox.Claim claim) {
        DynamoMaintenanceHistory.Result result;
        try {
            result = history.put(claim); // The claim statement has committed before any network I/O.
        } catch (SdkException ex) {
            if (outbox.finish(claim, "PENDING", "DYNAMODB_UNAVAILABLE")) {
                metrics.counter("os.history.retries").increment();
                log.warn("History projection scheduled for retry, orderId={}, version={}, errorType={}",
                        claim.orderId(), claim.version(), ex.getClass().getSimpleName());
            }
            return;
        }
        if (result == DynamoMaintenanceHistory.Result.COLLISION) {
            if (outbox.finish(claim, "COLLISION", "CONTENT_MISMATCH")) {
                log.error("History projection collision, orderId={}, version={}", claim.orderId(), claim.version());
            }
        } else if (outbox.finish(claim, "PROJECTED", null)) {
            metrics.counter("os.history.projected", "result", result.name()).increment();
        }
    }
}
