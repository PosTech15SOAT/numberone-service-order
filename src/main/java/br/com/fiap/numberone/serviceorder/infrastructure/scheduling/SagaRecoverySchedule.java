package br.com.fiap.numberone.serviceorder.infrastructure.scheduling;

import br.com.fiap.numberone.serviceorder.application.services.SagaRecoveryWorker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.saga.recovery-enabled", havingValue = "true", matchIfMissing = true)
public class SagaRecoverySchedule {
    private final SagaRecoveryWorker worker;

    public SagaRecoverySchedule(SagaRecoveryWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${app.saga.recovery-delay-ms:5000}")
    public void run() {
        worker.tick();
    }
}
