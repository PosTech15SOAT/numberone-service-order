package br.com.fiap.numberone.serviceorder.saga;

/** A separate JVM, with a fresh application context and only PostgreSQL as recovery state. */
public class RecoveryProcess {
    public static void main(String[] args) {
        try (var app=new org.springframework.boot.builder.SpringApplicationBuilder(br.com.fiap.numberone.ServiceOrderApplication.class)
                .profiles("test").run(args)) {
            app.getBean(br.com.fiap.numberone.serviceorder.application.services.SagaRecoveryWorker.class).tick();
        }
    }
}
