package br.com.fiap.numberone.serviceorder.infrastructure.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
class SagaHttpConfiguration {
    @Bean
    ParticipantHttpClient inventorySagaHttp(ObjectMapper json,
            @Value("${app.saga.inventory-url:}") String url,
            @Value("${app.saga.inventory-token:}") String token) {
        return new ParticipantHttpClient(json, url, token);
    }

    @Bean
    ParticipantHttpClient billingSagaHttp(ObjectMapper json,
            @Value("${app.saga.billing-url:}") String url,
            @Value("${app.saga.billing-token:}") String token) {
        return new ParticipantHttpClient(json, url, token);
    }
}
