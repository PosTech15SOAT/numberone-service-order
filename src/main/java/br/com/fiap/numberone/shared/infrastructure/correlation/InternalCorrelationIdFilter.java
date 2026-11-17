package br.com.fiap.numberone.shared.infrastructure.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalCorrelationIdFilter extends OncePerRequestFilter {
    private final ObjectMapper json;

    public InternalCorrelationIdFilter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().substring(request.getContextPath().length()).startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String value = request.getHeader(CorrelationIdResolver.HEADER);
        UUID correlation;
        try {
            correlation = UUID.fromString(value);
            if (!correlation.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("INVALID_CORRELATION");
            }
        } catch (IllegalArgumentException | NullPointerException ex) {
            response.setStatus(400);
            response.setContentType("application/json");
            response.getWriter().write(json.writeValueAsString(Map.of("code", "INVALID_REQUEST",
                    "message", "INVALID_REQUEST", "correlationId", UUID.randomUUID())));
            return;
        }
        String previousCorrelation = MDC.get(CorrelationIdResolver.MDC_KEY);
        MDC.put(CorrelationIdResolver.MDC_KEY, correlation.toString());
        try {
            response.setHeader(CorrelationIdResolver.HEADER, value);
            chain.doFilter(request, response);
        } finally {
            if (previousCorrelation == null) {
                MDC.remove(CorrelationIdResolver.MDC_KEY);
            } else {
                MDC.put(CorrelationIdResolver.MDC_KEY, previousCorrelation);
            }
        }
    }
}
