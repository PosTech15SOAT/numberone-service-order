package br.com.fiap.numberone.serviceorder.domain.exceptions;

/** Stable, sanitized business reason code; never contains user input. */
public class OrderRuleException extends RuntimeException {
    public OrderRuleException(String code) { super(code); }
}
