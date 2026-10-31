package br.com.fiap.numberone.shared.infrastructure.correlation;

public final class CorrelationIdResolver {
    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlation_id";

    private CorrelationIdResolver() { }

    public static boolean isValid(String value) {
        return value != null && !value.isBlank() && value.length() <= 128
                && value.chars().noneMatch(Character::isISOControl);
    }
}
