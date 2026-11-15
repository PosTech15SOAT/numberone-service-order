package br.com.fiap.numberone.automotiveservice.domain.exceptions;

public class AutomotiveServiceAlreadyInactiveException extends AutomotiveServiceBusinessException {
    public AutomotiveServiceAlreadyInactiveException(String message) {
        super(message);
    }
}
