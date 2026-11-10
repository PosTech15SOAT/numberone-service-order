package br.com.fiap.numberone.shared.infrastructure.correlation;

import br.com.fiap.numberone.shared.api.exception.HttpErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    private final HttpErrorResponseWriter errors;

    public CorrelationIdFilter(HttpErrorResponseWriter errors) {
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
        String id = request.getHeader(CorrelationIdResolver.HEADER);
        if (!CorrelationIdResolver.isValid(id)) {
            errors.write(response, 400);
            return;
        }
        MDC.put(CorrelationIdResolver.MDC_KEY, id);
        try {
            response.setHeader(CorrelationIdResolver.HEADER, id);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationIdResolver.MDC_KEY);
        }
    }
}
