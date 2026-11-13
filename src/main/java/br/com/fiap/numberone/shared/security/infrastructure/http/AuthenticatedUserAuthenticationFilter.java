package br.com.fiap.numberone.shared.security.infrastructure.http;

import br.com.fiap.numberone.shared.api.exception.HttpErrorResponseWriter;
import br.com.fiap.numberone.shared.security.application.gateways.AuthenticatedUserProvider;
import br.com.fiap.numberone.shared.security.domain.exceptions.InvalidAuthenticatedUserContextException;
import br.com.fiap.numberone.shared.security.infrastructure.identity.AuthenticatedUserProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.stream.Stream;

/** Adapts the phase-3 identity contract; never issues JWTs or logs identity headers. */
public class AuthenticatedUserAuthenticationFilter extends OncePerRequestFilter {
    private final AuthenticatedUserProvider provider;
    private final AuthenticatedUserProperties properties;
    private final HttpErrorResponseWriter errors;

    public AuthenticatedUserAuthenticationFilter(AuthenticatedUserProvider provider,
            AuthenticatedUserProperties properties, HttpErrorResponseWriter errors) {
        this.provider = provider;
        this.properties = properties;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        try {
            // Deployment must affirm Gateway overwrite and restricted direct access.
            if (!"gateway".equals(properties.getProvider()) || properties.isGatewayTrusted()) {
                var current = provider.currentUser();
                if (current.isPresent()) {
                    var user = current.get();
                    if (!user.isActive()) { errors.write(response, 401); return; }
                    var authorities = Stream.concat(
                            user.roles().stream().map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r),
                            user.permissions().stream()).distinct().map(SimpleGrantedAuthority::new).toList();
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(user, null, authorities));
                }
            }
        } catch (InvalidAuthenticatedUserContextException ex) {
            errors.write(response, 401);
            return;
        }
        chain.doFilter(request, response);
    }
}
