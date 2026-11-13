package br.com.fiap.numberone.shared.security.unit.infrastructure.authorization;

import br.com.fiap.numberone.shared.security.domain.valueobjects.AuthenticatedUser;
import br.com.fiap.numberone.shared.security.infrastructure.authorization.AuthenticatedCustomerAccess;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedCustomerAccessTest {

	private final AuthenticatedCustomerAccess access = new AuthenticatedCustomerAccess();

	@Test
	void shouldAllowCustomerToAccessOwnResource() {
		UUID customerId = UUID.randomUUID();

		assertThatCode(() -> access.requireOwnershipOrAdmin(customer(customerId), customerId))
			.doesNotThrowAnyException();
	}

	@Test
	void shouldRejectCustomerAccessingAnotherCustomersResource() {
		assertThatThrownBy(() -> access.requireOwnershipOrAdmin(customer(UUID.randomUUID()), UUID.randomUUID()))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void shouldAllowAdminWithoutCustomerId() {
		AuthenticatedUser admin = new AuthenticatedUser(
			"admin-subject", null, "ACTIVE", Set.of("ADMIN"), Set.of());

		assertThatCode(() -> access.requireOwnershipOrAdmin(admin, UUID.randomUUID()))
			.doesNotThrowAnyException();
	}

	private AuthenticatedUser customer(UUID customerId) {
		return new AuthenticatedUser(
			"customer-subject", customerId, "ACTIVE", Set.of("CUSTOMER"), Set.of());
	}
}
