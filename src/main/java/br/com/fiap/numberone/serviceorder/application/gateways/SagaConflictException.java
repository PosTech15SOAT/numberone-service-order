package br.com.fiap.numberone.serviceorder.application.gateways;

/** Confirmed business conflict: retry alone cannot authorize the missing effect. */
public class SagaConflictException extends RuntimeException {
    public SagaConflictException(String code) { super(code); }
}
