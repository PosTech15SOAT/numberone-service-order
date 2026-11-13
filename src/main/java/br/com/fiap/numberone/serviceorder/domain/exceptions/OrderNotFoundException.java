package br.com.fiap.numberone.serviceorder.domain.exceptions;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException() { super("RESOURCE_NOT_FOUND"); }
}
