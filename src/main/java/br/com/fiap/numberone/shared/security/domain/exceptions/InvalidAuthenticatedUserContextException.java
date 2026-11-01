package br.com.fiap.numberone.shared.security.domain.exceptions;

public class InvalidAuthenticatedUserContextException extends RuntimeException {

	public InvalidAuthenticatedUserContextException(String message) {
		super(message);
	}

	public InvalidAuthenticatedUserContextException(String message, Throwable cause) {
		super(message, cause);
	}
}
