package br.com.fiap.numberone.shared.security.infrastructure.identity;

import br.com.fiap.numberone.shared.security.application.gateways.AuthenticatedUserProvider;
import br.com.fiap.numberone.shared.security.domain.valueobjects.AuthenticatedUser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Optional;

@Component
@org.springframework.context.annotation.Profile("(local | test) & !production")
@RequestScope
@ConditionalOnProperty(prefix = "app.security.identity", name = "provider", havingValue = "local")
public class LocalAuthenticatedUserProvider implements AuthenticatedUserProvider {

	private final AuthenticatedUser authenticatedUser;

	public LocalAuthenticatedUserProvider(AuthenticatedUserProperties properties) {
		AuthenticatedUserProperties.Local local = properties.getLocal();

		this.authenticatedUser = new AuthenticatedUser(
			local.getSubject(),
			local.getCustomerId(),
			local.getStatus(),
			local.getRoles(),
			local.getPermissions()
		);
	}

	@Override
	public Optional<AuthenticatedUser> currentUser() {
		return Optional.of(authenticatedUser);
	}
}
