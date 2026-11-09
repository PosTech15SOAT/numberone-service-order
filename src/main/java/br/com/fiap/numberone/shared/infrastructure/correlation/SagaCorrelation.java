package br.com.fiap.numberone.shared.infrastructure.correlation;

import java.util.UUID;
import org.slf4j.MDC;

public final class SagaCorrelation {
    private SagaCorrelation() { }
    /** Legacy public correlation may be text; internal contracts require UUID. */
    public static UUID current() {
        try { return UUID.fromString(MDC.get(CorrelationIdResolver.MDC_KEY)); }
        catch (RuntimeException ex) { return UUID.randomUUID(); }
    }
}
