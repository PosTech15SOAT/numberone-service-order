package br.com.fiap.numberone.shared.security.unit.infrastructure;

import br.com.fiap.numberone.shared.security.domain.valueobjects.AuthenticatedUser;
import br.com.fiap.numberone.shared.security.infrastructure.identity.AuthenticatedUserProperties;
import br.com.fiap.numberone.shared.security.infrastructure.identity.LocalAuthenticatedUserProvider;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAuthenticatedUserProviderTest {

	@Test
	void shouldBuildConfiguredLocalUserAndReuseIncomingCorrelationId() {
		UUID customerId = UUID.randomUUID();
		AuthenticatedUserProperties properties = properties(customerId);

		AuthenticatedUser user = new LocalAuthenticatedUserProvider(properties)
			.currentUser()
			.orElseThrow();

		assertEquals("local-customer", user.subject());
		assertEquals(customerId, user.customerId());
		assertTrue(user.hasRole("CUSTOMER"));
		assertTrue(user.hasPermission("SERVICE_ORDER_READ"));
	}

	private AuthenticatedUserProperties properties(UUID customerId) {
		AuthenticatedUserProperties properties = new AuthenticatedUserProperties();
		properties.getLocal().setSubject("local-customer");
		properties.getLocal().setCustomerId(customerId);
		properties.getLocal().setStatus("ACTIVE");
		properties.getLocal().setRoles(Set.of("CUSTOMER"));
		properties.getLocal().setPermissions(Set.of("SERVICE_ORDER_READ"));
		return properties;
	}
}
