package br.com.fiap.numberone.shared.infrastructure.security;

import br.com.fiap.numberone.shared.api.exception.HttpErrorResponseWriter;
import br.com.fiap.numberone.shared.security.application.gateways.AuthenticatedUserProvider;
import br.com.fiap.numberone.shared.security.infrastructure.identity.AuthenticatedUserProperties;
import br.com.fiap.numberone.shared.security.infrastructure.http.AuthenticatedUserAuthenticationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, HttpErrorResponseWriter errors,
            AuthenticatedUserProvider provider, AuthenticatedUserProperties properties) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/health/liveness",
                                "/actuator/health/readiness", "/api/public/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/ordens-servico/*/acompanhamento",
                                "/api/public/veiculos/*/historico-manutencoes")
                            .hasAnyAuthority("ROLE_ADMIN", "SERVICE_ORDER_TRACK_OWN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> errors.write(res, 401))
                        .accessDeniedHandler((req, res, ex) -> errors.write(res, 403)))
                .addFilterBefore(new AuthenticatedUserAuthenticationFilter(provider, properties, errors),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    UserDetailsService disabledLocalUsers() {
        // Prevent Boot's generated local password. Gateway integration arrives with protected features.
        return username -> { throw new UsernameNotFoundException("Local authentication is disabled"); };
    }
}
