package br.com.fiap.numberone.shared.security.application.gateways;

import br.com.fiap.numberone.shared.security.domain.valueobjects.AuthenticatedUser;

import java.util.Optional;

public interface AuthenticatedUserProvider {

	Optional<AuthenticatedUser> currentUser();
}
