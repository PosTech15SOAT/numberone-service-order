package br.com.fiap.numberone.shared.security.unit.infrastructure;

import br.com.fiap.numberone.shared.api.exception.HttpErrorResponseWriter;
import br.com.fiap.numberone.shared.security.application.gateways.AuthenticatedUserProvider;
import br.com.fiap.numberone.shared.security.infrastructure.identity.AuthenticatedUserProperties;
import br.com.fiap.numberone.shared.security.infrastructure.http.AuthenticatedUserAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GatewayTrustTest {
    @Test void defaultConfigurationDoesNotTrustClientSuppliedIdentity() throws Exception {
        var provider = mock(AuthenticatedUserProvider.class);
        var filter = new AuthenticatedUserAuthenticationFilter(provider, new AuthenticatedUserProperties(), new HttpErrorResponseWriter());
        var request = new MockHttpServletRequest("GET", "/api/admin/ordens-servico");
        request.addHeader("X-Authenticated-Subject", "forged");
        request.addHeader("X-Authenticated-Roles", "ADMIN");
        SecurityContextHolder.clearContext();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
        verifyNoInteractions(provider);
    }
}
