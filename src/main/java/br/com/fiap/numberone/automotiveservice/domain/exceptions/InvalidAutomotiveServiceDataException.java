package br.com.fiap.numberone.automotiveservice.domain.exceptions;

public class InvalidAutomotiveServiceDataException extends AutomotiveServiceBusinessException {
    public InvalidAutomotiveServiceDataException(String message) {
        super(message);
    }
}
