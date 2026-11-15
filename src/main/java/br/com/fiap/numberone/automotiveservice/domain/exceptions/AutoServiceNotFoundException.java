package br.com.fiap.numberone.automotiveservice.domain.exceptions;

public class AutoServiceNotFoundException extends RuntimeException {
    public AutoServiceNotFoundException(String message) {
        super(message);
    }
}
