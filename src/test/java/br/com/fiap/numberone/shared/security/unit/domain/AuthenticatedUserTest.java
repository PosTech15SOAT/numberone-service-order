package br.com.fiap.numberone.shared.security.unit.domain;

import br.com.fiap.numberone.shared.security.domain.valueobjects.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticatedUserTest {

	@Test
	void shouldNormalizeAndProtectRolesAndPermissions() {
		Set<String> roles = new LinkedHashSet<>(Set.of(" admin "));
		Set<String> permissions = new LinkedHashSet<>(Set.of("service_order_read"));

		AuthenticatedUser user = new AuthenticatedUser(
			" customer-123 ",
			UUID.randomUUID(),
			" active ",
			roles,
			permissions
		);

		roles.add("CUSTOMER");
		permissions.clear();

		assertEquals("customer-123", user.subject());
		assertEquals("ACTIVE", user.status());
		assertTrue(user.isActive());
		assertTrue(user.hasRole("admin"));
		assertTrue(user.hasPermission("service_order_read"));
		assertFalse(user.hasRole("CUSTOMER"));
	}

	@Test
	void shouldRejectUserWithoutRole() {
		assertThrows(IllegalArgumentException.class, () -> new AuthenticatedUser(
			"subject",
			null,
			"ACTIVE",
			Set.of(),
			Set.of()
		));
	}

	@Test
	void shouldRejectBlankRequiredFields() {
		assertThrows(IllegalArgumentException.class, () -> new AuthenticatedUser(
			" ",
			null,
			"ACTIVE",
			Set.of("ADMIN"),
			Set.of()
		));
	}
}
