package br.com.fiap.numberone.shared.infrastructure.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class BillingInternalSecurity {
    @Bean @Order(1)
    SecurityFilterChain billingInternalChain(HttpSecurity http, ObjectMapper json, @Value("${app.saga.billing-inbound-token:}") String token) throws Exception {
        var filter = new OncePerRequestFilter() {
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
                String auth = req.getHeader("Authorization");
                if (token.isBlank() || auth == null || !MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8), auth.getBytes(StandardCharsets.UTF_8))) {
                    problem(json, req, res, 401, "UNAUTHENTICATED"); return;
                }
                try { UUID.fromString(req.getHeader("X-Correlation-Id")); }
                catch (RuntimeException ex) { problem(json, req, res, 400, "INVALID_REQUEST"); return; }
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("billing", null,
                        List.of(new SimpleGrantedAuthority("BILLING_READ_OS"))));
                chain.doFilter(req, res);
            }
        };
        return http.securityMatcher("/internal/**").csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a.requestMatchers(org.springframework.http.HttpMethod.GET,
                        "/internal/service-orders/*", "/internal/service-orders/*/quotation-context").hasAuthority("BILLING_READ_OS").anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> problem(json, req, res, 401, "UNAUTHENTICATED"))
                        .accessDeniedHandler((req,res,ex) -> problem(json, req, res, 403, "FORBIDDEN")))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build();
    }
    private static void problem(ObjectMapper json, HttpServletRequest req, HttpServletResponse res, int status, String code) throws IOException {
        UUID correlation;
        try { correlation = UUID.fromString(req.getHeader("X-Correlation-Id")); } catch (RuntimeException ex) { correlation = UUID.randomUUID(); }
        res.setStatus(status); res.setContentType("application/json");
        res.getWriter().write(json.writeValueAsString(Map.of("code", code, "message", code, "correlationId", correlation)));
    }
}
