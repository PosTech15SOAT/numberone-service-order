package br.com.fiap.numberone.automotiveservice.domain.exceptions;

public class AutomotiveServiceAlreadyActiveException extends AutomotiveServiceBusinessException {
    public AutomotiveServiceAlreadyActiveException(String message) {
        super(message);
    }
}
