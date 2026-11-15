package br.com.fiap.numberone.serviceorder.infrastructure.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
class SagaHttpConfiguration {
    @Bean
    ParticipantHttpClient inventorySagaHttp(ObjectMapper json,
            @Value("${app.saga.inventory-url:}") String url) {
        return new ParticipantHttpClient(json, url);
    }

    @Bean
    ParticipantHttpClient billingSagaHttp(ObjectMapper json,
            @Value("${app.saga.billing-url:}") String url) {
        return new ParticipantHttpClient(json, url);
    }
}
