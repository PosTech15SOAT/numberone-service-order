package br.com.fiap.numberone.serviceorder.application.gateways;

public class SagaDependencyException extends RuntimeException {
    public SagaDependencyException() { super("DEPENDENCY_UNAVAILABLE"); }
}
