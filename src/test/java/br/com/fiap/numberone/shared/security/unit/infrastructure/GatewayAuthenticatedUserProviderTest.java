package br.com.fiap.numberone.shared.security.unit.infrastructure;

import br.com.fiap.numberone.shared.security.domain.exceptions.InvalidAuthenticatedUserContextException;
import br.com.fiap.numberone.shared.security.domain.valueobjects.AuthenticatedUser;
import br.com.fiap.numberone.shared.security.infrastructure.identity.AuthenticatedUserProperties;
import br.com.fiap.numberone.shared.security.infrastructure.identity.GatewayAuthenticatedUserProvider;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayAuthenticatedUserProviderTest {

	private final AuthenticatedUserProperties properties = new AuthenticatedUserProperties();

	@Test
	void shouldMapTrustedGatewayHeaders() {
		UUID customerId = UUID.randomUUID();
		MockHttpServletRequest request = validRequest(customerId.toString());

		AuthenticatedUser user = new GatewayAuthenticatedUserProvider(request, properties)
			.currentUser()
			.orElseThrow();

		assertEquals("customer-subject", user.subject());
		assertEquals(customerId, user.customerId());
		assertTrue(user.hasRole("CUSTOMER"));
		assertTrue(user.hasPermission("SERVICE_ORDER_READ"));
		assertTrue(user.hasPermission("SERVICE_ORDER_APPROVE"));
	}

	@Test
	void shouldAllowIdentityWithoutCustomerId() {
		MockHttpServletRequest request = validRequest(null);

		AuthenticatedUser user = new GatewayAuthenticatedUserProvider(request, properties)
			.currentUser()
			.orElseThrow();

		assertNull(user.customerId());
	}

	@Test
	void shouldReturnEmptyForAnonymousRequest() {
		Optional<AuthenticatedUser> user = new GatewayAuthenticatedUserProvider(
			new MockHttpServletRequest(),
			properties
		).currentUser();

		assertFalse(user.isPresent());
	}

	@Test
	void shouldRejectPartialIdentityContext() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Authenticated-Subject", "customer-subject");

		assertThrows(
			InvalidAuthenticatedUserContextException.class,
			() -> new GatewayAuthenticatedUserProvider(request, properties).currentUser()
		);
	}

	@Test
	void shouldRejectBlankSubjectWhenIdentityHeaderIsPresent() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Authenticated-Subject", " ");

		assertThrows(
			InvalidAuthenticatedUserContextException.class,
			() -> new GatewayAuthenticatedUserProvider(request, properties).currentUser()
		);
	}

	@Test
	void shouldRejectIdentityHeadersWithoutSubject() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Authenticated-Roles", "ADMIN");

		assertThrows(
			InvalidAuthenticatedUserContextException.class,
			() -> new GatewayAuthenticatedUserProvider(request, properties).currentUser()
		);
	}

	@Test
	void shouldRejectInvalidCustomerIdWithoutExposingHeaderValue() {
		MockHttpServletRequest request = validRequest("invalid-customer-id");

		InvalidAuthenticatedUserContextException exception = assertThrows(
			InvalidAuthenticatedUserContextException.class,
			() -> new GatewayAuthenticatedUserProvider(request, properties).currentUser()
		);

		assertTrue(exception.getMessage().contains("X-Authenticated-Customer-Id"));
		assertFalse(exception.getMessage().contains("invalid-customer-id"));
	}

	@Test
	void shouldNotLogGatewayHeadersForActuatorRequests() {
		MockHttpServletRequest request = validRequest(UUID.randomUUID().toString());
		request.setRequestURI("/actuator/health/liveness");

		ListAppender<ILoggingEvent> appender = captureProviderLogs();
		try {
			new GatewayAuthenticatedUserProvider(request, properties).currentUser();
		} finally {
			detachProviderLogs(appender);
		}

		assertFalse(hasGatewayHeadersLog(appender));
	}

    @Test
    void shouldNeverLogIdentityHeadersForBusinessRequests() {
        MockHttpServletRequest request = validRequest(UUID.randomUUID().toString());
        request.setRequestURI("/api/admin/ordens-servico");
        ListAppender<ILoggingEvent> appender = captureProviderLogs();
        try { new GatewayAuthenticatedUserProvider(request, properties).currentUser(); }
        finally { detachProviderLogs(appender); }
        assertTrue(appender.list.isEmpty());
    }

	private MockHttpServletRequest validRequest(String customerId) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Authenticated-Subject", "customer-subject");
		if (customerId != null) {
			request.addHeader("X-Authenticated-Customer-Id", customerId);
		}
		request.addHeader("X-Authenticated-Status", "ACTIVE");
		request.addHeader("X-Authenticated-Roles", "CUSTOMER");
		request.addHeader("X-Authenticated-Permissions", "SERVICE_ORDER_READ, SERVICE_ORDER_APPROVE");
		return request;
	}

	private ListAppender<ILoggingEvent> captureProviderLogs() {
		Logger logger = (Logger) LoggerFactory.getLogger(GatewayAuthenticatedUserProvider.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		return appender;
	}

	private void detachProviderLogs(ListAppender<ILoggingEvent> appender) {
		Logger logger = (Logger) LoggerFactory.getLogger(GatewayAuthenticatedUserProvider.class);
		logger.detachAppender(appender);
	}

	private boolean hasGatewayHeadersLog(ListAppender<ILoggingEvent> appender) {
		return appender.list.stream()
			.map(ILoggingEvent::getFormattedMessage)
			.anyMatch(message -> message.startsWith("Received gateway authenticated user headers"));
	}
}
