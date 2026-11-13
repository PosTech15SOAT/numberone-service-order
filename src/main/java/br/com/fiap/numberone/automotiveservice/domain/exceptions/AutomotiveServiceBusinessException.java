package br.com.fiap.numberone.automotiveservice.domain.exceptions;

import java.util.List;

public class AutomotiveServiceBusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final List<String> errors;

    public AutomotiveServiceBusinessException(String message) {
        super(message);
        this.errors = List.of(message);
    }

    public AutomotiveServiceBusinessException(String message, List<String> errors) {
        super(message);
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
