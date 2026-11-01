package br.com.fiap.numberone.shared.security.domain.valueobjects;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record AuthenticatedUser(
	String subject,
	UUID customerId,
	String status,
	Set<String> roles,
	Set<String> permissions
) {

	public AuthenticatedUser {
		subject = requireNonBlank(subject, "subject");
		status = requireNonBlank(status, "status").toUpperCase(Locale.ROOT);
		roles = normalizeAuthorities(roles, "roles");
		permissions = normalizeAuthorities(permissions, "permissions");

		if (roles.isEmpty()) {
			throw new IllegalArgumentException("roles must contain at least one value");
		}
	}

	public boolean isActive() {
		return "ACTIVE".equals(status);
	}

	public boolean hasRole(String role) {
		return role != null && roles.contains(role.trim().toUpperCase(Locale.ROOT));
	}

	public boolean hasPermission(String permission) {
		return permission != null && permissions.contains(permission.trim().toUpperCase(Locale.ROOT));
	}

	private static String requireNonBlank(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
		return value.trim();
	}

	private static Set<String> normalizeAuthorities(Set<String> values, String field) {
		Objects.requireNonNull(values, field + " must not be null");
		return values.stream()
			.filter(Objects::nonNull)
			.map(String::trim)
			.filter(value -> !value.isEmpty())
			.map(value -> value.toUpperCase(Locale.ROOT))
			.collect(Collectors.toUnmodifiableSet());
	}
}
