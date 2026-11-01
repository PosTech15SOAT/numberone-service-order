package br.com.fiap.numberone.shared.security.infrastructure.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@ConfigurationProperties(prefix = "app.security.identity")
public class AuthenticatedUserProperties {

	private String provider = "gateway";
    private boolean gatewayTrusted;
    public boolean isGatewayTrusted() { return gatewayTrusted; }
    public void setGatewayTrusted(boolean value) { gatewayTrusted = value; }
	private final Headers headers = new Headers();
	private final Local local = new Local();

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public Headers getHeaders() {
		return headers;
	}

	public Local getLocal() {
		return local;
	}

	public static class Headers {
		private String subject = "X-Authenticated-Subject";
		private String customerId = "X-Authenticated-Customer-Id";
		private String status = "X-Authenticated-Status";
		private String roles = "X-Authenticated-Roles";
		private String permissions = "X-Authenticated-Permissions";
		private String correlationId = "X-Correlation-Id";

		public String getSubject() {
			return subject;
		}

		public void setSubject(String subject) {
			this.subject = subject;
		}

		public String getCustomerId() {
			return customerId;
		}

		public void setCustomerId(String customerId) {
			this.customerId = customerId;
		}

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public String getRoles() {
			return roles;
		}

		public void setRoles(String roles) {
			this.roles = roles;
		}

		public String getPermissions() {
			return permissions;
		}

		public void setPermissions(String permissions) {
			this.permissions = permissions;
		}

		public String getCorrelationId() {
			return correlationId;
		}

		public void setCorrelationId(String correlationId) {
			this.correlationId = correlationId;
		}
	}

	public static class Local {
		private String subject = "local-user";
		private UUID customerId;
		private String status = "ACTIVE";
		private Set<String> roles = new LinkedHashSet<>(Set.of("ADMIN"));
		private Set<String> permissions = new LinkedHashSet<>();

		public String getSubject() {
			return subject;
		}

		public void setSubject(String subject) {
			this.subject = subject;
		}

		public UUID getCustomerId() {
			return customerId;
		}

		public void setCustomerId(UUID customerId) {
			this.customerId = customerId;
		}

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public Set<String> getRoles() {
			return roles;
		}

		public void setRoles(Set<String> roles) {
			this.roles = roles;
		}

		public Set<String> getPermissions() {
			return permissions;
		}

		public void setPermissions(Set<String> permissions) {
			this.permissions = permissions;
		}
	}
}
