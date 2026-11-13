package br.com.fiap.numberone.shared.security.infrastructure.authorization;

import br.com.fiap.numberone.shared.security.domain.valueobjects.AuthenticatedUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthenticatedCustomerAccess {

	public void requireCurrentUserOwnershipOrAdmin(UUID resourceCustomerId) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
			throw new AccessDeniedException("Authenticated user context is unavailable");
		}
		requireOwnershipOrAdmin(user, resourceCustomerId);
	}

	public void requireOwnershipOrAdmin(AuthenticatedUser user, UUID resourceCustomerId) {
		if (user.hasRole("ADMIN")) {
			return;
		}

		if (user.customerId() == null || resourceCustomerId == null || !user.customerId().equals(resourceCustomerId)) {
			throw new AccessDeniedException("Authenticated customer cannot access this resource");
		}
	}
}
