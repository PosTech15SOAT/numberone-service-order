package br.com.fiap.numberone.shared.security.infrastructure.identity;

import br.com.fiap.numberone.shared.security.application.gateways.AuthenticatedUserProvider;
import br.com.fiap.numberone.shared.security.domain.exceptions.InvalidAuthenticatedUserContextException;
import br.com.fiap.numberone.shared.security.domain.valueobjects.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequestScope
@ConditionalOnProperty(prefix = "app.security.identity", name = "provider", havingValue = "gateway")
public class GatewayAuthenticatedUserProvider implements AuthenticatedUserProvider {

	private final HttpServletRequest request;
	private final AuthenticatedUserProperties.Headers headers;

	public GatewayAuthenticatedUserProvider(HttpServletRequest request, AuthenticatedUserProperties properties) {
		this.request = request;
		this.headers = properties.getHeaders();
	}

	@Override
	public Optional<AuthenticatedUser> currentUser() {
		return resolve(request, headers);
	}

	private Optional<AuthenticatedUser> resolve(
		HttpServletRequest request,
		AuthenticatedUserProperties.Headers headers
	) {

		if (!hasAnyIdentityHeader(request, headers)) {
			return Optional.empty();
		}

		String subject = requiredHeader(request, headers.getSubject());
		String status = requiredHeader(request, headers.getStatus());
		String roles = requiredHeader(request, headers.getRoles());
		String permissions = requiredHeaderAllowingEmpty(request, headers.getPermissions());
		UUID customerId = parseCustomerId(request.getHeader(headers.getCustomerId()), headers.getCustomerId());

		try {
			return Optional.of(new AuthenticatedUser(
				subject,
				customerId,
				status,
				parseAuthorities(roles),
				parseAuthorities(permissions)
			));
		} catch (IllegalArgumentException exception) {
			throw new InvalidAuthenticatedUserContextException("Invalid authenticated user context", exception);
		}
	}

	private boolean hasAnyIdentityHeader(HttpServletRequest request, AuthenticatedUserProperties.Headers headers) {
		return request.getHeader(headers.getSubject()) != null
			|| request.getHeader(headers.getCustomerId()) != null
			|| request.getHeader(headers.getStatus()) != null
			|| request.getHeader(headers.getRoles()) != null
			|| request.getHeader(headers.getPermissions()) != null;
	}

	private String requiredHeader(HttpServletRequest request, String headerName) {
		String value = request.getHeader(headerName);
		if (value == null || value.isBlank()) {
			throw new InvalidAuthenticatedUserContextException("Missing required identity header: " + headerName);
		}
		return value;
	}

	private String requiredHeaderAllowingEmpty(HttpServletRequest request, String headerName) {
		String value = request.getHeader(headerName);
		if (value == null) {
			throw new InvalidAuthenticatedUserContextException("Missing required identity header: " + headerName);
		}
		return value;
	}

	private UUID parseCustomerId(String value, String headerName) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(value.trim());
		} catch (IllegalArgumentException exception) {
			throw new InvalidAuthenticatedUserContextException("Invalid UUID in identity header: " + headerName, exception);
		}
	}

	private Set<String> parseAuthorities(String value) {
		if (value.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(value.split(","))
			.map(String::trim)
			.filter(item -> !item.isEmpty())
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
	}
}
